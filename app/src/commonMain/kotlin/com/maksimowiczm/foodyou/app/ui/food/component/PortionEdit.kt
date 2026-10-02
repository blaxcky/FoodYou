package com.maksimowiczm.foodyou.app.ui.food.component

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.normalizedLabel

sealed interface PortionEditResult {
    data class Success(val portions: List<ProductPortion>) : PortionEditResult

    data object EmptyName : PortionEditResult

    data object InvalidAmount : PortionEditResult

    data object DuplicateName : PortionEditResult
}

/**
 * Adds ([original] is null), replaces, or deletes ([edited] is null) a portion. Portions are
 * matched by their normalized label, the same key the repository uses for overrides.
 */
fun List<ProductPortion>.withPortionEdit(
    original: ProductPortion?,
    edited: ProductPortion?,
): PortionEditResult {
    val originalKey = original?.normalizedLabel()

    if (edited == null) {
        return PortionEditResult.Success(filterNot { it.normalizedLabel() == originalKey })
    }

    val portion = edited.copy(label = edited.label.trim())
    if (portion.label.isEmpty()) return PortionEditResult.EmptyName
    if (!portion.amount.isFinite() || portion.amount <= 0.0) {
        return PortionEditResult.InvalidAmount
    }

    val editedKey = portion.normalizedLabel()
    if (any { it.normalizedLabel() == editedKey && it.normalizedLabel() != originalKey }) {
        return PortionEditResult.DuplicateName
    }

    val index = indexOfFirst { it.normalizedLabel() == originalKey }
    val portions =
        if (originalKey == null || index < 0) {
            this + portion
        } else {
            toMutableList().apply { set(index, portion) }
        }

    return PortionEditResult.Success(portions)
}

/** Returns the portion whose single unit equals [measurement], e.g. 150 g for "1 medium". */
fun List<MeasurementPickerOption.Portion>.portionMatching(
    measurement: Measurement
): MeasurementPickerOption.Portion? = firstOrNull { it.unitMeasurement == measurement }

/** Finds the option for [portion] by its normalized label after the portion list changed. */
fun List<MeasurementPickerOption.Portion>.findByPortionLabel(
    portion: ProductPortion
): MeasurementPickerOption.Portion? {
    val key = portion.normalizedLabel()
    return firstOrNull { it.portion?.normalizedLabel() == key }
}
