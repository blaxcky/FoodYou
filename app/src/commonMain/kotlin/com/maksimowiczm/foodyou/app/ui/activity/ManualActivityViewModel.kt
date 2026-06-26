package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import com.maksimowiczm.foodyou.activity.domain.usecase.calculateDiscountedActivityEnergyKcal
import com.maksimowiczm.foodyou.activity.domain.usecase.toCompleteActivityDiscountPercentOrNull
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.math.round
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class ManualActivityViewModel(
    private val repository: ActivityRepository,
    private val settingsRepository: UserPreferencesRepository<Settings>,
) : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _energyKcal = MutableStateFlow("")
    val energyKcal: StateFlow<String> = _energyKcal
    private val _discountPercent = MutableStateFlow("")
    val discountPercent: StateFlow<String> = _discountPercent
    private val _preset = MutableStateFlow<ManualActivityPreset?>(null)
    val preset: StateFlow<ManualActivityPreset?> = _preset
    val calculatedEnergyKcal =
        combine(_energyKcal, _discountPercent, _preset) { energy, discount, preset ->
            if (preset != ManualActivityPreset.Crosstrainer) {
                null
            } else {
                val parsedEnergy = energy.replace(',', '.').toDoubleOrNull()
                val parsedDiscount = discount.toCompleteActivityDiscountPercentOrNull()
                if (parsedEnergy != null && parsedDiscount != null) {
                    calculateDiscountedActivityEnergyKcal(parsedEnergy, parsedDiscount)
                        .formatWithMaximumTwoDecimals()
                } else {
                    null
                }
            }
        }

    init {
        viewModelScope.launch {
            settingsRepository
                .observe()
                .map { it.crosstrainerCalorieDiscountPercent.formatWithMaximumTwoDecimals() }
                .collect { _discountPercent.value = it }
        }
    }

    fun setName(value: String) {
        _name.value = value
        if (_preset.value?.activityName != value) {
            _preset.value = null
        }
    }

    fun setEnergyKcal(value: String) {
        _energyKcal.value = value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    fun setDiscountPercent(value: String) {
        _discountPercent.value = value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    fun selectPreset(preset: ManualActivityPreset) {
        _preset.value = preset
        _name.value = preset.activityName
    }

    suspend fun load(id: Long) {
        val entry = repository.observeManualEntry(ManualActivityEntryId(id)).filterNotNull().first()
        setName(entry.name)
        setEnergyKcal(entry.energyKcal.formatWithMaximumTwoDecimals())
        _preset.value = null
    }

    fun save(date: LocalDate, id: Long?, onSaved: () -> Unit) {
        val parsedEnergy = energyKcal.value.replace(',', '.').toDoubleOrNull() ?: return
        val trimmedName = name.value.trim().ifBlank { return }
        val selectedPreset = preset.value.takeIf { id == null }
        val parsedDiscount =
            if (selectedPreset == ManualActivityPreset.Crosstrainer) {
                discountPercent.value.toCompleteActivityDiscountPercentOrNull() ?: return
            } else {
                null
            }
        val energyToSave =
            if (parsedDiscount != null) {
                calculateDiscountedActivityEnergyKcal(parsedEnergy, parsedDiscount)
            } else {
                parsedEnergy
            }
        viewModelScope.launch {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val existing =
                id?.let {
                    repository.observeManualEntry(ManualActivityEntryId(it)).first()
                }
            val entry =
                ManualActivityEntry(
                    id = ManualActivityEntryId(id ?: 0),
                    date = existing?.date ?: date,
                    name = trimmedName,
                    energyKcal = energyToSave,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            if (id == null) repository.createManualEntry(entry) else repository.updateManualEntry(entry)
            if (selectedPreset == ManualActivityPreset.Crosstrainer) {
                settingsRepository.update {
                    copy(crosstrainerCalorieDiscountPercent = parsedDiscount ?: 0.0)
                }
            }
            onSaved()
        }
    }

    fun delete(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteManualEntry(ManualActivityEntryId(id))
            onDeleted()
        }
    }
}

internal enum class ManualActivityPreset(val activityName: String) {
    Crosstrainer("Crosstrainer"),
}

internal fun Double.formatWithMaximumTwoDecimals(): String {
    val rounded = round(this * 100.0) / 100.0
    return rounded.toString().trimEnd('0').trimEnd('.')
}
