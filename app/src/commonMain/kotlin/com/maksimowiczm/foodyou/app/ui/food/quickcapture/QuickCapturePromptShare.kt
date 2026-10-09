package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberShareQuickCapturePromptAction(): (String) -> Boolean

internal fun copyAndShareQuickCapturePrompt(
    prompt: String,
    share: (String) -> Boolean,
    copy: (String) -> Unit,
): Boolean {
    copy(prompt)
    return share(prompt)
}
