package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maksimowiczm.foodyou.app.ui.food.component.Icon
import com.maksimowiczm.foodyou.app.ui.food.component.stringResource
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class FoodFilter(val source: Source = DefaultFilter) {

    companion object {
        val DefaultFilter = Source.All
    }

    val filterCount: Int
        get() {
            var count = 0

            if (source != DefaultFilter) {
                count++
            }

            return count
        }

    enum class Source {
        All,
        Recent,
        YourFood,
        OpenFoodFacts,
        USDA,
        SwissFoodCompositionDatabase,
        FDDB;

        @Composable
        fun Icon(modifier: Modifier = Modifier.Companion) =
            when (this) {
                All ->
                    androidx.compose.material3.Icon(
                        painter = painterResource(Res.drawable.ic_database_search),
                        contentDescription = null,
                        modifier = modifier,
                    )

                Recent ->
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = modifier,
                    )

                YourFood ->
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = modifier,
                    )

                OpenFoodFacts -> FoodSource.Type.OpenFoodFacts.Icon(modifier)
                USDA -> FoodSource.Type.USDA.Icon(modifier)
                SwissFoodCompositionDatabase -> FoodSource.Type.SwissFoodCompositionDatabase.Icon()
                FDDB -> FoodSource.Type.FDDB.Icon()
            }

        @Composable
        fun stringResource(): String =
            when (this) {
                All -> stringResource(Res.string.headline_all)
                Recent -> stringResource(Res.string.headline_recent)
                YourFood -> stringResource(Res.string.headline_your_food)
                OpenFoodFacts -> FoodSource.Type.OpenFoodFacts.stringResource()
                USDA -> FoodSource.Type.USDA.stringResource()
                SwissFoodCompositionDatabase ->
                    stringResource(Res.string.headline_swiss_food_composition_database)
                FDDB -> FoodSource.Type.FDDB.stringResource()
            }
    }
}
