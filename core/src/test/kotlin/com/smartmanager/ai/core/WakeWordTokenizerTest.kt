package com.smartmanager.ai.core

import com.smartmanager.ai.core.wake.SentencePieceEncoder
import org.junit.Assert.*
import org.junit.Test

/** Reference tokenizations produced with Python `sentencepiece` on the same bpe.model (sherpa-onnx-kws-zipformer-gigaspeech-3.3M, Apache-2.0). */
class WakeWordTokenizerTest {
    private val bpe = SentencePieceEncoder(javaClass.getResourceAsStream("/kws/bpe.model")!!.readBytes())

    @Test fun `matches reference sentencepiece output`() {
        val ref = mapOf(
            "HEY SMART" to "▁HE Y ▁S M AR T", "HELLO WORLD" to "▁HE LL O ▁WORLD", "HEY JARVIS" to "▁HE Y ▁JA R VI S", "OK COMPUTER" to "▁O K ▁COMP U TER",
            "SMART MANAGER" to "▁S M AR T ▁MAN AGE R", "ALEXA" to "▁A LE X A", "HEY ASSISTANT" to "▁HE Y ▁AS S IST ANT", "SALUT SMART" to "▁SA LU T ▁S M AR T",
            "HI THERE FRIEND" to "▁HI ▁THERE ▁FRIEND",
        )
        assertEquals(500, bpe.vocabSize)
        ref.forEach { (text, tokens) -> assertEquals(text, tokens, bpe.encode(text).joinToString(" ")) }
    }

    @Test fun `keyword line for the default phrase`() {
        assertEquals("▁HE Y ▁S M AR T :1.5 #0.25 @HEY_SMART", bpe.keywordLine("Hey Smart", 1.5f, 0.25f))
    }

    @Test fun `whitespace and case are normalised`() { assertEquals(bpe.encode("HEY SMART"), bpe.encode("  HEY   SMART ")) }
}
