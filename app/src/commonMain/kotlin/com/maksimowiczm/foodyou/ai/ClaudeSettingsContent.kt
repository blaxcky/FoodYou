package com.maksimowiczm.foodyou.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClaudeSettingsContent(
    settings: ClaudeSettings,
    busy: Boolean,
    onSave: (ClaudeSettings, String?, Boolean) -> Unit,
    onTest: (Boolean) -> Unit,
) {
    var photo by remember(settings.photo) { mutableStateOf(settings.photo) }
    var nutrition by remember(settings.nutrition) { mutableStateOf(settings.nutrition) }
    var parallelism by remember(settings.photoParallelism) { mutableStateOf(settings.photoParallelism) }
    var parallelismMenu by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    val dirty = parallelism != settings.photoParallelism || photo != settings.photo || nutrition != settings.nutrition || key.isNotEmpty()
    HorizontalDivider()
    Text(stringResource(Res.string.claude_settings), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(Res.string.claude_privacy))
    if (settings.hasApiKey) Text(stringResource(Res.string.claude_saved_key))
    OutlinedTextField(key, { key = it }, label = { Text(stringResource(Res.string.claude_key)) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth())
    Text(stringResource(Res.string.claude_photo), style = MaterialTheme.typography.titleMedium)
    ClaudeTaskFields(photo, !busy) { photo = it }
    Box {
        OutlinedButton(onClick = { parallelismMenu = true }, enabled = !busy) {
            Text(stringResource(Res.string.claude_photo_parallelism, parallelism))
        }
        DropdownMenu(parallelismMenu && !busy, { parallelismMenu = false }) {
            (1..5).forEach { value ->
                DropdownMenuItem(text = { Text(value.toString()) }, onClick = {
                    parallelism = value; parallelismMenu = false
                })
            }
        }
    }
    Text(stringResource(Res.string.claude_photo_parallelism_help), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.claude_nutrition), style = MaterialTheme.typography.titleMedium)
    ClaudeTaskFields(nutrition, !busy) { nutrition = it }
    Text(stringResource(Res.string.claude_effort_help), style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        onSave(settings.copy(photo = photo, nutrition = nutrition, photoParallelism = parallelism), key.takeIf { it.isNotBlank() }, false)
        key = ""
    }, enabled = !busy && photo.model.isNotBlank() && nutrition.model.isNotBlank()) { Text(stringResource(Res.string.claude_save)) }
    OutlinedButton(onClick = { onTest(false) }, enabled = !busy && !dirty && settings.hasApiKey) { Text(stringResource(Res.string.claude_test_photo)) }
    OutlinedButton(onClick = { onTest(true) }, enabled = !busy && !dirty && settings.hasApiKey) { Text(stringResource(Res.string.claude_test_nutrition)) }
    if (settings.hasApiKey) TextButton(onClick = { key = ""; onSave(settings, null, true) }, enabled = !busy) { Text(stringResource(Res.string.claude_remove)) }
}

@Composable
private fun ClaudeTaskFields(task: ClaudeTask, enabled: Boolean, onChange: (ClaudeTask) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var custom by remember(task.model) { mutableStateOf(task.model !in claudeModels) }
    var reset by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(Res.string.claude_model) + ": " + if (custom) stringResource(Res.string.claude_custom) else task.model)
        }
        DropdownMenu(expanded, { expanded = false }) {
            claudeModels.forEachIndexed { index, model ->
                val description = stringResource(when (index) { 0 -> Res.string.claude_luna; 1 -> Res.string.claude_sol; else -> Res.string.claude_astra })
                DropdownMenuItem(text = { Column { Text(model); Text(description, style = MaterialTheme.typography.bodySmall) } }, onClick = {
                    val changed = task.withModel(model)
                    reset = task.effort != null && changed.effort == null
                    custom = false; expanded = false; onChange(changed)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.claude_custom)) }, onClick = {
                custom = true; expanded = false; onChange(ClaudeTask(""))
            })
        }
    }
    if (custom) OutlinedTextField(task.model, { onChange(task.withModel(it.trim())) },
        label = { Text(stringResource(Res.string.claude_custom)) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    val efforts = claudeSupportedEfforts(task.model)
    if (efforts == null) {
        OutlinedTextField(task.effort.orEmpty(), { onChange(task.copy(effort = it.trim().takeIf(String::isNotEmpty))) },
            label = { Text(stringResource(Res.string.claude_effort)) },
            supportingText = { Text(stringResource(Res.string.claude_default_help)) },
            singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    } else {
        var effortMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { effortMenu = true }, enabled = enabled) {
                Text(stringResource(Res.string.claude_effort) + ": " + (task.effort ?: stringResource(Res.string.claude_default)))
            }
            DropdownMenu(effortMenu, { effortMenu = false }) {
                (listOf<String?>(null) + efforts).forEach { effort ->
                    DropdownMenuItem(text = { Text(effort ?: stringResource(Res.string.claude_default)) }, onClick = {
                        reset = false; effortMenu = false; onChange(task.copy(effort = effort))
                    })
                }
            }
        }
    }
    if (reset) Text(stringResource(Res.string.claude_reset), style = MaterialTheme.typography.bodySmall)
}
