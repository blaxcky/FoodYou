package com.maksimowiczm.foodyou.app.infrastructure.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.infrastructure.backup.FullBackupManager
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.theme.FoodYouTheme
import com.maksimowiczm.foodyou.common.auth.SessionRepository
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.OpenFoodFactsCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

class FullBackupActivity : FoodYouAbstractActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FoodYouTheme { BackupScreen() } }
    }
}

@Composable private fun Activity.BackupScreen() {
    val database: FoodYouDatabase = koinInject(); val crypto: MasterCrypto = koinInject()
    val sessions: SessionRepository = koinInject(); val fddb: FddbCredentialsRepository = koinInject(); val off: OpenFoodFactsCredentialsRepository = koinInject()
    val manager = remember { FullBackupManager(this, database, crypto, sessions, fddb, off) }
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<BackupAction?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.foodyou.backup")) { uri -> if (uri != null) action = BackupAction.Export(uri) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) action = BackupAction.Import(uri) }
    Scaffold(topBar = { TopAppBar(title = { Text("Vollständige Sicherung") }, navigationIcon = { ArrowBackIconButton(onClick = { finish() }) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sicherungen enthalten alle FoodYou-Daten, Einstellungen, Fotos und Zugangsdaten. Sie werden mit Ihrem Passwort verschlüsselt.", style = MaterialTheme.typography.bodyMedium)
            Button(enabled = !busy, onClick = { export.launch("foodyou-${System.currentTimeMillis()}.fybackup") }) { Text("Sicherung exportieren") }
            Button(enabled = !busy, onClick = { import.launch(arrayOf("application/vnd.foodyou.backup", "application/octet-stream", "*/*")) }) { Text("Sicherung wiederherstellen") }
            Text("Eine Wiederherstellung ersetzt den gesamten lokalen Bestand und startet die App neu.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (busy) CircularProgressIndicator()
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Sicherung fehlgeschlagen") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
    action?.let { current -> PasswordDialog(
        importing = current is BackupAction.Import,
        onDismiss = { action = null },
        onConfirm = { password ->
            action = null; busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        when (current) {
                            is BackupAction.Export -> contentResolver.openOutputStream(current.uri)?.use { manager.export(password, it) } ?: error("Datei kann nicht geschrieben werden.")
                            is BackupAction.Import -> contentResolver.openInputStream(current.uri)?.use { manager.import(password, it) } ?: error("Datei kann nicht gelesen werden.")
                        }
                    }
                    if (current is BackupAction.Import) { finishAffinity(); Process.killProcess(Process.myPid()) }
                } catch (e: Exception) { error = e.message ?: "Die Sicherung konnte nicht verarbeitet werden." } finally { busy = false }
            }
        }
    ) }
}

private sealed interface BackupAction { data class Export(val uri: Uri) : BackupAction; data class Import(val uri: Uri) : BackupAction }

@Composable private fun PasswordDialog(importing: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (importing) "Backup-Passwort" else "Backup schützen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation())
            if (!importing) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Passwort bestätigen") }, visualTransformation = PasswordVisualTransformation())
            if (importing) Text("Die Wiederherstellung ersetzt alle lokalen Daten.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = password.length >= 8 && (importing || password == confirmation), onClick = { onConfirm(password.toCharArray()) }) { Text(if (importing) "Endgültig wiederherstellen" else "Exportieren") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}
