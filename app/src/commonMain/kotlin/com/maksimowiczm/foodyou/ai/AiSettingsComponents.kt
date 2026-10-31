package com.maksimowiczm.foodyou.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Small primary heading with an icon above a group of settings. */
@Composable
internal fun AiSettingsSection(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Material 3 expressive list group: rows sit 2 dp apart; the group's 16 dp clip rounds the outer corners while each
 * row keeps 4 dp inner corners.
 */
@Composable
internal fun AiSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** Free-form row inside an [AiSettingsGroup], e.g. for text fields or the model download. */
@Composable
internal fun AiSettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun AiSettingsRow(
    headline: String,
    modifier: Modifier = Modifier,
    /** Current value shown under the headline, e.g. a dropdown's choice on narrow screens. */
    detail: String? = null,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    /** On/off state announced for [Role.Switch] rows. */
    checked: Boolean? = null,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.secondaryContainer else colors.surfaceContainerLow
    val content = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onSecondaryContainer
        else -> colors.onSurface
    }
    val click = when {
        onClick == null -> Modifier
        role == Role.RadioButton -> Modifier.selectable(selected, enabled = enabled, role = role, onClick = onClick)
        checked != null -> Modifier.toggleable(checked, enabled = enabled, role = role) { onClick() }
        else -> Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(container).then(click)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.bodyLarge)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = if (enabled) colors.primary else content)
                }
                supporting?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled && !selected) colors.onSurfaceVariant else content,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Icon in a round tonal container, as in the provider sheet of the quick capture. */
@Composable
internal fun AiRowIcon(icon: ImageVector, highlighted: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
    }
}

/** A row that shows its current value on the right and opens a menu of choices. */
@Composable
internal fun AiDropdownRow(
    label: String,
    value: String,
    enabled: Boolean,
    supporting: String? = null,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints {
        // Narrow screens and large fonts show the value below the label so neither gets squeezed.
        val compact = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
        AiSettingsRow(
            headline = label,
            detail = value.takeIf { compact },
            supporting = supporting,
            enabled = enabled,
            onClick = { expanded = true },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) Text(
                        value,
                        modifier = Modifier.widthIn(max = 160.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            },
        )
        DropdownMenu(expanded && enabled, { expanded = false }) { menu { expanded = false } }
    }
}

@Composable
internal fun aiEffortLabel(effort: String?): String = when (effort) {
    null -> stringResource(Res.string.ai_settings_effort_default)
    "none" -> stringResource(Res.string.ai_settings_effort_none)
    "low" -> stringResource(Res.string.ai_settings_effort_low)
    "medium" -> stringResource(Res.string.ai_settings_effort_medium)
    "high" -> stringResource(Res.string.ai_settings_effort_high)
    "xhigh" -> stringResource(Res.string.ai_settings_effort_xhigh)
    "max" -> stringResource(Res.string.ai_settings_effort_max)
    else -> effort
}

/**
 * Model and reasoning-effort rows for one OpenAI or Claude task. [models] pairs each known model ID with a short
 * description; any other ID is entered as a custom model whose effort is free text.
 */
@Composable
internal fun CloudTaskFields(
    model: String,
    effort: String?,
    models: List<Pair<String, String>>,
    supportedEfforts: (String) -> List<String>?,
    enabled: Boolean,
    onChange: (model: String, effort: String?) -> Unit,
) {
    var custom by remember(model) { mutableStateOf(models.none { it.first == model }) }
    var reset by remember { mutableStateOf(false) }
    val keptEffort = { newModel: String -> effort?.takeIf { supportedEfforts(newModel)?.contains(it) == true } }
    AiDropdownRow(
        label = stringResource(Res.string.ai_settings_model),
        value = if (custom) stringResource(Res.string.ai_settings_custom_model) else model,
        supporting = models.firstOrNull { it.first == model && !custom }?.second,
        enabled = enabled,
    ) { close ->
        models.forEach { (id, description) ->
            DropdownMenuItem(
                text = { Column { Text(id); Text(description, style = MaterialTheme.typography.bodySmall) } },
                onClick = {
                    val kept = keptEffort(id)
                    reset = effort != null && kept == null
                    custom = false; close(); onChange(id, kept)
                },
            )
        }
        DropdownMenuItem(text = { Text(stringResource(Res.string.ai_settings_custom_model)) }, onClick = {
            custom = true; reset = false; close(); onChange("", null)
        })
    }
    if (custom) AiSettingsBlock {
        OutlinedTextField(model, { onChange(it.trim(), keptEffort(it.trim())) },
            label = { Text(stringResource(Res.string.ai_settings_custom_model)) }, singleLine = true, enabled = enabled,
            modifier = Modifier.fillMaxWidth())
    }
    val efforts = supportedEfforts(model)
    if (efforts == null) AiSettingsBlock {
        OutlinedTextField(effort.orEmpty(), { onChange(model, it.trim().takeIf(String::isNotEmpty)) },
            label = { Text(stringResource(Res.string.ai_settings_effort)) },
            supportingText = { Text(stringResource(Res.string.ai_settings_effort_empty_help)) },
            singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    } else AiDropdownRow(
        label = stringResource(Res.string.ai_settings_effort),
        value = aiEffortLabel(effort),
        supporting = stringResource(if (reset) Res.string.ai_settings_effort_reset else Res.string.ai_settings_effort_help),
        enabled = enabled,
    ) { close ->
        (listOf<String?>(null) + efforts).forEach { value ->
            DropdownMenuItem(text = { Text(aiEffortLabel(value)) }, onClick = {
                reset = false; close(); onChange(model, value)
            })
        }
    }
}
