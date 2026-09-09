package com.voxcoach.core.speech.recording

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class RecordingPathTest {
    @Test
    fun sessionAudioFileUsesM4aUnderRecordingsDir() {
        val sessionId = "sess-abc"
        val dir = File("/tmp", MediaRecorderSessionAudioCapture.RECORDINGS_DIR)
        val file = File(dir, "$sessionId.m4a")
        assertThat(file.name).isEqualTo("sess-abc.m4a")
        assertThat(file.extension).isEqualTo("m4a")
        assertThat(file.parentFile!!.name).isEqualTo("recordings")
    }
}
