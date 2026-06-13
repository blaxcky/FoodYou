package com.maksimowiczm.foodyou.app.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ReceiverUpdateLauncher(private val scope: CoroutineScope) {
    fun launch(finish: () -> Unit, update: suspend () -> Unit) {
        scope.launch {
            try {
                update()
            } finally {
                finish()
            }
        }
    }
}
