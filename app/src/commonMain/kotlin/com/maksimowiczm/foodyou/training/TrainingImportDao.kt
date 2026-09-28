package com.maksimowiczm.foodyou.training

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Entity(tableName = "ImportedTrainingActivity", indices = [
    Index(value = ["firebaseProjectId", "firebaseUid", "importId"], unique = true),
    Index(value = ["firebaseProjectId", "firebaseUid", "dateEpochDay"]),
])
data class ImportedTrainingActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firebaseProjectId: String,
    val firebaseUid: String,
    val importId: String,
    val sessionId: String,
    val dateEpochDay: Long,
    val name: String,
    val energyKcal: Long,
)

@Entity(tableName = "TrainingImportReceipt", primaryKeys = ["firebaseProjectId", "firebaseUid", "sessionId"])
data class TrainingImportReceiptEntity(
    val firebaseProjectId: String,
    val firebaseUid: String,
    val sessionId: String,
    val canonicalPayload: String,
    val importedAtMillis: Long,
    val receivedAtMillis: Long,
)

@Dao
abstract class TrainingImportDao {
    @Query("SELECT * FROM ImportedTrainingActivity WHERE firebaseProjectId = :project AND firebaseUid = :uid AND dateEpochDay = :date ORDER BY sessionId, importId")
    abstract fun observeEntries(project: String, uid: String, date: Long): Flow<List<ImportedTrainingActivityEntity>>

    @Query("SELECT * FROM ImportedTrainingActivity WHERE firebaseProjectId = :project AND firebaseUid = :uid AND id = :id")
    abstract fun observeEntry(project: String, uid: String, id: Long): Flow<ImportedTrainingActivityEntity?>

    @Query("SELECT * FROM TrainingImportReceipt WHERE firebaseProjectId = :project AND firebaseUid = :uid AND sessionId = :session")
    abstract suspend fun receipt(project: String, uid: String, session: String): TrainingImportReceiptEntity?

    @Insert protected abstract suspend fun insertEntry(entry: ImportedTrainingActivityEntity)
    @Insert protected abstract suspend fun insertReceipt(receipt: TrainingImportReceiptEntity)

    @Query("UPDATE ImportedTrainingActivity SET name = :name, energyKcal = :energyKcal WHERE firebaseProjectId = :project AND firebaseUid = :uid AND id = :id")
    abstract suspend fun updateEntry(project: String, uid: String, id: Long, name: String, energyKcal: Long)

    @Query("DELETE FROM ImportedTrainingActivity WHERE firebaseProjectId = :project AND firebaseUid = :uid AND id = :id")
    abstract suspend fun deleteEntry(project: String, uid: String, id: Long)

    @Transaction
    open suspend fun importDocument(account: TrainingAccount, document: TrainingDocument, nowMillis: Long): TrainingImportResult {
        require(account.project == TRAINING_PROJECT && account.uid.isNotBlank())
        val session = validateTrainingDocument(document)
        val payload = session.canonicalPayload()
        receipt(account.project, account.uid, session.sessionId)?.let {
            return if (it.canonicalPayload == payload) TrainingImportResult.AlreadyImported else TrainingImportResult.Conflict
        }
        for ((category, name, kcal) in listOf(
            Triple("strength", "Krafttraining", session.strengthKcal),
            Triple("cardio", "Cardio", session.cardioKcal),
        )) {
            if (kcal > 0) insertEntry(ImportedTrainingActivityEntity(
                firebaseProjectId = account.project, firebaseUid = account.uid,
                importId = "$TRAINING_SOURCE:${session.sessionId}:$category", sessionId = session.sessionId,
                dateEpochDay = LocalDate.parse(session.activityDate).toEpochDays(), name = name, energyKcal = kcal,
            ))
        }
        insertReceipt(TrainingImportReceiptEntity(account.project, account.uid, session.sessionId,
            payload, nowMillis, requireNotNull(document.receivedAt).toEpochMilliseconds()))
        return if (session.strengthKcal == 0L && session.cardioKcal == 0L) TrainingImportResult.ZeroCalories else TrainingImportResult.Imported
    }
}
