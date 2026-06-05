package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPortionDao {
    @Query(
        """
        SELECT *
        FROM ProductPortion
        WHERE productId = :productId
        ORDER BY id
        """
    )
    fun observeProductPortions(productId: Long): Flow<List<ProductPortionEntity>>

    @Query(
        """
        SELECT *
        FROM ProductPortion
        WHERE productId = :productId
        ORDER BY id
        """
    )
    suspend fun getProductPortions(productId: Long): List<ProductPortionEntity>

    @Query(
        """
        DELETE FROM ProductPortion
        WHERE productId = :productId AND sourceType = :sourceType
        """
    )
    suspend fun deleteProductPortions(productId: Long, sourceType: FoodSourceType)

    @Insert suspend fun insertProductPortions(portions: List<ProductPortionEntity>)
}
