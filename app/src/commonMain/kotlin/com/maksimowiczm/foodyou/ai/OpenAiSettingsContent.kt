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
internal fun OpenAiSettingsContent(
    settings: OpenAiSettings,
    busy: Boolean,
    onSave: (OpenAiSettings, String?, Boolean) -> Unit,
    onTest: (Boolean) -> Unit,
) {
    var photo by remember(settings.photo) { mutableStateOf(settings.photo) }
    var nutrition by remember(settings.nutrition) { mutableStateOf(settings.nutrition) }
    var parallelism by remember(settings.photoParallelism) { mutableStateOf(settings.photoParallelism) }
    var parallelismMenu by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    val dirty = parallelism != settings.photoParallelism || photo != settings.photo || nutrition != settings.nutrition || key.isNotEmpty()
    HorizontalDivider()
    Text(stringResource(Res.string.openai_settings), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(Res.string.openai_privacy))
    if (settings.hasApiKey) Text(stringResource(Res.string.openai_saved_key))
    OutlinedTextField(key, { key = it }, label = { Text(stringResource(Res.string.openai_key)) },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth())
    Text(stringResource(Res.string.openai_photo), style = MaterialTheme.typography.titleMedium)
    OpenAiTaskFields(photo, !busy) { photo = it }
    Box {
        OutlinedButton(onClick = { parallelismMenu = true }, enabled = !busy) {
            Text(stringResource(Res.string.openai_photo_parallelism, parallelism))
        }
        DropdownMenu(parallelismMenu && !busy, { parallelismMenu = false }) {
            (1..5).forEach { value ->
                DropdownMenuItem(text = { Text(value.toString()) }, onClick = {
                    parallelism = value; parallelismMenu = false
                })
            }
        }
    }
    Text(stringResource(Res.string.openai_photo_parallelism_help), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.openai_nutrition), style = MaterialTheme.typography.titleMedium)
    OpenAiTaskFields(nutrition, !busy) { nutrition = it }
    Text(stringResource(Res.string.openai_effort_help), style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        onSave(settings.copy(photo = photo, nutrition = nutrition, photoParallelism = parallelism), key.takeIf { it.isNotBlank() }, false)
        key = ""
    }, enabled = !busy && photo.model.isNotBlank() && nutrition.model.isNotBlank()) { Text(stringResource(Res.string.openai_save)) }
    OutlinedButton(onClick = { onTest(false) }, enabled = !busy && !dirty && settings.hasApiKey) { Text(stringResource(Res.string.openai_test_photo)) }
    OutlinedButton(onClick = { onTest(true) }, enabled = !busy && !dirty && settings.hasApiKey) { Text(stringResource(Res.string.openai_test_nutrition)) }
    if (settings.hasApiKey) TextButton(onClick = { key = ""; onSave(settings, null, true) }, enabled = !busy) { Text(stringResource(Res.string.openai_remove)) }
}

@Composable
private fun OpenAiTaskFields(task: OpenAiTask, enabled: Boolean, onChange: (OpenAiTask) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var custom by remember(task.model) { mutableStateOf(task.model !in openAiModels) }
    var reset by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(Res.string.openai_model) + ": " + if (custom) stringResource(Res.string.openai_custom) else task.model)
        }
        DropdownMenu(expanded, { expanded = false }) {
            openAiModels.forEachIndexed { index, model ->
                val description = stringResource(when (index) { 0 -> Res.string.openai_luna; 1 -> Res.string.openai_sol; else -> Res.string.openai_astra })
                DropdownMenuItem(text = { Column { Text(model); Text(description, style = MaterialTheme.typography.bodySmall) } }, onClick = {
                    val changed = task.withModel(model)
                    reset = task.effort != null && changed.effort == null
                    custom = false; expanded = false; onChange(changed)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.openai_custom)) }, onClick = {
                custom = true; expanded = false; onChange(OpenAiTask(""))
            })
        }
    }
    if (custom) OutlinedTextField(task.model, { onChange(task.withModel(it.trim())) },
        label = { Text(stringResource(Res.string.openai_custom)) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    val efforts = supportedEfforts(task.model)
    if (efforts == null) {
        OutlinedTextField(task.effort.orEmpty(), { onChange(task.copy(effort = it.trim().takeIf(String::isNotEmpty))) },
            label = { Text(stringResource(Res.string.openai_effort)) },
            supportingText = { Text(stringResource(Res.string.openai_default) + " = " + "∅") },
            singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    } else {
        var effortMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { effortMenu = true }, enabled = enabled) {
                Text(stringResource(Res.string.openai_effort) + ": " + (task.effort ?: stringResource(Res.string.openai_default)))
            }
            DropdownMenu(effortMenu, { effortMenu = false }) {
                (listOf<String?>(null) + efforts).forEach { effort ->
                    DropdownMenuItem(text = { Text(effort ?: stringResource(Res.string.openai_default)) }, onClick = {
                        reset = false; effortMenu = false; onChange(task.copy(effort = effort))
                    })
                }
            }
        }
    }
    if (reset) Text(stringResource(Res.string.openai_reset), style = MaterialTheme.typography.bodySmall)
}
