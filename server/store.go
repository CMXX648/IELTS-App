package main

import (
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

// ---------------------------------------------------------------------------
// Errors (docs/05 §9)
// ---------------------------------------------------------------------------

var (
	ErrInvalidDeviceKey = errors.New("invalid_device_key")
	ErrPayloadTooLarge  = errors.New("payload_too_large")
	ErrInvalidChange    = errors.New("invalid_change")
)

// ---------------------------------------------------------------------------
// Change model — JSON keys match docs/05 §4.2 and client SyncContract.kt.
// ---------------------------------------------------------------------------

const (
	OpUpsert = "UPSERT"
	OpDelete = "DELETE"
)

// Shared EV contract ids — mirror of client SyncPolicy (core:domain/sync).
// docs/05 §4.4 freezes schemaVer + promptVer across client and server.
const (
	SyncPolicyEVSchemaVer = "ev.v1"
	SyncPolicyEVPromptVer = "rubric-2026.09"
)

// Change is one pushed change. UpdatedAtMs is epoch millis internally;
// the wire format accepts RFC3339 UTC strings or epoch millis (client sends
// RFC3339 per docs/05 §4.2 examples, SyncChange uses Long millis).
type Change struct {
	Entity     string          `json:"entity"`
	Op         string          `json:"op"`
	ID         string          `json:"id"`
	UpdatedAt  json.RawMessage `json:"updatedAt"` // "2026-09-03T08:12:30Z" or 1725351150000
	DeviceID   string          `json:"deviceId"`
	Data       json.RawMessage `json:"data"`
	UpdatedAtMs int64          `json:"-"`
}

// LoggedChange is a change_log row returned by pull.
type LoggedChange struct {
	Seq    int64  `json:"seq"`
	Entity string `json:"entity"`
	Op     string `json:"op"`
	ID     string `json:"id"`
	DeviceID string `json:"deviceId"`
	UpdatedAtMs int64 `json:"-"`
	UpdatedAtStr string `json:"updatedAt"`
	Data   json.RawMessage `json:"data"`
}

type Conflict struct {
	Entity string `json:"entity"`
	ID     string `json:"id"`
	Reason string `json:"reason"`
}

// ParseUpdatedAt accepts RFC3339 UTC strings and epoch millis.
func ParseUpdatedAt(raw json.RawMessage) (int64, error) {
	s := strings.Trim(string(raw), `" `)
	if s == "" || s == "null" {
		return 0, fmt.Errorf("empty updatedAt")
	}
	if t, err := time.Parse(time.RFC3339, s); err == nil {
		return t.UnixMilli(), nil
	}
	var ms int64
	if _, err := fmt.Sscanf(s, "%d", &ms); err == nil {
		return ms, nil
	}
	return 0, fmt.Errorf("unsupported updatedAt format: %s", s)
}

func MillisToRFC3339(ms int64) string {
	return time.UnixMilli(ms).UTC().Format(time.RFC3339)
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

type Store struct {
	db   *sql.DB
	salt string
}

// Mirror tables per docs/05 §5. CHANGE_LOG is authoritative; mirrors serve stats.
const schema = `
CREATE TABLE IF NOT EXISTS meta (
	key   TEXT PRIMARY KEY,
	value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS devices (
	id           TEXT PRIMARY KEY,
	name         TEXT NOT NULL,
	key_hash     TEXT NOT NULL UNIQUE,
	created_at   INTEGER NOT NULL,
	last_seen_at INTEGER NOT NULL,
	revoked      INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS change_log (
	seq          INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id    TEXT NOT NULL,
	entity       TEXT NOT NULL,
	entity_id    TEXT NOT NULL,
	op           TEXT NOT NULL,
	payload_json TEXT,
	updated_at   INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_change_log_entity ON change_log(entity, entity_id);
CREATE TABLE IF NOT EXISTS entity_state (
	entity       TEXT NOT NULL,
	entity_id    TEXT NOT NULL,
	updated_at   INTEGER NOT NULL,
	device_id    TEXT NOT NULL,
	op           TEXT NOT NULL,
	payload_json TEXT,
	PRIMARY KEY (entity, entity_id)
);
CREATE TABLE IF NOT EXISTS sessions (
	id TEXT PRIMARY KEY, device_id TEXT, type TEXT, topic_id TEXT,
	started_at INTEGER, ended_at INTEGER, duration_ms INTEGER,
	turn_count INTEGER, deleted INTEGER DEFAULT 0, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS ev_results (
	id TEXT PRIMARY KEY, session_id TEXT, overall_band REAL,
	dims_json TEXT, items_json TEXT, highlights_json TEXT,
	prompt_ver TEXT, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS mistakes (
	id TEXT PRIMARY KEY, device_id TEXT, dimension TEXT, quote TEXT,
	correction TEXT, why TEXT, grammar_point_id TEXT, status TEXT,
	updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS vocab_notes (
	id TEXT PRIMARY KEY, term TEXT, topic_id TEXT, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS user_profile (
	id TEXT PRIMARY KEY, stage TEXT, target_band REAL, streak INTEGER,
	total_duration_ms INTEGER, total_turn_count INTEGER,
	total_session_count INTEGER, updated_at INTEGER
);
CREATE TABLE IF NOT EXISTS grammar_progress (
	id TEXT PRIMARY KEY, grammar_point_id TEXT, device_id TEXT,
	hits INTEGER, attempts INTEGER, updated_at INTEGER
);
`

func OpenStore(dbPath string) (*Store, error) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return nil, err
	}
	// WAL: pulls (reads) never block pushes (single writer) — docs/05 §5.
	for _, pragma := range []string{
		"PRAGMA journal_mode=WAL",
		"PRAGMA busy_timeout=5000",
		"PRAGMA synchronous=NORMAL",
		"PRAGMA foreign_keys=ON",
	} {
		if _, err := db.Exec(pragma); err != nil {
			db.Close()
			return nil, fmt.Errorf("pragma %q: %w", pragma, err)
		}
	}
	if _, err := db.Exec(schema); err != nil {
		db.Close()
		return nil, fmt.Errorf("schema: %w", err)
	}
	s := &Store{db: db}
	if s.salt, err = s.loadOrCreateSalt(); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) loadOrCreateSalt() (string, error) {
	var value string
	err := s.db.QueryRow(`SELECT value FROM meta WHERE key='salt'`).Scan(&value)
	if err == nil {
		return value, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return "", err
	}
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	salt := hex.EncodeToString(buf)
	_, err = s.db.Exec(`INSERT INTO meta(key, value) VALUES('salt', ?)`, salt)
	return salt, err
}

