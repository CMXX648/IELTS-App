// Package main implements the VoxCoach personal sync server (docs/05).
//
// Single-user (DATA_OWNER) Go binary + SQLite (WAL). It serves:
//   - auth:      POST /api/v1/auth/register-device, /auth/revoke-device (docs/05 §4.1)
//   - sync:      POST /api/v1/sync/push, GET /api/v1/sync/pull (docs/05 §4.2–4.3)
//   - stats:     GET /api/v1/stats/* (docs/05 §4.5, read-only mirrors)
//   - llm proxy: POST /api/v1/llm/chat, /llm/chat-stream (docs/05 §6, mode B, optional)
//   - health:    GET /api/v1/health
//
// CHANGE_LOG is the sync master table (append-only); seq is the pull cursor.
// Business mirror tables are rebuildable projections for stats (docs/05 §5).
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"
)

// Config mirrors docs/05 §8 .env variables.
type Config struct {
	Port             string
	DBPath           string
	DataOwner        string
	LogLevel         string
	LLMProxyEnabled  bool
	LLMUpstreamURL   string
	LLMUpstreamKey   string
	LLMAllowedModels []string
	RateLimitPerMin  int
	MaxBodyBytes     int64 // per-change / proxy body cap (docs/05 §6: ≤64KB)
	PullLimit        int
}

func configFromEnv() Config {
	cfg := Config{
		Port:            envOr("PORT", "8080"),
		DBPath:          envOr("DB_PATH", "db.sqlite"),
		DataOwner:       envOr("DATA_OWNER", "owner"),
		LogLevel:        envOr("LOG_LEVEL", "info"),
		LLMProxyEnabled: envOr("LLM_PROXY_ENABLED", "false") == "true",
		LLMUpstreamURL:  envOr("LLM_UPSTREAM_BASE_URL", ""),
		LLMUpstreamKey:  os.Getenv("LLM_UPSTREAM_KEY"),
		RateLimitPerMin: envInt("RATE_LIMIT_PER_MIN", 20),
		MaxBodyBytes:    int64(envInt("MAX_BODY_BYTES", 64*1024)),
		PullLimit:       envInt("PULL_LIMIT", 1000),
	}
	for _, m := range splitCSV(envOr("LLM_ALLOWED_MODELS", "mimo-v2.5")) {
		cfg.LLMAllowedModels = append(cfg.LLMAllowedModels, m)
	}
	return cfg
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func splitCSV(s string) []string {
	var out []string
	for _, part := range strings.Split(s, ",") {
		part = strings.TrimSpace(part)
		if part != "" {
			out = append(out, part)
		}
	}
	return out
}

func main() {
	cfg := configFromEnv()
	logger := log.New(os.Stdout, "voxcoach ", log.LstdFlags|log.LUTC)

	store, err := OpenStore(cfg.DBPath)
	if err != nil {
		logger.Fatalf("open store: %v", err)
	}
	defer store.Close()

	limiter := NewLimiter(cfg.RateLimitPerMin)
	srv := &Server{
		cfg:    cfg,
		store:  store,
		limiter: limiter,
		logger: logger,
		client: &http.Client{Timeout: 120 * time.Second},
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/health", srv.handleHealth)
	mux.HandleFunc("POST /api/v1/auth/register-device", srv.handleRegisterDevice)
	mux.HandleFunc("POST /api/v1/auth/revoke-device", srv.handleRevokeDevice)
	mux.HandleFunc("POST /api/v1/sync/push", srv.auth(srv.handlePush))
	mux.HandleFunc("GET /api/v1/sync/pull", srv.auth(srv.handlePull))
	mux.HandleFunc("GET /api/v1/stats/summary", srv.auth(srv.handleStatsSummary))
	mux.HandleFunc("GET /api/v1/stats/dims-trend", srv.auth(srv.handleStatsDimsTrend))
	mux.HandleFunc("GET /api/v1/stats/weakness", srv.auth(srv.handleStatsWeakness))
	mux.HandleFunc("GET /api/v1/stats/export", srv.auth(srv.handleStatsExport))
	mux.HandleFunc("POST /api/v1/llm/chat", srv.auth(srv.handleLLMChat(false)))
	mux.HandleFunc("POST /api/v1/llm/chat-stream", srv.auth(srv.handleLLMChat(true)))

	httpSrv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
	}

	logger.Printf("listening on :%s (db=%s owner=%s llmProxy=%v)", cfg.Port, cfg.DBPath, cfg.DataOwner, cfg.LLMProxyEnabled)

	errCh := make(chan error, 1)
	go func() { errCh <- httpSrv.ListenAndServe() }()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	select {
	case err := <-errCh:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Fatalf("server: %v", err)
		}
	case sig := <-stop:
		logger.Printf("received %s, shutting down", sig)
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = httpSrv.Shutdown(ctx)
	}
}
