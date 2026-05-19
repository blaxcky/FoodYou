package com.maksimowiczm.foodyou.food.domain.repository

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlinx.coroutines.flow.Flow

interface FoodMeasurementSuggestionRepository {
    suspend fun insert(foodId: FoodId, measurement: Measurement)

    suspend fun findLatestByProductIdAndType(
        productId: FoodId.Product,
        type: MeasurementType,
    ): Measurement?

    fun observeByFoodId(foodId: FoodId, limit: Int): Flow<List<Measurement>>
}