// HashKey returns the salted SHA-256 hex of a device key (docs/05 §4.1:
// server stores hashes only, never plaintext).
func (s *Store) HashKey(key string) string {
	sum := sha256.Sum256([]byte(s.salt + ":" + key))
	return hex.EncodeToString(sum[:])
}

func randomHex(n int) string {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		panic(err) // crypto/rand failure is unrecoverable
	}
	return hex.EncodeToString(buf)
}

// ---------------------------------------------------------------------------
// Devices
// ---------------------------------------------------------------------------

type Device struct {
	ID         string
	Name       string
	CreatedAt  int64
	LastSeenAt int64
	Revoked    bool
}

// RegisterDevice creates a device row and returns (deviceId, plaintext key).
// The key is returned exactly once; only its hash is persisted.
func (s *Store) RegisterDevice(name string) (string, string, error) {
	id := "d_" + randomHex(6)
	key := "vk_" + randomHex(24)
	now := time.Now().UnixMilli()
	_, err := s.db.Exec(
		`INSERT INTO devices(id, name, key_hash, created_at, last_seen_at, revoked)
		 VALUES(?, ?, ?, ?, ?, 0)`,
		id, name, s.HashKey(key), now, now,
	)
	return id, key, err
}

// DeviceByKey authenticates an X-Device-Key and returns the device.
func (s *Store) DeviceByKey(key string) (*Device, error) {
	if key == "" {
		return nil, ErrInvalidDeviceKey
	}
	var d Device
	var revoked int
	err := s.db.QueryRow(
		`SELECT id, name, created_at, last_seen_at, revoked FROM devices WHERE key_hash = ?`,
		s.HashKey(key),
	).Scan(&d.ID, &d.Name, &d.CreatedAt, &d.LastSeenAt, &revoked)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrInvalidDeviceKey
	}
	if err != nil {
		return nil, err
	}
	if revoked != 0 {
		return nil, ErrInvalidDeviceKey
	}
	d.Revoked = false
	return &d, nil
}

// TouchDevice updates last_seen_at (best effort).
func (s *Store) TouchDevice(id string) {
	_, _ = s.db.Exec(`UPDATE devices SET last_seen_at = ? WHERE id = ?`, time.Now().UnixMilli(), id)
}

