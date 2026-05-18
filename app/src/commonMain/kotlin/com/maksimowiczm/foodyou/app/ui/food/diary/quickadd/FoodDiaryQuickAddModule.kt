package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import kotlinx.datetime.LocalDate
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind

fun Module.foodDiaryQuickAdd() {
    factoryOf(::QuickAddCsvParserImpl).bind<QuickAddCsvParser>()

    viewModel { (date: LocalDate, mealId: Long) ->
        CreateQuickAddViewModel(
            mealId = mealId,
            date = date,
            manualDiaryEntryRepository = get(),
            dateProvider = get(),
        )
    }
    viewModelOf(::UpdateQuickAddViewModel)
}
