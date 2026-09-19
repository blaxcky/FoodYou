package com.maksimowiczm.foodyou.app.infrastructure.android

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.maksimowiczm.foodyou.R

internal fun publishAppShortcuts(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    context.getSystemService(ShortcutManager::class.java).dynamicShortcuts =
        createAppShortcuts(context)
}

internal fun createAppShortcuts(context: Context): List<ShortcutInfo> =
    listOf(
        ShortcutInfo.Builder(context, SHORTCUT_QUICK_CAPTURE_CAMERA_ID)
            .setShortLabel(context.getString(R.string.shortcut_quick_capture_camera_short))
            .setLongLabel(context.getString(R.string.shortcut_quick_capture_camera_long))
            .setIcon(
                Icon.createWithResource(context, R.drawable.ic_shortcut_quick_capture_camera)
            )
            .setIntent(quickCaptureCameraIntent(context))
            .setRank(0)
            .build(),
        ShortcutInfo.Builder(context, SHORTCUT_SCAN_BARCODE_ID)
            .setShortLabel(context.getString(R.string.shortcut_scan_barcode_short))
            .setLongLabel(context.getString(R.string.shortcut_scan_barcode_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_scan_barcode))
            .setIntent(barcodeScanIntent(context))
            .setRank(1)
            .build(),
    )

private fun barcodeScanIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_SCAN_BARCODE)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

internal const val ACTION_SCAN_BARCODE = "com.maksimowiczm.foodyou.action.SCAN_BARCODE"
internal const val SHORTCUT_QUICK_CAPTURE_CAMERA_ID = "quick_capture_camera"
internal const val SHORTCUT_SCAN_BARCODE_ID = "scan_barcode"