// RevokeDevice marks the device revoked (docs/05 §4.1, authenticated).
func (s *Store) RevokeDevice(deviceID string) error {
	res, err := s.db.Exec(`UPDATE devices SET revoked = 1 WHERE id = ?`, deviceID)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrInvalidDeviceKey
	}
	return nil
}

// ---------------------------------------------------------------------------
// Push (LWW adjudication, docs/05 §4.3)
// ---------------------------------------------------------------------------

type PushResult struct {
	Pushed    int
	Conflicts []Conflict
	NewCursor int64
}

var syncableEntities = map[string]bool{
	"session": true, "ev_result": true, "mistake": true,
	"vocab_note": true, "profile": true, "grammar_progress": true,
}

// Push applies changes with server-side LWW: newer updatedAt wins; ties break
// by lexicographically larger deviceId. Losing changes are reported as
// conflicts and neither enter change_log nor overwrite entity_state/mirrors.
func (s *Store) Push(deviceID string, changes []Change, maxDataBytes int64) (*PushResult, error) {
	result := &PushResult{Conflicts: []Conflict{}}
	for i := range changes {
		c := &changes[i]
		if c.Entity == "" || c.ID == "" || c.DeviceID == "" {
			return nil, ErrInvalidChange
		}
		if !syncableEntities[c.Entity] {
			return nil, fmt.Errorf("%w: entity %q not in whitelist", ErrInvalidChange, c.Entity)
		}
		if c.Op != OpUpsert && c.Op != OpDelete {
			return nil, fmt.Errorf("%w: op %q", ErrInvalidChange, c.Op)
		}
		if c.Op == OpUpsert && len(c.Data) == 0 {
			return nil, fmt.Errorf("%w: UPSERT requires data", ErrInvalidChange)
		}
		if int64(len(c.Data)) > maxDataBytes {
			return nil, ErrPayloadTooLarge
		}
		ms, err := ParseUpdatedAt(c.UpdatedAt)
		if err != nil {
			return nil, fmt.Errorf("%w: %v", ErrInvalidChange, err)
		}
		c.UpdatedAtMs = ms

		won, conflict, err := s.applyChange(deviceID, c)
		if err != nil {
			return nil, err
		}
		if won {
			result.Pushed++
		} else {
			result.Conflicts = append(result.Conflicts, *conflict)
		}
	}
	cursor, err := s.MaxSeq()
	if err != nil {
		return nil, err
	}
	result.NewCursor = cursor
	return result, nil
}

// applyChange runs LWW + (change_log append, entity_state upsert, mirror
// upsert) in one transaction. Returns (won=true) or (won=false, conflict).
func (s *Store) applyChange(deviceID string, c *Change) (bool, *Conflict, error) {
	tx, err := s.db.Begin()
	if err != nil {
		return false, nil, err
	}
	defer tx.Rollback()

	var curUpdatedAt int64
	var curDeviceID, curOp string
	var curPayload sql.NullString
	err = tx.QueryRow(
		`SELECT updated_at, device_id, op, payload_json FROM entity_state WHERE entity=? AND entity_id=?`,
		c.Entity, c.ID,
	).Scan(&curUpdatedAt, &curDeviceID, &curOp, &curPayload)
	hasCur := !errors.Is(err, sql.ErrNoRows)
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return false, nil, err
	}

	if hasCur {
		// LWW: newer wins; tie → lexicographically larger deviceId wins (docs/05 §4.3).
		if c.UpdatedAtMs < curUpdatedAt ||
			(c.UpdatedAtMs == curUpdatedAt && c.DeviceID < curDeviceID) {
			return false, &Conflict{Entity: c.Entity, ID: c.ID, Reason: "lww_lost"}, nil
		}
		// Exact duplicate (same winner content): dedupe, not a conflict.
		if c.UpdatedAtMs == curUpdatedAt && c.DeviceID == curDeviceID && c.Op == curOp &&
			string(c.Data) == curPayload.String {
			return false, &Conflict{Entity: c.Entity, ID: c.ID, Reason: "duplicate"}, nil
		}
	}

	payload := sql.NullString{String: string(c.Data), Valid: len(c.Data) > 0}
	if _, err := tx.Exec(
		`INSERT INTO change_log(device_id, entity, entity_id, op, payload_json, updated_at)
		 VALUES(?, ?, ?, ?, ?, ?)`,
		deviceID, c.Entity, c.ID, c.Op, payload, c.UpdatedAtMs,
	); err != nil {
		return false, nil, err
	}
	if _, err := tx.Exec(
		`INSERT INTO entity_state(entity, entity_id, updated_at, device_id, op, payload_json)
		 VALUES(?, ?, ?, ?, ?, ?)
		 ON CONFLICT(entity, entity_id) DO UPDATE SET
		   updated_at=excluded.updated_at, device_id=excluded.device_id,
		   op=excluded.op, payload_json=excluded.payload_json`,
		c.Entity, c.ID, c.UpdatedAtMs, deviceID, c.Op, payload,
	); err != nil {
		return false, nil, err
	}
	if err := upsertMirror(tx, deviceID, c, c.UpdatedAtMs); err != nil {
		return false, nil, err
	}
	return true, nil, tx.Commit()
}

