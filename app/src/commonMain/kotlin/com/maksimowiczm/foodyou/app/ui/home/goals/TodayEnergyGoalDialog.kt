package com.maksimowiczm.foodyou.app.ui.home.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TodayEnergyGoalDialog(
    baseGoalKcal: Int,
    currentReductionKcal: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onReset: () -> Unit,
) {
    val energyFormatter = LocalEnergyFormatter.current
    var input by remember(currentReductionKcal) {
        mutableStateOf(
            currentReductionKcal
                ?.let(energyFormatter::fromKcal)
                ?.formatClipZeros("%.2f")
                .orEmpty()
        )
    }
    val reductionKcal =
        validatedTodayEnergyGoalReductionKcal(
            input = input,
            baseGoalKcal = baseGoalKcal.toDouble(),
            toKcal = energyFormatter::toKcal,
        )
    val showError = input.isNotBlank() && reductionKcal == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.goal_today_adjustment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.goal_today_adjustment)) },
                    suffix = { Text(energyFormatter.suffix()) },
                    supportingText = {
                        if (showError) {
                            Text(stringResource(Res.string.error_today_energy_goal_adjustment))
                        }
                    },
                    isError = showError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                reductionKcal?.let { reduction ->
                    val todayGoal = (baseGoalKcal - reduction).coerceAtLeast(0.0)
                    Text(
                        stringResource(
                            Res.string.goal_today_preview,
                            energyFormatter
                                .formatEnergy(baseGoalKcal, withSuffix = false)
                                .groupDigits(),
                            energyFormatter
                                .formatEnergy(reduction, withSuffix = false)
                                .groupDigits(),
                            energyFormatter.formatEnergy(todayGoal),
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reductionKcal != null,
                onClick = { reductionKcal?.let(onSave) },
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (currentReductionKcal != null) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(Res.string.action_reset))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        },
    )
}

internal fun validatedTodayEnergyGoalReductionKcal(
    input: String,
    baseGoalKcal: Double,
    toKcal: (Double) -> Double,
): Double? =
    input
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?.let(toKcal)
        ?.takeIf { it > 0.0 && it <= baseGoalKcal }
