package com.voxcoach.core.domain.model

/**
 * M3 SY payload contracts shared by client + Go server (docs/05 §4.2–4.4).
 *
 * Field names intentionally mirror docs/05 JSON keys so Room mappers and the
 * server stay aligned (AGENTS.md rule 5). Payload bodies are opaque JSON
 * strings — see [com.voxcoach.core.domain.sync.SyncChange].
 */
object SyncContract {
    const val PUSH_PATH = "/api/v1/sync/push"
    const val PULL_PATH = "/api/v1/sync/pull"
    const val HEALTH_PATH = "/api/v1/health"
    const val REGISTER_DEVICE_PATH = "/api/v1/auth/register-device"
    const val REVOKE_DEVICE_PATH = "/api/v1/auth/revoke-device"
    const val DEVICE_KEY_HEADER = "X-Device-Key"

    /** Whitelisted sync entities (docs/05 §4.2). */
    val entities = listOf(
        "session",
        "ev_result",
        "mistake",
        "vocab_note",
        "profile",
        "grammar_progress",
    )

    /** Explicitly never synced: turn detail + recordings (docs/05 §4.2). */
    val excluded = listOf("turns", "recording")
}
