package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun copiesFullPromptBeforeSharingDirectlyToChatGpt() {
        var copied: String? = null
        val calls = mutableListOf<String>()
        val context = ShareContext {
            calls += "start"
            assertEquals(prompt, copied)
        }

        assertTrue(
            copyAndShareQuickCapturePrompt(
                prompt,
                share = { shareQuickCapturePromptToChatGpt(context, it) },
                copy = {
                    calls += "copy"
                    copied = it
                },
            )
        )

        val intent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("com.openai.chatgpt", intent.`package`)
        assertEquals(prompt, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertEquals(prompt, copied)
        assertEquals(listOf("copy", "start"), calls)
    }

    @Test
    fun repeatedSharingCopiesAndSendsTheLatestPrompt() {
        val updatedPrompt = "$prompt\nBanane: 100 g"
        var copied: String? = null
        val copies = mutableListOf<String>()
        val receivedPrompts = mutableListOf<String>()
        val context = ShareContext { intent ->
            val received = requireNotNull(intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(copied, received)
            receivedPrompts += received
        }

        listOf(prompt, updatedPrompt).forEach { currentPrompt ->
            assertTrue(
                copyAndShareQuickCapturePrompt(
                    currentPrompt,
                    share = { shareQuickCapturePromptToChatGpt(context, it) },
                    copy = {
                        copies += it
                        copied = it
                    },
                )
            )
        }

        assertEquals(listOf(prompt, updatedPrompt), copies)
        assertEquals(copies, receivedPrompts)
        assertEquals(updatedPrompt, copied)
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
        var copied: String? = null
        val calls = mutableListOf<String>()
        val context = ShareContext(failure) {
            calls += "start"
            assertEquals(prompt, copied)
        }

        assertFalse(
            copyAndShareQuickCapturePrompt(
                prompt,
                share = { shareQuickCapturePromptToChatGpt(context, it) },
                copy = {
                    calls += "copy"
                    copied = it
                },
            )
        )

        assertEquals(prompt, copied)
        assertEquals(listOf("copy", "start"), calls)
    }

    private class ShareContext(
        private val failure: RuntimeException? = null,
        private val onStart: (Intent) -> Unit = {},
    ) : ContextWrapper(null) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            onStart(intent)
            failure?.let { throw it }
            startedIntent = intent
        }
    }
}
