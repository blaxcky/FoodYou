package com.maksimowiczm.foodyou.app.infrastructure.backup

import android.content.Context
import com.maksimowiczm.foodyou.common.auth.SessionRepository
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.OpenFoodFactsCredentialsRepository
import kotlinx.serialization.json.Json

/** Bridges an imported archive to this device's keystore before the app exposes its UI. */
internal object BackupBootstrap {
    private const val FILE = "pending-full-restore"

    suspend fun save(context: Context, crypto: MasterCrypto, state: SensitiveState) {
        context.noBackupFilesDir.mkdirs()
        context.noBackupFilesDir.resolve(FILE).writeBytes(crypto.encrypt(Json.encodeToString(state).encodeToByteArray()))
    }

    suspend fun finalize(
        context: Context,
        crypto: MasterCrypto,
        sessions: SessionRepository,
        fddb: FddbCredentialsRepository,
        openFoodFacts: OpenFoodFactsCredentialsRepository,
    ) {
        val file = context.noBackupFilesDir.resolve(FILE)
        if (!file.isFile) return
        val state = Json.decodeFromString<SensitiveState>(crypto.decrypt(file.readBytes()).decodeToString())
        if (state.session != null) sessions.saveSession(state.session) else sessions.clearSession()
        state.fddb?.let { fddb.store(it.login, it.password) } ?: fddb.clear()
        state.openFoodFacts?.let { openFoodFacts.store(it.login, it.password) } ?: openFoodFacts.clear()
        file.delete()
    }
}
