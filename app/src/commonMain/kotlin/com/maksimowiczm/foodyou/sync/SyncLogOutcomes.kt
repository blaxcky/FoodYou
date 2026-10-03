package com.maksimowiczm.foodyou.sync

import com.maksimowiczm.foodyou.activity.HealthConnectSyncResult
import com.maksimowiczm.foodyou.food.domain.usecase.ManualFddbProductSyncResult

fun HealthConnectSyncResult.logOutcome(): SyncLogOutcome = when (this) {
    HealthConnectSyncResult.Synced -> SyncLogOutcome.success("Abgleich abgeschlossen")
    HealthConnectSyncResult.Disabled -> SyncLogOutcome.skipped("In den Einstellungen deaktiviert")
    HealthConnectSyncResult.Unavailable -> SyncLogOutcome.skipped("Health Connect nicht verfügbar")
    HealthConnectSyncResult.UpdateRequired -> SyncLogOutcome.skipped("Health Connect benötigt ein Update")
    HealthConnectSyncResult.MissingPermission -> SyncLogOutcome.skipped("Health-Connect-Berechtigung fehlt")
    HealthConnectSyncResult.Failed -> SyncLogOutcome.failed("Health-Connect-Abgleich fehlgeschlagen")
}

fun ManualFddbProductSyncResult.logOutcome(): SyncLogOutcome = when (this) {
    ManualFddbProductSyncResult.NotEnabled -> SyncLogOutcome.skipped("Produktnachsync deaktiviert")
    is ManualFddbProductSyncResult.Waiting -> SyncLogOutcome.skipped("Noch nicht fällig: $completedSyncs von 3 Tagebuch-Syncs")
    ManualFddbProductSyncResult.NoProducts -> SyncLogOutcome.skipped("Keine fälligen Produkte")
    is ManualFddbProductSyncResult.Completed -> SyncLogOutcome(
        if (result.failed > 0) SyncLogStatus.Failed else SyncLogStatus.Success,
        "${result.synced} Produkte aktualisiert · ${result.failed} Fehler" + if (result.blocked) " · FDDB-Zugriff blockiert" else "",
    )
}
