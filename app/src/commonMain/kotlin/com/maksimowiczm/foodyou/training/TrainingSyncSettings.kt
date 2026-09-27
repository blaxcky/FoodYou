package com.maksimowiczm.foodyou.training

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@Composable
fun TrainingSyncSettings() {
    val controller: TrainingSync = koinInject()
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TrainingSyncSettingsContent(state,
        onSignIn = { email, password -> scope.launch { controller.signIn(email, password) } },
        onSignOut = controller::signOut, onEnabled = controller::setEnabled)
}

@Composable
internal fun TrainingSyncSettingsContent(
    state: TrainingSyncState,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // Never saveable or written to preferences.
    OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trainings-App", style = MaterialTheme.typography.titleMedium)
            Text("Krafttraining und Cardio beim manuellen Sync übernehmen. Nur das angemeldete Konto zählt zur Tagesbilanz.")
            if (!state.configured) Text("Nicht eingerichtet – Firebase-Projektkonfiguration fehlt.")
            else if (state.account == null) {
                Text("Mit demselben bestehenden Konto wie in der Trainings-App anmelden.")
                OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, singleLine = true,
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
                OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false))
                Button(onClick = { onSignIn(email, password); password = "" },
                    enabled = !state.busy && email.isNotBlank() && password.isNotEmpty()) { Text("Anmelden") }
            } else {
                Text(state.account.email ?: "Angemeldet")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Beim manuellen Sync übernehmen", Modifier.weight(1f))
                    Switch(state.enabled, onCheckedChange = onEnabled)
                }
                TextButton(onClick = onSignOut) { Text("Abmelden") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.report?.let { report ->
                val date = Instant.fromEpochMilliseconds(report.finishedAtMillis).toLocalDateTime(TimeZone.currentSystemDefault())
                Text("FoodYou-Import: $date", style = MaterialTheme.typography.labelMedium)
                Text(report.description(), color = if (report.successful) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            }
        }
    }
}
