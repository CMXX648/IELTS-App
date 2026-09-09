package com.voxcoach.core.domain.ux

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUxTest {
    @Test
    fun mapsUnknownHost() {
        val msg = NetworkUx.userMessage(UnknownHostException("api.example.com"))
        assertTrue(msg.contains("网络"))
    }

    @Test
    fun mapsTimeout() {
        val msg = NetworkUx.userMessage(SocketTimeoutException("timeout"))
        assertTrue(msg.contains("超时"))
    }

    @Test
    fun maps401() {
        val msg = NetworkUx.userMessage(IOException("HTTP 401: invalid_api_key"))
        assertTrue(msg.contains("401"))
    }

    @Test
    fun maps429() {
        val msg = NetworkUx.userMessage(IOException("HTTP 429: rate limit"))
        assertTrue(msg.contains("429"))
    }

    @Test
    fun maps5xx() {
        val msg = NetworkUx.userMessage(IOException("HTTP 503: unavailable"))
        assertTrue(msg.contains("503"))
    }
}
