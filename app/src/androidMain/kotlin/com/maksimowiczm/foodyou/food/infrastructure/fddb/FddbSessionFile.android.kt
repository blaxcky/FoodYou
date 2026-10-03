package com.maksimowiczm.foodyou.food.infrastructure.fddb

import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

internal actual fun Scope.produceFddbSessionFile(): String =
    androidContext().noBackupFilesDir.resolve("fddb_session.preferences_pb").absolutePath
