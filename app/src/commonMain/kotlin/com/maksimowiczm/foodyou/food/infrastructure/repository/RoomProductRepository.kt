package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.infrastructure.room.toDomain
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntity
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntityNutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.toNutritionFacts
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import com.maksimowiczm.foodyou.food.domain.entity.FddbPortion
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.distinctByNormalizedLabel
import com.maksimowiczm.foodyou.food.domain.entity.normalizedLabel
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductDao
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductPortionDao
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductPortionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomProductRepository(
    private val productDao: ProductDao,
    private val productPortionDao: ProductPortionDao,
) : ProductRepository {
    override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
        productDao.observeProducts(limit, offset).map { list -> list.map { it.toModel() } }

    override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
        productDao.observeProductCountBySource(type.toEntity())

    override fun observeProduct(id: FoodId.Product): Flow<Product?> =
        combine(
            productDao.observeProduct(id.id),
            productPortionDao.observeProductPortions(id.id),
        ) { product, portions ->
            product?.toModel(portions)
        }

    override fun observeProductByBarcode(barcode: String): Flow<Product?> =
        productDao.observeProductByBarcode(barcode).map { it?.toModel() }

    override suspend fun getProductByBarcode(barcode: String): Product? =
        productDao.getProductByBarcode(barcode)?.let { product ->
            product.toModel(productPortionDao.getProductPortions(product.id))
        }

    override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
        productDao.getProductBySource(type.toEntity(), url)?.let { product ->
            product.toModel(productPortionDao.getProductPortions(product.id))
        }

    override suspend fun deleteProduct(product: Product) {
        val entity = product.toEntity()
        productDao.deleteProduct(entity)
    }

    override suspend fun deleteProductsBySource(type: FoodSource.Type): Int =
        productDao.deleteProductsBySource(type.toEntity())

    override suspend fun insertProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
    ): FoodId.Product {
        val product =
            Product(
                id = FoodId.Product(0), // Temporary ID, will be replaced upon insertion
                name = name,
                brand = brand,
                barcode = barcode,
                note = note,
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                portions = emptyList(),
                source = source,
                nutritionFacts = nutritionFacts,
            )
        val entity = product.toEntity()
        val id = productDao.insertProduct(entity)
        return FoodId.Product(id)
    }

    override suspend fun insertUniqueProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
    ): FoodId.Product? {
        val product =
            Product(
                id = FoodId.Product(0), // Temporary ID, will be replaced upon insertion
                name = name,
                brand = brand,
                barcode = barcode,
                note = note,
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                portions = emptyList(),
                source = source,
                nutritionFacts = nutritionFacts,
            )
        return productDao.insertUniqueProduct(product.toEntity())?.let(FoodId::Product)
    }

    override suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.toEntity())
    }

    override suspend fun replaceProductPortions(
        productId: FoodId.Product,
        sourceType: FoodSource.Type,
        portions: List<FddbPortion>,
    ) {
        val sourceTypeEntity = sourceType.toEntity()
        val distinctPortions = portions.distinctByNormalizedLabel()
        productPortionDao.deleteProductPortions(productId.id, sourceTypeEntity)
        if (distinctPortions.isNotEmpty()) {
            productPortionDao.insertProductPortions(
                distinctPortions.map { it.toEntity(productId.id, sourceTypeEntity) }
            )
        }
    }
}

private fun ProductEntity.toModel(portions: List<ProductPortionEntity> = emptyList()): Product =
    Product(
        id = FoodId.Product(this.id),
        name = this.name,
        brand = this.brand,
        barcode = this.barcode,
        note = this.note,
        isLiquid = this.isLiquid,
        packageWeight = this.packageWeight,
        servingWeight = this.servingWeight,
        portions = portions.mapNotNull { it.toModel() },
        source = FoodSource(type = this.sourceType.toDomain(), url = this.sourceUrl),
        nutritionFacts = this.toNutritionFacts(),
    )

private fun ProductEntity.toNutritionFacts(): NutritionFacts =
    toNutritionFacts(nutrients, vitamins, minerals)

private fun Product.toEntity(): ProductEntity {
    val (nutrients, vitamins, minerals) = toEntityNutrients(nutritionFacts)

    return ProductEntity(
        id = id.id,
        name = name,
        brand = brand,
        barcode = barcode,
        nutrients = nutrients,
        vitamins = vitamins,
        minerals = minerals,
        packageWeight = packageWeight,
        servingWeight = servingWeight,
        note = note,
        sourceType = source.type.toEntity(),
        sourceUrl = source.url,
        isLiquid = isLiquid,
    )
}

private fun FddbPortion.toEntity(
    productId: Long,
    sourceType: FoodSourceType,
): ProductPortionEntity =
    ProductPortionEntity(
        productId = productId,
        sourceType = sourceType,
        label = label,
        normalizedLabel = normalizedLabel(),
        amount = amount,
        unit =
            when (unit) {
                FddbPortion.Unit.Gram -> "g"
                FddbPortion.Unit.Milliliter -> "ml"
            },
    )

private fun ProductPortionEntity.toModel(): FddbPortion? {
    val unit =
        when (unit) {
            "g" -> FddbPortion.Unit.Gram
            "ml" -> FddbPortion.Unit.Milliliter
            else -> return null
        }

    return FddbPortion(label = label, amount = amount, unit = unit)
}
