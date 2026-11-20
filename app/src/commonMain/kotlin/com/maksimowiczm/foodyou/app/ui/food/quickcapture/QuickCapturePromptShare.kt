package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.ai.PromptShareTarget

/** How far handing the quick capture prompt to the chat app got. */
internal enum class QuickCapturePromptShareResult {
    /** The app received the prompt. */
    Sent,

    /** The app opened without the prompt; the user pastes it from the clipboard. */
    OpenedOnly,

    /** The app could not be opened; the prompt is only in the clipboard. */
    Failed,
}

@Composable
internal expect fun rememberShareQuickCapturePromptAction(
    target: PromptShareTarget
): (String) -> QuickCapturePromptShareResult

internal fun copyAndShareQuickCapturePrompt(
    prompt: String,
    share: (String) -> QuickCapturePromptShareResult,
    copy: (String) -> Unit,
): QuickCapturePromptShareResult {
    copy(prompt)
    return share(prompt)
}
