package com.quata.core.language

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastTextLanguageDetectorInstrumentedTest {
    @Test
    fun detectsBundledFastTextLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val detector = FastTextLanguageDetector.loadFromAssets(context)

        assertLanguage(detector, "hola como estas amigo", "es")
        assertLanguage(detector, "bonjour je suis tres content", "fr")
        assertLanguage(detector, "hello how are you today", "en")
        assertLanguage(detector, "ma mbolo ane fang dzam", "fan")
    }

    private fun assertLanguage(
        detector: FastTextLanguageDetector,
        text: String,
        expectedCode: String
    ) {
        val result = detector.detect(text)
        assertEquals(expectedCode, result.code)
        assertTrue("Expected confidence for $text to be > 0.80, got ${result.confidence}", result.confidence > 0.80f)
    }
}
