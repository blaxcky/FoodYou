package com.maksimowiczm.foodyou.barcodescanner.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import com.journeyapps.barcodescanner.BarcodeView

internal class FullFrameBarcodeView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    BarcodeView(context, attrs, defStyleAttr) {

    override fun calculateFramingRect(container: Rect, surface: Rect): Rect {
        return Rect(container).apply {
            if (!intersect(surface)) {
                set(container)
            }
        }
    }
}
