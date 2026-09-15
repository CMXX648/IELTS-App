package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func newTestServer(t *testing.T, llmProxy bool) (*httptest.Server, Config) {
	t.Helper()
	cfg := Config{
		Port:             "0",
		DBPath:           filepath.Join(t.TempDir(), "test.sqlite"),
		DataOwner:        "test-owner",
		LLMProxyEnabled:  llmProxy,
		LLMUpstreamURL:   "http://upstream.invalid/v1",
		LLMUpstreamKey:   "sk-test",
		LLMAllowedModels: []string{"mimo-v2.5"},
		RateLimitPerMin:  20,
		MaxBodyBytes:     64 * 1024,
		PullLimit:        1000,
	}
	store, err := OpenStore(cfg.DBPath)
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	t.Cleanup(func() { store.Close() })
	srv := &Server{
		cfg:     cfg,
		store:   store,
		limiter: NewLimiter(cfg.RateLimitPerMin),
		logger:  log.New(io.Discard, "", 0),
		client:  &http.Client{},
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/health", srv.handleHealth)
	mux.HandleFunc("POST /api/v1/auth/register-device", srv.handleRegisterDevice)
	mux.HandleFunc("POST /api/v1/auth/revoke-device", srv.handleRevokeDevice)
	mux.HandleFunc("POST /api/v1/sync/push", srv.auth(srv.handlePush))
	mux.HandleFunc("GET /api/v1/sync/pull", srv.auth(srv.handlePull))
	mux.HandleFunc("GET /api/v1/stats/summary", srv.auth(srv.handleStatsSummary))
	mux.HandleFunc("GET /api/v1/stats/weakness", srv.auth(srv.handleStatsWeakness))
	ts := httptest.NewServer(mux)
	t.Cleanup(ts.Close)
	return ts, cfg
}

func register(t *testing.T, ts *httptest.Server, name string) (deviceID, key string) {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"deviceName": name, "platform": "android"})
	resp, err := http.Post(ts.URL+"/api/v1/auth/register-device", "application/json", bytes.NewReader(body))
	if err != nil {
		t.Fatalf("register: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		t.Fatalf("register status = %d", resp.StatusCode)
	}
	var out struct {
		DeviceID  string `json:"deviceId"`
		DeviceKey string `json:"deviceKey"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		t.Fatalf("decode register: %v", err)
	}
	return out.DeviceID, out.DeviceKey
}

func push(t *testing.T, ts *httptest.Server, key string, changes []map[string]any) (int, map[string]any) {
	t.Helper()
	body, _ := json.Marshal(map[string]any{"changes": changes})
	req, _ := http.NewRequest("POST", ts.URL+"/api/v1/sync/push", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Device-Key", key)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("push: %v", err)
	}
	defer resp.Body.Close()
	var out map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		t.Fatalf("decode push: %v", err)
	}
	return resp.StatusCode, out
}

func pull(t *testing.T, ts *httptest.Server, key, query string) (int, map[string]any) {
	t.Helper()
	req, _ := http.NewRequest("GET", ts.URL+"/api/v1/sync/pull"+query, nil)
	req.Header.Set("X-Device-Key", key)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("pull: %v", err)
	}
	defer resp.Body.Close()
	var out map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		t.Fatalf("decode pull: %v", err)
	}
	return resp.StatusCode, out
}

func pushChange(entity, op, id string, updatedAtMs int64, deviceID string, data map[string]any) map[string]any {
	change := map[string]any{
		"entity": entity, "op": op, "id": id,
		"updatedAt": MillisToRFC3339(updatedAtMs), "deviceId": deviceID,
	}
	if data != nil {
		change["data"] = data
	} else {
		change["data"] = nil
	}
	return change
}

// --- tests ---

func TestHealth(t *testing.T) {
	ts, _ := newTestServer(t, false)
	resp, err := http.Get(ts.URL + "/api/v1/health")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		t.Fatalf("health status = %d", resp.StatusCode)
	}
}

func TestAuthRejectsBadKey(t *testing.T) {
	ts, _ := newTestServer(t, false)
	status, body := pull(t, ts, "vk_wrong", "")
	if status != 401 {
		t.Fatalf("status = %d, want 401", status)
	}
	if body["code"] != "invalid_device_key" {
		t.Fatalf("code = %v", body["code"])
	}
}

func TestRegisterPushPullRoundtrip(t *testing.T) {
	ts, _ := newTestServer(t, false)
	deviceID, key := register(t, ts, "Pixel8")

	changes := []map[string]any{
		pushChange("session", "UPSERT", "s_1", 1725351150000, deviceID, map[string]any{
			"type": "CONVERSATION", "topicId": "T-hometown", "startedAt": 1725351150000,
			"durationMs": 421000, "turnCount": 14,
		}),
		pushChange("ev_result", "UPSERT", "ev_1", 1725351160000, deviceID, map[string]any{
			"sessionId": "s_1", "overallBand": 7.5,
			"dims": map[string]any{"fc": 7.0, "lr": 7.5, "gra": 7.0, "p": 8.0},
			"items": []any{}, "promptVer": SyncPolicyEVPromptVer,
		}),
		pushChange("mistake", "UPSERT", "m_1", 1725351170000, deviceID, map[string]any{
			"dimension": "gra", "quote": "If I know...", "correction": "If I had known...",
			"status": "OPEN",
		}),
	}
	status, out := push(t, ts, key, changes)
	if status != 200 {
		t.Fatalf("push status = %d, body=%v", status, out)
	}
	if out["pushed"].(float64) != 3 {
		t.Fatalf("pushed = %v", out["pushed"])
	}

	// Pull since 0 returns all 3 in seq order.
	status, out = pull(t, ts, key, "?cursor=0")
	if status != 200 {
		t.Fatalf("pull status = %d", status)
	}
	got := out["changes"].([]any)
	if len(got) != 3 {
		t.Fatalf("changes = %d, want 3", len(got))
	}
	first := got[0].(map[string]any)
	if first["entity"] != "session" || first["id"] != "s_1" {
		t.Fatalf("first change = %v", first)
	}
	if first["updatedAt"] != "2024-09-03T08:12:30Z" {
		t.Fatalf("updatedAt roundtrip = %v", first["updatedAt"])
	}
}

func TestLWWConflictOlderLoses(t *testing.T) {
	ts, _ := newTestServer(t, false)
	d1, k1 := register(t, ts, "phone")
	d2, k2 := register(t, ts, "tablet")

	// Newer write from phone.
	status, out := push(t, ts, k1, []map[string]any{
		pushChange("mistake", "UPSERT", "m_9", 2000, d1, map[string]any{"status": "MASTERED", "dimension": "gra", "quote": "q", "correction": "c"}),
	})
	if status != 200 || out["pushed"].(float64) != 1 {
		t.Fatalf("first push: status=%d out=%v", status, out)
	}
	// Older write from tablet loses.
	status, out = push(t, ts, k2, []map[string]any{
		pushChange("mistake", "UPSERT", "m_9", 1000, d2, map[string]any{"status": "OPEN", "dimension": "gra", "quote": "q2", "correction": "c2"}),
	})
	if status != 200 || out["pushed"].(float64) != 0 {
		t.Fatalf("older push: status=%d out=%v", status, out)
	}
	conflicts := out["conflicts"].([]any)
	if len(conflicts) != 1 {
		t.Fatalf("conflicts = %v", conflicts)
	}
	c := conflicts[0].(map[string]any)
	if c["reason"] != "lww_lost" || c["id"] != "m_9" {
		t.Fatalf("conflict = %v", c)
	}

	// Equal timestamps: larger deviceId wins (docs/05 §4.3).
	status, _ = push(t, ts, k2, []map[string]any{
		pushChange("mistake", "UPSERT", "m_10", 3000, d2, map[string]any{"status": "OPEN", "dimension": "gra", "quote": "a", "correction": "b"}),
	})
	if status != 200 {
		t.Fatal(status)
	}
	status, out = push(t, ts, k1, []map[string]any{
		pushChange("mistake", "UPSERT", "m_10", 3000, d1, map[string]any{"status": "MASTERED", "dimension": "gra", "quote": "a", "correction": "b"}),
	})
	if status != 200 {
		t.Fatal(status)
	}
	if d2 > d1 {
		if out["pushed"].(float64) != 0 {
			t.Fatalf("tie should favor larger deviceId %q > %q", d2, d1)
		}
	} else if out["pushed"].(float64) != 1 {
		t.Fatalf("tie should favor larger deviceId %q > %q", d1, d2)
	}
}

func TestTombstoneThenNewerUpsert(t *testing.T) {
	ts, _ := newTestServer(t, false)
	deviceID, key := register(t, ts, "phone")
	push(t, ts, key, []map[string]any{
		pushChange("session", "UPSERT", "s_t", 1000, deviceID, map[string]any{
			"type": "CONVERSATION", "startedAt": 1000, "durationMs": 10, "turnCount": 1,
		}),
	})
	push(t, ts, key, []map[string]any{
		pushChange("session", "DELETE", "s_t", 2000, deviceID, nil),
	})
	// Newer UPSERT covers the tombstone (docs/05 §4.3).
	status, out := push(t, ts, key, []map[string]any{
		pushChange("session", "UPSERT", "s_t", 3000, deviceID, map[string]any{
			"type": "CONVERSATION", "startedAt": 3000, "durationMs": 20, "turnCount": 2,
		}),
	})
	if status != 200 || out["pushed"].(float64) != 1 {
		t.Fatalf("newer upsert after tombstone: status=%d out=%v", status, out)
	}
}

func TestPullCursorAndTruncation(t *testing.T) {
	ts, _ := newTestServer(t, false)
	deviceID, key := register(t, ts, "phone")
	var changes []map[string]any
	for i := 0; i < 5; i++ {
		changes = append(changes, pushChange("mistake", "UPSERT", fmt.Sprintf("m_%d", i), int64(1000+i), deviceID,
			map[string]any{"dimension": "gra", "status": "OPEN", "quote": "q", "correction": "c"}))
	}
	push(t, ts, key, changes)

	// cursor=2 → only seq 3..5.
	_, out := pull(t, ts, key, "?cursor=2")
	got := out["changes"].([]any)
	if len(got) != 3 {
		t.Fatalf("after cursor=2 got %d changes, want 3", len(got))
	}
	// full=1 → everything.
	_, out = pull(t, ts, key, "?cursor=999&full=1")
	got = out["changes"].([]any)
	if len(got) != 5 {
		t.Fatalf("full pull got %d changes, want 5", len(got))
	}
}

func TestValidationErrors(t *testing.T) {
	ts, _ := newTestServer(t, false)
	deviceID, key := register(t, ts, "phone")

	// Non-whitelisted entity (turns must never sync — docs/05 §4.2 warning).
	status, out := push(t, ts, key, []map[string]any{
		pushChange("turn", "UPSERT", "t_1", 1000, deviceID, map[string]any{"text": "hello"}),
	})
	if status != 400 || out["code"] != "invalid_change" {
		t.Fatalf("turn entity: status=%d out=%v", status, out)
	}

	// UPSERT without data.
	body, _ := json.Marshal(map[string]any{"changes": []map[string]any{
		{"entity": "session", "op": "UPSERT", "id": "s_x", "updatedAt": MillisToRFC3339(1000), "deviceId": deviceID},
	}})
	req, _ := http.NewRequest("POST", ts.URL+"/api/v1/sync/push", bytes.NewReader(body))
	req.Header.Set("X-Device-Key", key)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != 400 {
		t.Fatalf("upsert w/o data status = %d", resp.StatusCode)
	}
}

func TestStatsSummaryAndWeakness(t *testing.T) {
	ts, _ := newTestServer(t, false)
	deviceID, key := register(t, ts, "phone")
	push(t, ts, key, []map[string]any{
		pushChange("session", "UPSERT", "s_1", 1725351150000, deviceID, map[string]any{
			"type": "CONVERSATION", "startedAt": 1725351150000, "durationMs": 421000, "turnCount": 14,
		}),
		pushChange("ev_result", "UPSERT", "ev_1", 1725351160000, deviceID, map[string]any{
			"sessionId": "s_1", "overallBand": 7.5,
			"dims": map[string]any{"fc": 7.0},
			"items": []any{map[string]any{"dim": "gra", "category": "grammar_tense"}},
		}),
		pushChange("mistake", "UPSERT", "m_1", 1725351170000, deviceID, map[string]any{
			"dimension": "gra", "status": "OPEN", "quote": "q", "correction": "c",
		}),
	})

	req, _ := http.NewRequest("GET", ts.URL+"/api/v1/stats/summary", nil)
	req.Header.Set("X-Device-Key", key)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	var summary struct {
		SessionCount int64 `json:"sessionCount"`
		DurationMs   int64 `json:"durationMs"`
		TurnCount    int64 `json:"turnCount"`
		ActiveDays   int64 `json:"activeDays"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&summary); err != nil {
		t.Fatal(err)
	}
	if summary.SessionCount != 1 || summary.DurationMs != 421000 || summary.TurnCount != 14 || summary.ActiveDays != 1 {
		t.Fatalf("summary = %+v", summary)
	}

	// Export contains the mirrored session.
	req2, _ := http.NewRequest("GET", ts.URL+"/api/v1/stats/export", nil)
	req2.Header.Set("X-Device-Key", key)
	resp2, err := http.DefaultClient.Do(req2)
	if err != nil {
		t.Fatal(err)
	}
	defer resp2.Body.Close()
	var export map[string]any
	if err := json.NewDecoder(resp2.Body).Decode(&export); err != nil {
		t.Fatal(err)
	}
	sessions := export["sessions"].([]any)
	if len(sessions) != 1 {
		t.Fatalf("export sessions = %v", export["sessions"])
	}
}

func TestRevokeDevice(t *testing.T) {
	ts, _ := newTestServer(t, false)
	deviceID, key := register(t, ts, "phone")
	body, _ := json.Marshal(map[string]string{"deviceId": deviceID})
	req, _ := http.NewRequest("POST", ts.URL+"/api/v1/auth/revoke-device", bytes.NewReader(body))
	req.Header.Set("X-Device-Key", key)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != 204 {
		t.Fatalf("revoke status = %d", resp.StatusCode)
	}
	// Key no longer valid.
	status, _ := pull(t, ts, key, "")
	if status != 401 {
		t.Fatalf("after revoke status = %d, want 401", status)
	}
}
