package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QuickCapturePromptShareTest {
    private val prompt = "Lebensmittel:\nSkyr Natur: 200 g\nApfel: 125,5 g\nBitte als CSV zurückgeben."

    @Test
    fun sharesFullPromptDirectlyToChatGptWithoutCopying() {
        val context = ShareContext()
        var copied: String? = null

        assertTrue(
            shareOrCopyQuickCapturePrompt(
                prompt,
                share = { shareQuickCapturePromptToChatGpt(context, it) },
                copy = { copied = it },
            )
        )

        val intent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("com.openai.chatgpt", intent.`package`)
        assertEquals(prompt, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(copied)
    }

    @Test
    fun copiesFullPromptWhenChatGptCannotReceiveIt() {
        assertCopyFallback(ActivityNotFoundException())
    }

    @Test
    fun copiesFullPromptWhenChatGptActivityIsInaccessible() {
        assertCopyFallback(SecurityException())
    }

    private fun assertCopyFallback(failure: RuntimeException) {
        val context = ShareContext(failure)
        var copied: String? = null

        assertFalse(
            shareOrCopyQuickCapturePrompt(
                prompt,
                share = { shareQuickCapturePromptToChatGpt(context, it) },
                copy = { copied = it },
            )
        )

        assertEquals(prompt, copied)
    }

    private class ShareContext(private val failure: RuntimeException? = null) : ContextWrapper(null) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            failure?.let { throw it }
            startedIntent = intent
        }
    }
}
