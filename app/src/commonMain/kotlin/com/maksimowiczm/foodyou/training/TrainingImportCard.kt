package com.maksimowiczm.foodyou.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun TrainingImportCard(
    state: TrainingSyncState,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trainings-App", style = MaterialTheme.typography.titleMedium)
            Text("Fehlende Trainings übernehmen. Jedes Training wird seinem Trainingsdatum zugeordnet.",
                style = MaterialTheme.typography.bodyMedium)
            when {
                !state.configured -> Text("Nicht eingerichtet – Firebase-Projektkonfiguration fehlt.")
                state.account == null -> {
                    Text("Melde dich in den Synchronisierungseinstellungen mit deinem Konto der Trainings-App an.")
                    FilledTonalButton(onClick = onSettings, enabled = !state.busy) {
                        Text("Konto einrichten")
                    }
                }
                else -> {
                    Text(state.account.email ?: "Angemeldet", style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = onSync, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp))
                        Text("Trainings jetzt importieren")
                    }
                    TextButton(onClick = onSettings) { Text("Konto verwalten") }
                }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Trainings werden importiert …", style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.report?.let { report ->
                HorizontalDivider()
                val finished = Instant.fromEpochMilliseconds(report.finishedAtMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val time = "${finished.hour.toString().padStart(2, '0')}:${finished.minute.toString().padStart(2, '0')}"
                Text("Letzter Abgleich: ${trainingDateLabel(finished.date.toString())} · $time",
                    style = MaterialTheme.typography.labelMedium)
                if (report.successful && report.imported == 0) {
                    Text("Keine neuen Trainings gefunden", style = MaterialTheme.typography.titleSmall)
                }
                Text(report.description(), style = MaterialTheme.typography.bodyMedium,
                    color = if (report.successful) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error)
                if (report.imported > 0 && report.importedSessions.isEmpty()) {
                    Text("Für diesen älteren Import sind keine Einzeldetails verfügbar.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (report.importedSessions.isNotEmpty()) {
                    Text("Neu übernommene Trainings", style = MaterialTheme.typography.titleSmall)
                }
                for (session in report.importedSessions.sortedByDescending { it.activityDate }) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(trainingDateLabel(session.activityDate), style = MaterialTheme.typography.titleSmall)
                        Text("${session.strengthKcal + session.cardioKcal} kcal",
                            style = MaterialTheme.typography.titleSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Krafttraining", style = MaterialTheme.typography.bodySmall)
                            Text("${session.strengthKcal} kcal", style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Cardio", style = MaterialTheme.typography.bodySmall)
                            Text("${session.cardioKcal} kcal", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun trainingDateLabel(value: String): String {
    val date = LocalDate.parse(value)
    return "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"
}
