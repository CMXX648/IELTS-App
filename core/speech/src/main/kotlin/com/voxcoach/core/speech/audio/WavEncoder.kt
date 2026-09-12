package com.voxcoach.core.speech.audio

/**
 * PCM16LE mono → WAV (44-byte canonical header).
 */
object WavEncoder {
    fun pcm16LeMonoToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val out = ByteArray(44 + dataSize)
        writeAscii(out, 0, "RIFF")
        writeIntLe(out, 4, 36 + dataSize)
        writeAscii(out, 8, "WAVE")
        writeAscii(out, 12, "fmt ")
        writeIntLe(out, 16, 16)
        writeShortLe(out, 20, 1)
        writeShortLe(out, 22, channels)
        writeIntLe(out, 24, sampleRate)
        writeIntLe(out, 28, byteRate)
        writeShortLe(out, 32, blockAlign)
        writeShortLe(out, 34, bitsPerSample)
        writeAscii(out, 36, "data")
        writeIntLe(out, 40, dataSize)
        System.arraycopy(pcm, 0, out, 44, dataSize)
        return out
    }

    private fun writeAscii(dst: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        System.arraycopy(bytes, 0, dst, offset, bytes.size)
    }

    private fun writeIntLe(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeShortLe(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
