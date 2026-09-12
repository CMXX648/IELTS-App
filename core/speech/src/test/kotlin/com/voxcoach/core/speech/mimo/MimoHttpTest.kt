package com.voxcoach.core.speech.mimo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MimoHttpTest {
    @Test
    fun joinUrlStripsDuplicateV1() {
        assertThat(MimoHttp.joinUrl("https://api.xiaomimimo.com/v1", "/v1/chat/completions"))
            .isEqualTo("https://api.xiaomimimo.com/v1/chat/completions")
        assertThat(MimoHttp.joinUrl("https://api.xiaomimimo.com/v1/", "/v1/chat/completions"))
            .isEqualTo("https://api.xiaomimimo.com/v1/chat/completions")
    }

    @Test
    fun asrLanguageMapsTag() {
        assertThat(MimoHttp.asrLanguage("en-GB")).isEqualTo("en")
        assertThat(MimoHttp.asrLanguage("zh-CN")).isEqualTo("zh")
        assertThat(MimoHttp.asrLanguage("ja-JP")).isEqualTo("auto")
    }
}
