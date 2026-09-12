package com.voxcoach.core.speech.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WavEncoderTest {
    @Test
    fun pcm16MonoGets44ByteHeader() {
        val pcm = ByteArray(32000) { 1 }
        val wav = WavEncoder.pcm16LeMonoToWav(pcm, 16_000)
        assertThat(wav.size).isEqualTo(44 + 32000)
        fun four(at: Int) = wav.copyOfRange(at, at + 4).toString(Charsets.US_ASCII)
        assertThat(four(0)).isEqualTo("RIFF")
        assertThat(four(8)).isEqualTo("WAVE")
        assertThat(four(12)).isEqualTo("fmt ")
        assertThat(four(36)).isEqualTo("data")
        // sample rate 16000 little-endian at offset 24
        val rate = (wav[24].toInt() and 0xFF) or
            ((wav[25].toInt() and 0xFF) shl 8) or
            ((wav[26].toInt() and 0xFF) shl 16) or
            ((wav[27].toInt() and 0xFF) shl 24)
        assertThat(rate).isEqualTo(16_000)
    }
}
