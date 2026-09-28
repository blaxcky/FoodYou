package com.maksimowiczm.foodyou.app.ui.home.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.training.ImportedActivity
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ActivitiesCard(
    homeState: HomeState,
    onAdd: (epochDay: Long) -> Unit,
    onEdit: (id: Long) -> Unit,
    onEditImported: (id: Long) -> Unit,
    onStepExclusions: (epochDay: Long) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivitiesCardViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }
    val model = viewModel.model.collectAsStateWithLifecycle().value
    ActivitiesCardContent(
        model = model,
        onAdd = { onAdd(homeState.selectedDate.toEpochDays()) },
        onEdit = onEdit,
        onEditImported = onEditImported,
        onStepExclusions = { onStepExclusions(homeState.selectedDate.toEpochDays()) },
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@Composable
internal fun ActivitiesCardContent(
    model: ActivitiesCardModel?,
    onAdd: () -> Unit,
    onEdit: (id: Long) -> Unit,
    onEditImported: (id: Long) -> Unit,
    onStepExclusions: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current
    FoodYouHomeCard(
        modifier = modifier,
        color = Color.White,
        onClick = onAdd,
        onLongClick = onLongClick,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.headline_activities),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                val cardModel = model
                if (cardModel != null) {
                    Text(
                        text = energyFormatter.formatEnergy(-cardModel.totalEnergyKcal),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            val cardModel = model
            if (cardModel == null) {
                Text(
                    text = stringResource(Res.string.neutral_loading_activity),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                ActivitySectionHeader(stringResource(Res.string.headline_activity_section_steps))
                ActivityRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    label =
                        stringResource(
                            Res.string.neutral_x_steps,
                            cardModel.countedSteps.toString().groupDigits(),
                        ),
                    supportingText =
                        if (cardModel.healthConnectStepsEnabled) {
                            stringResource(
                                Res.string.neutral_x_excluded_steps_short,
                                cardModel.excludedSteps.toString().groupDigits(),
                            )
                        } else {
                            null
                        },
                    energy = energyFormatter.formatEnergy(-cardModel.stepEnergyKcal),
                    onClick = if (cardModel.healthConnectStepsEnabled) onStepExclusions else null,
                )

                if (cardModel.importedEntries.isNotEmpty()) {
                    SectionDivider()
                    ActivitySectionHeader(
                        text = stringResource(Res.string.headline_activity_section_training_app),
                        icon = Icons.Filled.Sync,
                    )
                    cardModel.importedEntries.forEach { entry ->
                        ActivityRow(
                            leadingIcon = {
                                Icon(
                                    imageVector = entry.icon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            label = entry.name,
                            energy = energyFormatter.formatEnergy(-entry.energyKcal.toDouble()),
                            onClick = { onEditImported(entry.id.value) },
                        )
                    }
                }

                if (cardModel.manualEntries.isNotEmpty()) {
                    SectionDivider()
                    ActivitySectionHeader(stringResource(Res.string.headline_activity_section_manual))
                    cardModel.manualEntries.forEach { entry ->
                        ActivityRow(
                            label = entry.name,
                            energy = energyFormatter.formatEnergy(-entry.energyKcal.toInt()),
                            onClick = { onEdit(entry.id.value) },
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.action_add_activity),
                    modifier = Modifier.padding(start = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ActivitySectionHeader(text: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun ActivityRow(
    label: String,
    energy: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (energy != null) Text(text = energy, style = MaterialTheme.typography.labelLarge)
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ImportedActivity.icon(): ImageVector =
    when (importId.substringAfterLast(':')) {
        "cardio" -> Icons.Filled.MonitorHeart
        "strength" -> Icons.Filled.FitnessCenter
        else -> Icons.AutoMirrored.Filled.DirectionsRun
    }

private fun String.groupDigits(): String {
    val sign = if (startsWith("-")) "-" else ""
    val digits = if (sign.isEmpty()) this else drop(1)

    if (digits.length <= 3 || digits.any { !it.isDigit() }) return this

    return sign + digits.reversed().chunked(3).joinToString(" ").reversed()
}