// upsertMirror projects an accepted change into the stats mirror (docs/05 §5).
// Unknown JSON fields are ignored; mirrors are rebuildable.
func upsertMirror(tx *sql.Tx, deviceID string, c *Change, updatedAtMs int64) error {
	var data map[string]any
	if len(c.Data) > 0 {
		if err := json.Unmarshal(c.Data, &data); err != nil {
			return fmt.Errorf("decode %s data: %w", c.Entity, err)
		}
	}
	switch c.Entity {
	case "session":
		if c.Op == OpDelete {
			res, err := tx.Exec(`UPDATE sessions SET deleted=1, updated_at=? WHERE id=?`, updatedAtMs, c.ID)
			if err != nil {
				return err
			}
			if n, _ := res.RowsAffected(); n == 0 {
				_, err = tx.Exec(
					`INSERT INTO sessions(id, device_id, deleted, updated_at) VALUES(?, ?, 1, ?)`,
					c.ID, deviceID, updatedAtMs)
			}
			return err
		}
		_, err := tx.Exec(
			`INSERT INTO sessions(id, device_id, type, topic_id, started_at, ended_at, duration_ms, turn_count, deleted, updated_at)
			 VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   device_id=excluded.device_id, type=excluded.type, topic_id=excluded.topic_id,
			   started_at=excluded.started_at, ended_at=excluded.ended_at,
			   duration_ms=excluded.duration_ms, turn_count=excluded.turn_count,
			   deleted=0, updated_at=excluded.updated_at`,
			c.ID, deviceID, str(data, "type"), str(data, "topicId"),
			num(data, "startedAt"), numPtr(data, "endedAt"), num(data, "durationMs"),
			int(num(data, "turnCount")), updatedAtMs)
		return err
	case "ev_result":
		_, err := tx.Exec(
			`INSERT INTO ev_results(id, session_id, overall_band, dims_json, items_json, highlights_json, prompt_ver, updated_at)
			 VALUES(?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   session_id=excluded.session_id, overall_band=excluded.overall_band,
			   dims_json=excluded.dims_json, items_json=excluded.items_json,
			   highlights_json=excluded.highlights_json, prompt_ver=excluded.prompt_ver,
			   updated_at=excluded.updated_at`,
			c.ID, str(data, "sessionId"), num(data, "overallBand"),
			rawJSON(data, "dims"), rawJSON(data, "items"), rawJSON(data, "highlights"),
			strOr(data, "promptVer", SyncPolicyEVPromptVer), updatedAtMs)
		return err
	case "mistake":
		if c.Op == OpDelete {
			_, err := tx.Exec(`DELETE FROM mistakes WHERE id=?`, c.ID)
			return err
		}
		_, err := tx.Exec(
			`INSERT INTO mistakes(id, device_id, dimension, quote, correction, why, grammar_point_id, status, updated_at)
			 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   dimension=excluded.dimension, quote=excluded.quote, correction=excluded.correction,
			   why=excluded.why, grammar_point_id=excluded.grammar_point_id,
			   status=excluded.status, updated_at=excluded.updated_at`,
			c.ID, deviceID, str(data, "dimension"), str(data, "quote"), str(data, "correction"),
			str(data, "why"), str(data, "grammarPointId"), strOr(data, "status", "OPEN"), updatedAtMs)
		return err
	case "vocab_note":
		if c.Op == OpDelete {
			_, err := tx.Exec(`DELETE FROM vocab_notes WHERE id=?`, c.ID)
			return err
		}
		_, err := tx.Exec(
			`INSERT INTO vocab_notes(id, term, topic_id, updated_at) VALUES(?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET term=excluded.term, topic_id=excluded.topic_id, updated_at=excluded.updated_at`,
			c.ID, str(data, "term"), str(data, "topicId"), updatedAtMs)
		return err
	case "profile":
		_, err := tx.Exec(
			`INSERT INTO user_profile(id, stage, target_band, streak, total_duration_ms, total_turn_count, total_session_count, updated_at)
			 VALUES(?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   stage=excluded.stage, target_band=excluded.target_band, streak=excluded.streak,
			   total_duration_ms=excluded.total_duration_ms, total_turn_count=excluded.total_turn_count,
			   total_session_count=excluded.total_session_count, updated_at=excluded.updated_at`,
			c.ID, strOr(data, "stage", "S0"), num(data, "targetBand"),
			int(num(data, "streak")), num(data, "totalDurationMs"),
			num(data, "totalTurnCount"), num(data, "totalSessionCount"), updatedAtMs)
		return err
	case "grammar_progress":
		if c.Op == OpDelete {
			_, err := tx.Exec(`DELETE FROM grammar_progress WHERE id=?`, c.ID)
			return err
		}
		_, err := tx.Exec(
			`INSERT INTO grammar_progress(id, grammar_point_id, device_id, hits, attempts, updated_at)
			 VALUES(?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   grammar_point_id=excluded.grammar_point_id, hits=excluded.hits,
			   attempts=excluded.attempts, updated_at=excluded.updated_at`,
			c.ID, str(data, "grammarPointId"), deviceID,
			int(num(data, "hits")), int(num(data, "attempts")), updatedAtMs)
		return err
	}
	return nil
}

