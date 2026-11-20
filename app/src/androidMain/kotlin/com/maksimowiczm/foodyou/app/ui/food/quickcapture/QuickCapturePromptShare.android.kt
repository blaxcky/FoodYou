package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.maksimowiczm.foodyou.ai.PromptShareTarget

@Composable
internal actual fun rememberShareQuickCapturePromptAction(
    target: PromptShareTarget
): (String) -> QuickCapturePromptShareResult {
    val context = LocalContext.current
    return remember(context, target) { { prompt -> shareQuickCapturePrompt(context, prompt, target) } }
}

/**
 * Sends [prompt] straight to [target]. An app that takes no text share (or blocks it) is still
 * opened, so the user can paste the prompt that was copied beforehand.
 */
internal fun shareQuickCapturePrompt(
    context: Context,
    prompt: String,
    target: PromptShareTarget,
    launchIntent: (String) -> Intent? = { context.packageManager.getLaunchIntentForPackage(it) },
): QuickCapturePromptShareResult {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, prompt)
        setPackage(target.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.tryStart(send)) return QuickCapturePromptShareResult.Sent
    val launch = launchIntent(target.packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return if (launch != null && context.tryStart(launch)) QuickCapturePromptShareResult.OpenedOnly
    else QuickCapturePromptShareResult.Failed
}

private fun Context.tryStart(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
