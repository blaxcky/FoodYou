package com.maksimowiczm.foodyou.app.ui.food.diary.search

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement

internal data class DiaryFoodSearchInput(
    val originalText: String = "",
    val searchText: String = "",
    val amount: Int? = null,
)

internal fun parseDiaryFoodSearchInput(input: String): DiaryFoodSearchInput {
    val separatorIndex = input.lastIndexOf(';')
    if (separatorIndex < 0) {
        return DiaryFoodSearchInput(originalText = input, searchText = input.trim())
    }

    val searchText = input.substring(0, separatorIndex).trim()
    val amountText = input.substring(separatorIndex + 1).trim()
    val amount =
        amountText
            .takeIf { text -> text.isNotEmpty() && text.all { it in '0'..'9' } }
            ?.trimStart('0')
            ?.ifEmpty { "0" }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    return if (searchText.isNotEmpty() && amount != null) {
        DiaryFoodSearchInput(
            originalText = input,
            searchText = searchText,
            amount = amount,
        )
    } else {
        DiaryFoodSearchInput(originalText = input, searchText = input.trim())
    }
}

internal fun diarySearchMeasurement(
    suggestedMeasurement: Measurement,
    isLiquid: Boolean,
    amount: Int?,
): Measurement =
    when {
        amount == null -> suggestedMeasurement
        isLiquid -> Measurement.Milliliter(amount.toDouble())
        else -> Measurement.Gram(amount.toDouble())
    }
