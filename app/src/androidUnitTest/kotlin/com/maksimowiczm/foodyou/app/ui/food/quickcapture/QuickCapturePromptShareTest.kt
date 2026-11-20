package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import com.maksimowiczm.foodyou.ai.PromptShareTarget
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertSharesDirectly(PromptShareTarget.ChatGpt, "com.openai.chatgpt")
    }

    @Test
    fun copiesFullPromptBeforeSharingDirectlyToGemini() {
        assertSharesDirectly(PromptShareTarget.Gemini, "com.google.android.apps.bard")
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
            assertEquals(
                QuickCapturePromptShareResult.Sent,
                copyAndShareQuickCapturePrompt(
                    currentPrompt,
                    share = { shareQuickCapturePrompt(context, it, PromptShareTarget.ChatGpt, noLaunch) },
                    copy = {
                        copies += it
                        copied = it
                    },
                ),
            )
        }

        assertEquals(listOf(prompt, updatedPrompt), copies)
        assertEquals(copies, receivedPrompts)
        assertEquals(updatedPrompt, copied)
    }

    @Test
    fun opensAppWithoutPromptWhenItTakesNoTextShare() {
        assertOpensAppOnly(ActivityNotFoundException())
    }

    @Test
    fun opensAppWithoutPromptWhenTextShareIsInaccessible() {
        assertOpensAppOnly(SecurityException())
    }

    @Test
    fun copiesFullPromptWhenAppIsNotInstalled() {
        assertCopyFallback(ActivityNotFoundException())
    }

    @Test
    fun copiesFullPromptWhenAppActivityIsInaccessible() {
        assertCopyFallback(SecurityException())
    }

    private fun assertSharesDirectly(target: PromptShareTarget, packageName: String) {
        var copied: String? = null
        val calls = mutableListOf<String>()
        val context = ShareContext {
            calls += "start"
            assertEquals(prompt, copied)
        }

        assertEquals(
            QuickCapturePromptShareResult.Sent,
            copyAndShareQuickCapturePrompt(
                prompt,
                share = { shareQuickCapturePrompt(context, it, target, noLaunch) },
                copy = {
                    calls += "copy"
                    copied = it
                },
            ),
        )

        val intent = context.startedIntents.single()
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(packageName, intent.`package`)
        assertEquals(prompt, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertEquals(listOf("copy", "start"), calls)
    }

    private fun assertOpensAppOnly(failure: RuntimeException) {
        var copied: String? = null
        val requestedPackages = mutableListOf<String>()
        val context = ShareContext(failOn = { it.action == Intent.ACTION_SEND }, failure = failure) {
            assertEquals(prompt, copied)
        }

        assertEquals(
            QuickCapturePromptShareResult.OpenedOnly,
            copyAndShareQuickCapturePrompt(
                prompt,
                share = {
                    shareQuickCapturePrompt(context, it, PromptShareTarget.Gemini) { packageName ->
                        requestedPackages += packageName
                        Intent(Intent.ACTION_MAIN).setPackage(packageName)
                    }
                },
                copy = { copied = it },
            ),
        )

        assertEquals(listOf("com.google.android.apps.bard"), requestedPackages)
        val launch = context.startedIntents.single()
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertEquals("com.google.android.apps.bard", launch.`package`)
        assertNull(launch.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun assertCopyFallback(failure: RuntimeException) {
        var copied: String? = null
        val calls = mutableListOf<String>()
        val context = ShareContext(failOn = { true }, failure = failure) {
            calls += "start"
            assertEquals(prompt, copied)
        }

        assertEquals(
            QuickCapturePromptShareResult.Failed,
            copyAndShareQuickCapturePrompt(
                prompt,
                share = { shareQuickCapturePrompt(context, it, PromptShareTarget.ChatGpt, noLaunch) },
                copy = {
                    calls += "copy"
                    copied = it
                },
            ),
        )

        assertEquals(prompt, copied)
        assertEquals(listOf("copy", "start"), calls)
        assertEquals(emptyList(), context.startedIntents)
    }

    private val noLaunch: (String) -> Intent? = { null }

    private class ShareContext(
        private val failOn: (Intent) -> Boolean = { false },
        private val failure: RuntimeException = ActivityNotFoundException(),
        private val onStart: (Intent) -> Unit = {},
    ) : ContextWrapper(null) {
        val startedIntents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            onStart(intent)
            if (failOn(intent)) throw failure
            startedIntents += intent
        }
    }
}
