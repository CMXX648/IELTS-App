package com.voxcoach.core.domain.ux

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Maps LLM / network failures to short Chinese UX copy (SY-03 local).
 * Safe for UI error banners; never throws.
 */
object NetworkUx {
    fun userMessage(throwable: Throwable, fallback: String = "操作失败，请稍后重试"): String {
        val chain = generateSequence(throwable) { it.cause }.toList()
        val joined = chain.joinToString(" ") { (it.message ?: "") + " " + it.javaClass.simpleName }
        val lower = joined.lowercase()

        if (chain.any { it is UnknownHostException || it is ConnectException } ||
            lower.contains("unable to resolve host") ||
            lower.contains("failed to connect") ||
            lower.contains("network is unreachable") ||
            lower.contains("no address associated")
        ) {
            return "模型网络不可用，请检查网络后重试。本地录音与已保存轮次仍保留。"
        }

        if (chain.any {
                it is SocketTimeoutException || it is TimeoutException ||
                    (it is InterruptedIOException && (it.message ?: "").lowercase().contains("timeout"))
            } || lower.contains("timeout") || lower.contains("timed out")
        ) {
            return "请求超时，请稍后重试。本地录音与已保存轮次仍保留。"
        }

        val code = Regex("""\bHTTP\s+(\d{3})\b""", RegexOption.IGNORE_CASE)
            .find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""\b(401|429|500|502|503|504)\b""").find(joined)?.groupValues?.getOrNull(1)?.toIntOrNull()

        when (code) {
            401 -> return "API Key 无效或未授权（401），请到设置检查密钥。"
            429 -> return "请求过于频繁，已被限流（429），请稍后再试。"
            in 500..599 -> return "模型服务异常（${code}），请稍后重试。本地录音与已保存轮次仍保留。"
        }

        if (lower.contains("api key is empty") || lower.contains("api key") && lower.contains("empty")) {
            return "尚未配置 API Key，请先到设置填写。"
        }
        if (lower.contains("base url is empty")) {
            return "尚未配置 Base URL，请先到设置填写。"
        }
        if (lower.contains("unauthorized") || lower.contains("invalid api key") ||
            lower.contains("incorrect api key")
        ) {
            return "API Key 无效或未授权（401），请到设置检查密钥。"
        }
        if (lower.contains("rate limit") || lower.contains("too many requests")) {
            return "请求过于频繁，已被限流（429），请稍后再试。"
        }

        val raw = throwable.message?.trim().orEmpty()
        return if (raw.isBlank()) fallback else "$fallback（$raw）"
    }
}
