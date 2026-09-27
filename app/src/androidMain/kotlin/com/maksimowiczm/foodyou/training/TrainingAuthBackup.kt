package com.maksimowiczm.foodyou.training

import java.io.File

/** Firebase Auth owns persisted refresh tokens; they must never travel with a FoodYou backup. */
internal fun isTrainingAuthPreference(file: File): Boolean = file.name.startsWith("com.google.firebase.auth.")

internal fun removeTrainingAuthPreferences(directory: File) {
    directory.listFiles()?.filter(::isTrainingAuthPreference)?.forEach { check(it.delete()) }
}
