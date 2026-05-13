package com.maksimowiczm.foodyou.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntry
import com.maksimowiczm.foodyou.activity.domain.entity.ManualActivityEntryId
import com.maksimowiczm.foodyou.activity.domain.repository.ActivityRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class ManualActivityViewModel(private val repository: ActivityRepository) : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _energyKcal = MutableStateFlow("")
    val energyKcal: StateFlow<String> = _energyKcal

    fun setName(value: String) {
        _name.value = value
    }

    fun setEnergyKcal(value: String) {
        _energyKcal.value = value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    suspend fun load(id: Long) {
        val entry = repository.observeManualEntry(ManualActivityEntryId(id)).filterNotNull().first()
        setName(entry.name)
        setEnergyKcal(entry.energyKcal.toInt().toString())
    }

    fun save(date: LocalDate, id: Long?, onSaved: () -> Unit) {
        val parsedEnergy = energyKcal.value.replace(',', '.').toDoubleOrNull() ?: return
        val trimmedName = name.value.trim().ifBlank { return }
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
                    energyKcal = parsedEnergy,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            if (id == null) repository.createManualEntry(entry) else repository.updateManualEntry(entry)
            onSaved()
        }
    }
}
