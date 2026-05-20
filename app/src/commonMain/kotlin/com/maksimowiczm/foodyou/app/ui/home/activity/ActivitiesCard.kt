package com.maksimowiczm.foodyou.app.ui.home.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ActivitiesCard(
    homeState: HomeState,
    onAdd: (epochDay: Long) -> Unit,
    onEdit: (id: Long) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivitiesCardViewModel = koinViewModel(),
) {
    LaunchedEffect(homeState.selectedDate) { viewModel.setDate(homeState.selectedDate) }
    val model = viewModel.model.collectAsStateWithLifecycle().value
    ActivitiesCard(
        model = model,
        onAdd = { onAdd(homeState.selectedDate.toEpochDays()) },
        onEdit = onEdit,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@Composable
private fun ActivitiesCard(
    model: ActivitiesCardModel?,
    onAdd: () -> Unit,
    onEdit: (id: Long) -> Unit,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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

            HorizontalDivider()

            val cardModel = model
            if (cardModel == null) {
                Text(
                    text = stringResource(Res.string.neutral_loading_activity),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
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
                            cardModel.steps.toString().groupDigits(),
                        ),
                    energy = energyFormatter.formatEnergy(-cardModel.stepEnergyKcal),
                    labelStyle = MaterialTheme.typography.labelLarge,
                    energyStyle = MaterialTheme.typography.labelLarge,
                )

                cardModel.manualEntries.forEach { entry ->
                    HorizontalDivider()
                    ActivityRow(
                        label = entry.name,
                        energy = energyFormatter.formatEnergy(-entry.energyKcal.toInt()),
                        modifier = Modifier.clickable { onEdit(entry.id.value) },
                    )
                }
            }

            HorizontalDivider()
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 14.dp),
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
private fun ActivityRow(
    label: String,
    energy: String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.titleMedium,
    energyStyle: TextStyle = MaterialTheme.typography.titleMedium,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        } else {
            Spacer(Modifier.padding(start = 24.dp))
        }
        Text(text = label, modifier = Modifier.weight(1f), style = labelStyle)
        Text(text = energy, style = energyStyle)
    }
}

private fun String.groupDigits(): String {
    val sign = if (startsWith("-")) "-" else ""
    val digits = if (sign.isEmpty()) this else drop(1)

    if (digits.length <= 3 || digits.any { !it.isDigit() }) return this

    return sign + digits.reversed().chunked(3).joinToString(" ").reversed()
}
