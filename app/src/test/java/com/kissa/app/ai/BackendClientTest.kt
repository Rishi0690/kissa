package com.kissa.app.ai

import org.junit.Assert.*
import org.junit.Test

class BackendClientTest {
    @Test fun placeholderUrlIsNotConfigured() {
        assertFalse(BackendClient.isConfiguredUrl("https://kissa-backend.REPLACE-ME.workers.dev"))
        assertFalse(BackendClient.isConfiguredUrl("http://insecure.example.org"))
        assertFalse(BackendClient.isConfiguredUrl(""))
        assertFalse(BackendClient.isConfiguredUrl(null))
        assertTrue(BackendClient.isConfiguredUrl("https://kissa-backend.rishi.workers.dev"))
    }

    @Test fun mapsBackendErrorCodesToFriendlyKinds() {
        fun kind(status: Int, code: String) = BackendClient.errorFrom(status, """{"error":{"code":"$code","message":"m"}}""").kind
        assertEquals(AiException.Kind.SERVICE_SETUP, kind(503, "NOT_CONFIGURED"))
        assertEquals(AiException.Kind.SERVICE_SETUP, kind(503, "UPSTREAM_AUTH"))
        assertEquals(AiException.Kind.SERVICE_LIMIT, kind(503, "UPSTREAM_QUOTA"))
        assertEquals(AiException.Kind.QUOTA, kind(429, "RATE_LIMITED"))
        assertEquals(AiException.Kind.AUTH, kind(401, "UNAUTHORIZED"))
        assertEquals(AiException.Kind.BLOCKED, kind(422, "BLOCKED"))
        assertEquals(AiException.Kind.TIMEOUT, kind(504, "UPSTREAM_TIMEOUT"))
        assertEquals(AiException.Kind.SERVER, kind(502, "UPSTREAM_UNAVAILABLE"))
        // non-JSON bodies (e.g. a proxy error page) still map sensibly
        assertEquals(AiException.Kind.SERVER, BackendClient.errorFrom(502, "<html>Bad gateway</html>").kind)
        assertEquals(AiException.Kind.SERVICE_SETUP, BackendClient.errorFrom(404, "Not found").kind)
        assertTrue(BackendClient.errorFrom(429, null).retryable)
    }

    @Test fun parsesRegisterAndText() {
        val (t, exp) = BackendClient.parseRegister("""{"token":"abc.def","deviceId":"d_1","expiresAt":1790000000,"services":{"chat":true}}""")
        assertEquals("abc.def", t)
        assertEquals(1790000000L, exp)
        assertEquals("hello", BackendClient.parseText("""{"text":"hello","model":"m"}"""))
        assertThrows(AiException::class.java) { BackendClient.parseText("""{"model":"m"}""") }
    }

    @Test fun parsesHealthAndBuildsHonestSetupState() {
        val ok = BackendClient.parseHealth("""{"ok":true,"version":"2.0.0","configured":{"gemini":true,"tokenSecret":true},
            "services":{"chat":{"ok":true,"model":"gemini-3.5-flash-lite","latencyMs":420},"tts":{"ok":true,"model":"gemini-3.8-flash-lite-tts"},"stt":{"ok":true,"heard":"namaste"}}}""")
        assertTrue(ok.ok); assertTrue(ok.configured); assertEquals("gemini-3.5-flash-lite", ok.chat.model); assertEquals(420L, ok.chat.latencyMs)
        assertEquals(SetupState.Phase.READY, AiService.stateFrom(ok).phase)

        val partial = BackendClient.parseHealth("""{"ok":false,"configured":{"gemini":true},"services":{"chat":{"ok":true},"tts":{"ok":false,"error":"UPSTREAM_QUOTA"},"stt":{"ok":false,"error":"SKIPPED_NO_AUDIO"}}}""")
        val ps = AiService.stateFrom(partial)
        assertEquals(SetupState.Phase.PARTIAL, ps.phase)
        assertTrue(ps.chat); assertFalse(ps.premiumVoice)
        assertTrue(ps.message.contains("basic phone voice"))

        val none = BackendClient.parseHealth("""{"ok":false,"configured":{"gemini":false},"services":{"chat":{"ok":false,"error":"NOT_CONFIGURED"},"tts":{"ok":false},"stt":{"ok":false}}}""")
        assertEquals(SetupState.Phase.NOT_SET_UP, AiService.stateFrom(none).phase)

        val quota = BackendClient.parseHealth("""{"ok":false,"configured":{"gemini":true},"services":{"chat":{"ok":false,"error":"UPSTREAM_QUOTA"},"tts":{"ok":false,"error":"UPSTREAM_QUOTA"},"stt":{"ok":false}}}""")
        val qs = AiService.stateFrom(quota)
        assertEquals(SetupState.Phase.OFFLINE, qs.phase)
        assertTrue(qs.message.contains("limit"))
    }

    @Test fun ttsBodyCarriesVoiceEmotionAndDelivery() {
        val b = BackendClient.ttsBody(TtsRequest("Acha?", "Leda", "SHY", "hinglish", "soft, a nervous laugh", "giggle")).toString()
        assertTrue(b.contains("\"voice\":\"Leda\""))
        assertTrue(b.contains("\"emotion\":\"shy\""))
        assertTrue(b.contains("\"delivery\":\"soft, a nervous laugh\""))
        assertTrue(b.contains("\"vocal\":\"giggle\""))
        assertFalse(BackendClient.ttsBody(TtsRequest("Hi", "Puck", "NEUTRAL", "english")).toString().contains("delivery"))
    }
}
