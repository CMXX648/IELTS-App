package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"
)

// ---------------------------------------------------------------------------
// HTTP plumbing
// ---------------------------------------------------------------------------

type Server struct {
	cfg     Config
	store   *Store
	limiter *Limiter
	logger  *log.Logger
	client  *http.Client
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

// writeErr emits docs/05 §9 error bodies: {"code": string, "message": string}.
func writeErr(w http.ResponseWriter, status int, code, msg string) {
	writeJSON(w, status, map[string]string{"code": code, "message": msg})
}

// auth authenticates X-Device-Key and rejects 401 invalid_device_key (§4.1).
func (s *Server) auth(next func(http.ResponseWriter, *http.Request, *Device)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		device, err := s.store.DeviceByKey(r.Header.Get("X-Device-Key"))
		if err != nil {
			if errors.Is(err, ErrInvalidDeviceKey) {
				writeErr(w, http.StatusUnauthorized, "invalid_device_key", "device key missing, wrong or revoked")
				return
			}
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
			return
		}
		s.store.TouchDevice(device.ID)
		next(w, r, device)
	}
}

// ---------------------------------------------------------------------------
// Health + devices (docs/05 §4.1)
// ---------------------------------------------------------------------------

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok", "version": "1.0.0"})
}

func (s *Server) handleRegisterDevice(w http.ResponseWriter, r *http.Request) {
	var req struct {
		DeviceName string `json:"deviceName"`
		Platform   string `json:"platform"`
	}
	if err := decodeBody(r, s.cfg.MaxBodyBytes, &req); err != nil {
		writeDecodeErr(w, err)
		return
	}
	if req.DeviceName == "" {
		req.DeviceName = "device"
	}
	id, key, err := s.store.RegisterDevice(req.DeviceName)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"deviceId":   id,
		"deviceKey":  key,
		"serverTime": time.Now().UTC().Format(time.RFC3339),
	})
}

func (s *Server) handleRevokeDevice(w http.ResponseWriter, r *http.Request, device *Device) {
	var req struct {
		DeviceID string `json:"deviceId"`
	}
	if err := decodeBody(r, s.cfg.MaxBodyBytes, &req); err != nil {
		writeDecodeErr(w, err)
		return
	}
	if req.DeviceID == "" {
		req.DeviceID = device.ID // self-revoke
	}
	if err := s.store.RevokeDevice(req.DeviceID); err != nil {
		if errors.Is(err, ErrInvalidDeviceKey) {
			writeErr(w, http.StatusUnauthorized, "invalid_device_key", "unknown device")
			return
		}
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func decodeBody(r *http.Request, maxBytes int64, into any) error {
	body, err := io.ReadAll(io.LimitReader(r.Body, maxBytes+1))
	if err != nil {
		return fmt.Errorf("read body: %w", err)
	}
	if int64(len(body)) > maxBytes {
		return ErrPayloadTooLarge
	}
	if len(body) == 0 {
		return nil
	}
	return json.Unmarshal(body, into)
}

func writeDecodeErr(w http.ResponseWriter, err error) {
	if errors.Is(err, ErrPayloadTooLarge) {
		writeErr(w, http.StatusRequestEntityTooLarge, "payload_too_large", "body exceeds limit")
		return
	}
	writeErr(w, http.StatusBadRequest, "invalid_request", err.Error())
}

// ---------------------------------------------------------------------------
// Sync (docs/05 §4.2–4.3)
// ---------------------------------------------------------------------------

func (s *Server) handlePush(w http.ResponseWriter, r *http.Request, device *Device) {
	var req struct {
		Changes []Change `json:"changes"`
	}
	if err := decodeBody(r, s.cfg.MaxBodyBytes*16, &req); err != nil {
		writeDecodeErr(w, err)
		return
	}
	if len(req.Changes) == 0 {
		// docs/05 §9: 409 no_change (or 200 empty); we choose 200 with zeros.
		writeJSON(w, http.StatusOK, map[string]any{
			"pushed": 0, "conflicts": []Conflict{}, "newCursor": mustMaxSeq(s.store), "serverTime": nowRFC3339(),
		})
		return
	}
	result, err := s.store.Push(device.ID, req.Changes, s.cfg.MaxBodyBytes)
	if err != nil {
		switch {
		case errors.Is(err, ErrPayloadTooLarge):
			writeErr(w, http.StatusRequestEntityTooLarge, "payload_too_large", "change data exceeds 64KB")
		case errors.Is(err, ErrInvalidChange):
			writeErr(w, http.StatusBadRequest, "invalid_change", err.Error())
		default:
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		}
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"pushed":     result.Pushed,
		"conflicts":  result.Conflicts,
		"newCursor":  result.NewCursor,
		"serverTime": nowRFC3339(),
	})
}

func (s *Server) handlePull(w http.ResponseWriter, r *http.Request, _ *Device) {
	cursor := int64(queryInt(r, "cursor", 0))
	full := r.URL.Query().Get("full") == "1"
	limit := queryInt(r, "limit", s.cfg.PullLimit)
	if limit <= 0 || limit > s.cfg.PullLimit {
		limit = s.cfg.PullLimit
	}
	result, err := s.store.Pull(cursor, full, limit)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"changes":   result.Changes,
		"cursor":    result.Cursor,
		"truncated": result.Truncated,
	})
}

