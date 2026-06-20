package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
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

    @Query("SELECT * FROM ProductPortionOverride WHERE productId = :productId")
    suspend fun getOverrides(productId: Long): List<ProductPortionOverrideEntity>

    @Query("SELECT * FROM ProductPortionOverride WHERE productId = :productId")
    fun observeOverrides(productId: Long): Flow<List<ProductPortionOverrideEntity>>

    @Query("DELETE FROM ProductPortionOverride WHERE productId = :productId")
    suspend fun deleteOverrides(productId: Long)

    @Insert suspend fun insertOverrides(overrides: List<ProductPortionOverrideEntity>)
}