// MaxSeq returns the current change_log head (pull cursor source).
func (s *Store) MaxSeq() (int64, error) {
	var seq sql.NullInt64
	if err := s.db.QueryRow(`SELECT MAX(seq) FROM change_log`).Scan(&seq); err != nil {
		return 0, err
	}
	return seq.Int64, nil
}

// ---------------------------------------------------------------------------
// Pull
// ---------------------------------------------------------------------------

type PullResult struct {
	Changes   []LoggedChange
	Cursor    int64
	Truncated bool
}

// Pull returns change_log rows with seq > cursor (or everything when full),
// ordered by seq, capped at limit (docs/05 §4.2).
func (s *Store) Pull(cursor int64, full bool, limit int) (*PullResult, error) {
	if full || cursor < 0 {
		cursor = 0
	}
	rows, err := s.db.Query(
		`SELECT seq, device_id, entity, entity_id, op, payload_json, updated_at
		 FROM change_log WHERE seq > ? ORDER BY seq ASC LIMIT ?`,
		cursor, limit+1,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := &PullResult{Changes: []LoggedChange{}}
	for rows.Next() {
		var lc LoggedChange
		var payload sql.NullString
		if err := rows.Scan(&lc.Seq, &lc.DeviceID, &lc.Entity, &lc.ID, &lc.Op, &payload, &lc.UpdatedAtMs); err != nil {
			return nil, err
		}
		if payload.Valid && payload.String != "" {
			lc.Data = json.RawMessage(payload.String)
		}
		lc.UpdatedAtStr = MillisToRFC3339(lc.UpdatedAtMs)
		result.Changes = append(result.Changes, lc)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if len(result.Changes) > limit {
		result.Changes = result.Changes[:limit]
		result.Truncated = true
	}
	if head, err := s.MaxSeq(); err == nil {
		result.Cursor = head
	}
	return result, nil
}

// ---------------------------------------------------------------------------
// JSON field helpers (payloads are schema-less maps; mirrors pick known keys)
// ---------------------------------------------------------------------------

func str(m map[string]any, key string) string {
	if m == nil {
		return ""
	}
	if v, ok := m[key].(string); ok {
		return v
	}
	return ""
}

func strOr(m map[string]any, key, def string) string {
	if v := str(m, key); v != "" {
		return v
	}
	return def
}

func num(m map[string]any, key string) int64 {
	if m == nil {
		return 0
	}
	switch v := m[key].(type) {
	case float64:
		return int64(v)
	case int64:
		return v
	case json.Number:
		n, _ := v.Int64()
		return n
	}
	return 0
}

func numPtr(m map[string]any, key string) any {
	if m == nil {
		return nil
	}
	if v, ok := m[key].(float64); ok {
		return int64(v)
	}
	return nil
}

func rawJSON(m map[string]any, key string) any {
	if m == nil {
		return nil
	}
	v, ok := m[key]
	if !ok || v == nil {
		return nil
	}
	b, err := json.Marshal(v)
	if err != nil {
		return nil
	}
	return string(b)
}
