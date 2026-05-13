package com.maksimowiczm.foodyou.app.ui.home.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
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
    FoodYouHomeCard(modifier = modifier, onClick = onAdd, onLongClick = onLongClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsWalk, contentDescription = null)
                Text(
                    text = "Activities",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "Add activity")
                }
            }

            val cardModel = model
            if (cardModel == null) {
                Text("Loading activity", color = MaterialTheme.colorScheme.outline)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActivityMetric("Steps", cardModel.steps.toString())
                    ActivityMetric(
                        "Step kcal",
                        energyFormatter.formatEnergy(cardModel.stepEnergyKcal),
                    )
                    ActivityMetric(
                        "Manual",
                        energyFormatter.formatEnergy(cardModel.manualEnergyKcal),
                    )
                }

                Text(
                    text = "Burned ${energyFormatter.formatEnergy(cardModel.totalEnergyKcal)}",
                    style = MaterialTheme.typography.titleLarge,
                )

                cardModel.manualEntries.take(3).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onEdit(entry.id.value) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(entry.name, modifier = Modifier.weight(1f))
                        Text(
                            text = energyFormatter.formatEnergy(entry.energyKcal.toInt()),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMetric(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(value, style = MaterialTheme.typography.bodyLargeEmphasized)
    }
}
