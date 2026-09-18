package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "QuickCaptureLogEntry",
    foreignKeys =
        [
            ForeignKey(
                entity = QuickCaptureFoodNameEntity::class,
                parentColumns = ["id"],
                childColumns = ["foodNameId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices =
        [
            Index(value = ["foodNameId"]),
            Index(value = ["createdAt"]),
            Index(value = ["completedAt"]),
        ],
)
data class QuickCaptureLogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodNameId: Long?,
    val foodName: String?,
    val weightMode: Int,
    val directWeightInGrams: Double?,
    val beforeWeightInGrams: Double?,
    val afterWeightInGrams: Double?,
    val afterRequired: Boolean,
    val photoPath: String?,
    val createdAt: Long,
    val completedAt: Long?,
)
