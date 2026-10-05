package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberShareQuickCapturePromptAction(): (String) -> Boolean

internal fun shareOrCopyQuickCapturePrompt(
    prompt: String,
    share: (String) -> Boolean,
    copy: (String) -> Unit,
): Boolean {
    val shared = share(prompt)
    if (!shared) copy(prompt)
    return shared
}
