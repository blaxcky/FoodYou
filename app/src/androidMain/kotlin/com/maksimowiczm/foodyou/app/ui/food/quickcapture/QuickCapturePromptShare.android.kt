package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberShareQuickCapturePromptAction(): (String) -> Boolean {
    val context = LocalContext.current
    return remember(context) { { prompt -> shareQuickCapturePromptToChatGpt(context, prompt) } }
}

internal fun shareQuickCapturePromptToChatGpt(context: Context, prompt: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, prompt)
        setPackage("com.openai.chatgpt")
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
