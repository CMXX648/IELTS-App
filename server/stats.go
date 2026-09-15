package main

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"
)

// Stats endpoints read the mirror tables only (docs/05 §4.5). Mirrors are
// projections of change_log/entity_state; client stays the data authority.

func (s *Server) handleStatsSummary(w http.ResponseWriter, r *http.Request, _ *Device) {
	from := queryInt64(r, "from", 0)
	to := queryInt64(r, "to", time.Now().UnixMilli()+86_400_000)
	if to < from {
		to = time.Now().UnixMilli() + 86_400_000
	}
	var sessions, duration, turns, days int64
	err := s.store.db.QueryRow(
		`SELECT COUNT(*), COALESCE(SUM(duration_ms),0), COALESCE(SUM(turn_count),0),
		        COUNT(DISTINCT date(started_at/1000, 'unixepoch'))
		 FROM sessions WHERE deleted=0 AND started_at >= ? AND started_at < ?`,
		from, to,
	).Scan(&sessions, &duration, &turns, &days)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"from": from, "to": to,
		"sessionCount": sessions, "durationMs": duration,
		"turnCount": turns, "activeDays": days,
	})
}

func (s *Server) handleStatsDimsTrend(w http.ResponseWriter, r *http.Request, _ *Device) {
	limit := queryInt(r, "limit", 30)
	if limit <= 0 || limit > 200 {
		limit = 30
	}
	rows, err := s.store.db.Query(
		`SELECT id, session_id, overall_band, dims_json, updated_at
		 FROM ev_results ORDER BY updated_at DESC LIMIT ?`, limit)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	defer rows.Close()
	type trendPoint struct {
		ID          string          `json:"id"`
		SessionID   string          `json:"sessionId"`
		OverallBand float64         `json:"overallBand"`
		Dims        json.RawMessage `json:"dims"`
		UpdatedAt   string          `json:"updatedAt"`
	}
	points := []trendPoint{}
	for rows.Next() {
		var p trendPoint
		var dimsJSON []byte
		var updatedAt int64
		if err := rows.Scan(&p.ID, &p.SessionID, &p.OverallBand, &dimsJSON, &updatedAt); err != nil {
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
			return
		}
		p.Dims = json.RawMessage(dimsJSON)
		p.UpdatedAt = MillisToRFC3339(updatedAt)
		points = append(points, p)
	}
	writeJSON(w, http.StatusOK, map[string]any{"points": points})
}

func (s *Server) handleStatsWeakness(w http.ResponseWriter, _ *http.Request, _ *Device) {
	// Mistakes grouped by dimension/status.
	mistakeRows, err := s.store.db.Query(
		`SELECT dimension, status, COUNT(*) FROM mistakes GROUP BY dimension, status`)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	type bucket struct {
		Dimension string `json:"dimension"`
		Status    string `json:"status"`
		Count     int64  `json:"count"`
	}
	buckets := []bucket{}
	for mistakeRows.Next() {
		var b bucket
		if err := mistakeRows.Scan(&b.Dimension, &b.Status, &b.Count); err != nil {
			mistakeRows.Close()
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
			return
		}
		buckets = append(buckets, b)
	}
	mistakeRows.Close()

	// EV feedback items grouped by (dimension, category) — parsed from items_json.
	evRows, err := s.store.db.Query(`SELECT items_json FROM ev_results WHERE items_json IS NOT NULL`)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "internal", err.Error())
		return
	}
	type evItem struct {
		Dim      string `json:"dim"`
		Category string `json:"category"`
	}
	itemBuckets := map[string]int64{}
	for evRows.Next() {
		var itemsJSON string
		if err := evRows.Scan(&itemsJSON); err != nil {
			continue
		}
		var items []evItem
		if json.Unmarshal([]byte(itemsJSON), &items) != nil {
			continue
		}
		for _, it := range items {
			if it.Category == "" {
				continue
			}
			key := it.Dim + "/" + it.Category
			itemBuckets[key]++
		}
	}
	evRows.Close()

	cats := []map[string]any{}
	for key, count := range itemBuckets {
		dim, category := key, key
		for i := 0; i < len(key); i++ {
			if key[i] == '/' {
				dim, category = key[:i], key[i+1:]
				break
			}
		}
		cats = append(cats, map[string]any{"dimension": dim, "category": category, "count": count})
	}
	writeJSON(w, http.StatusOK, map[string]any{"mistakes": buckets, "evCategories": cats})
}

func (s *Server) handleStatsExport(w http.ResponseWriter, _ *http.Request, _ *Device) {
	// Full JSON export — data ownership (docs/03 §8 / A-1).
	export := map[string]any{"owner": s.cfg.DataOwner, "exportedAt": nowRFC3339()}
	tables := map[string]string{
		"sessions":         `SELECT * FROM sessions`,
		"evResults":        `SELECT * FROM ev_results`,
		"mistakes":         `SELECT * FROM mistakes`,
		"vocabNotes":       `SELECT * FROM vocab_notes`,
		"profile":          `SELECT * FROM user_profile`,
		"grammarProgress":  `SELECT * FROM grammar_progress`,
		"changeLogCount":   `SELECT COUNT(*) FROM change_log`,
	}
	for name, query := range tables {
		rows, err := s.store.db.Query(query)
		if err != nil {
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
			return
		}
		data, err := rowsToMaps(rows)
		rows.Close()
		if err != nil {
			writeErr(w, http.StatusInternalServerError, "internal", err.Error())
			return
		}
		export[name] = data
	}
	writeJSON(w, http.StatusOK, export)
}

func rowsToMaps(rows interface{ Next() bool; Scan(...any) error; Columns() ([]string, error) }) ([]map[string]any, error) {
	cols, err := rows.Columns()
	if err != nil {
		return nil, err
	}
	out := []map[string]any{}
	for rows.Next() {
		vals := make([]any, len(cols))
		ptrs := make([]any, len(cols))
		for i := range vals {
			ptrs[i] = &vals[i]
		}
		if err := rows.Scan(ptrs...); err != nil {
			return nil, err
		}
		row := map[string]any{}
		for i, c := range cols {
			if b, ok := vals[i].([]byte); ok {
				row[c] = string(b)
			} else {
				row[c] = vals[i]
			}
		}
		out = append(out, row)
	}
	return out, nil
}

func queryInt64(r *http.Request, key string, def int64) int64 {
	v := r.URL.Query().Get(key)
	if v == "" {
		return def
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil {
		return def
	}
	return n
}