func queryInt(r *http.Request, key string, def int) int {
	v := r.URL.Query().Get(key)
	if v == "" {
		return def
	}
	var n int
	if _, err := fmt.Sscanf(v, "%d", &n); err != nil {
		return def
	}
	return n
}

func mustMaxSeq(s *Store) int64 {
	seq, err := s.MaxSeq()
	if err != nil {
		return 0
	}
	return seq
}

func nowRFC3339() string { return time.Now().UTC().Format(time.RFC3339) }

// ---------------------------------------------------------------------------
// Rate limiter (per device, docs/05 §6: e.g. 20 req/min for proxy)
// ---------------------------------------------------------------------------

type Limiter struct {
	mu     sync.Mutex
	window time.Duration
	limit  int
	hits   map[string][]time.Time
}

func NewLimiter(perMinute int) *Limiter {
	return &Limiter{window: time.Minute, limit: perMinute, hits: map[string][]time.Time{}}
}

func (l *Limiter) Allow(key string) bool {
	if l.limit <= 0 {
		return true
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	kept := l.hits[key][:0]
	for _, t := range l.hits[key] {
		if now.Sub(t) < l.window {
			kept = append(kept, t)
		}
	}
	if len(kept) >= l.limit {
		l.hits[key] = kept
		return false
	}
	l.hits[key] = append(kept, now)
	return true
}

// ---------------------------------------------------------------------------
// LLM proxy (optional mode B, docs/05 §6)
// ---------------------------------------------------------------------------

func (s *Server) handleLLMChat(stream bool) func(http.ResponseWriter, *http.Request, *Device) {
		if !s.cfg.LLMProxyEnabled {
			writeErr(w, http.StatusNotFound, "not_enabled", "LLM proxy disabled on this server")
			return
		}
		if s.cfg.LLMUpstreamURL == "" || s.cfg.LLMUpstreamKey == "" {
			writeErr(w, http.StatusInternalServerError, "internal", "upstream not configured")
			return
		}
		if !s.limiter.Allow(device.ID) {
			writeErr(w, http.StatusTooManyRequests, "rate_limited", "too many requests")
			return
		}
		body, err := io.ReadAll(io.LimitReader(r.Body, s.cfg.MaxBodyBytes+1))
		if err != nil {
			writeErr(w, http.StatusBadRequest, "invalid_request", err.Error())
			return
		}
		if int64(len(body)) > s.cfg.MaxBodyBytes {
			writeErr(w, http.StatusRequestEntityTooLarge, "payload_too_large", "body exceeds 64KB")
			return
		}
		var payload struct {
			Model string `json:"model"`
		}
		if err := json.Unmarshal(body, &payload); err != nil {
			writeErr(w, http.StatusBadRequest, "invalid_request", "bad JSON body")
			return
		}
		if !modelAllowed(s.cfg.LLMAllowedModels, payload.Model) {
			writeErr(w, http.StatusForbidden, "model_not_allowed", "model not in whitelist")
			return
		}

		upstreamURL := strings.TrimRight(s.cfg.LLMUpstreamURL, "/") + "/chat/completions"
		req, err := http.NewRequestWithContext(r.Context(), http.MethodPost, upstreamURL, strings.NewReader(string(body)))
		if err != nil {
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
			return
		}
		req.Header.Set("Content-Type", "application/json")
		// Upstream key never echoed back or logged (docs/05 §6 security rules).
		req.Header.Set("Authorization", "Bearer "+s.cfg.LLMUpstreamKey)
		req.Header.Set("api-key", s.cfg.LLMUpstreamKey)

		resp, err := s.client.Do(req)
		if err != nil {
			writeErr(w, http.StatusBadGateway, "upstream_error", err.Error())
			return
		}
		defer resp.Body.Close()

		// Sanitized headers only; upstream key cannot leak via response.
		for _, h := range []string{"Content-Type"} {
			if v := resp.Header.Get(h); v != "" {
				w.Header().Set(h, v)
			}
		}
		w.WriteHeader(resp.StatusCode)
		if stream {
			flusher, canFlush := w.(http.Flusher)
			buf := make([]byte, 4096)
			for {
				n, rerr := resp.Body.Read(buf)
				if n > 0 {
					if _, werr := w.Write(buf[:n]); werr != nil {
						return
					}
					if canFlush {
						flusher.Flush()
					}
				}
				if rerr != nil {
					return
				}
			}
		}
		_, _ = io.Copy(w, resp.Body)
	}
}

func modelAllowed(allowed []string, model string) bool {
	for _, m := range allowed {
		if m == model {
			return true
		}
	}
	return false
}
