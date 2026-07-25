package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.infrastructure.room.toDomain
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntity
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntityNutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.toNutritionFacts
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.entity.distinctByNormalizedLabel
import com.maksimowiczm.foodyou.food.domain.entity.normalizedLabel
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductDao
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductPortionDao
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductPortionEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductPortionOverrideEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomProductRepository(
    private val productDao: ProductDao,
    private val productPortionDao: ProductPortionDao,
) : ProductRepository {
    override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
        productDao.observeProducts(limit, offset).map { list -> list.map { it.toModel() } }

    override fun observeProductsBySource(type: FoodSource.Type, limit: Int, offset: Int): Flow<List<Product>> =
        productDao.observeProductsBySource(type.toEntity(), limit, offset).map { list -> list.map { it.toModel() } }

    override fun observeQuickCaptureProducts(): Flow<List<Product>> =
        productDao.observeQuickCaptureProducts().map { list -> list.map { it.toModel() } }

    override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
        productDao.observeProductCountBySource(type.toEntity())

    override fun observeProduct(id: FoodId.Product): Flow<Product?> =
        combine(
            productDao.observeProduct(id.id),
            productPortionDao.observeProductPortions(id.id),
            productPortionDao.observeOverrides(id.id),
        ) { product, portions, overrides ->
            product?.toModel(portions, overrides)
        }

    override fun observeProductByBarcode(barcode: String): Flow<Product?> =
        productDao.observeProductByBarcode(barcode).map { it?.toModel() }

    override suspend fun getProductByBarcode(barcode: String): Product? =
        productDao.getProductByBarcode(barcode)?.let { product ->
            product.toModel(productPortionDao.getProductPortions(product.id), productPortionDao.getOverrides(product.id))
        }

    override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
        productDao.getProductBySource(type.toEntity(), url)?.let { product ->
            product.toModel(productPortionDao.getProductPortions(product.id), productPortionDao.getOverrides(product.id))
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
                isFavorite = false,
                isQuickCapture = false,
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
                isFavorite = false,
                isQuickCapture = false,
                nutritionFacts = nutritionFacts,
            )
        return productDao.insertUniqueProduct(product.toEntity())?.let(FoodId::Product)
    }

    override suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.toEntity())
    }

    override suspend fun setProductFavorite(id: FoodId.Product, isFavorite: Boolean) {
        productDao.setProductFavorite(id.id, isFavorite)
    }

    override suspend fun setProductQuickCapture(id: FoodId.Product, isQuickCapture: Boolean) {
        productDao.setProductQuickCapture(id.id, isQuickCapture)
    }

    override suspend fun replaceProductPortions(
        productId: FoodId.Product,
        sourceType: FoodSource.Type,
        portions: List<ProductPortion>,
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

    override suspend fun updateProductPortions(productId: FoodId.Product, portions: List<ProductPortion>) {
        val desired = portions.distinctByNormalizedLabel()
        val imported = productPortionDao.getProductPortions(productId.id).mapNotNull { it.toModel() }
        val importedByLabel = imported.associateBy { it.normalizedLabel() }
        val desiredByLabel = desired.associateBy { it.normalizedLabel() }
        val overrides = buildList {
            imported.forEach { base ->
                val desiredPortion = desiredByLabel[base.normalizedLabel()]
                when {
                    desiredPortion == null -> add(ProductPortionOverrideEntity(productId.id, base.normalizedLabel(), base.label, base.amount, base.unit.toStorageUnit(), true))
                    desiredPortion != base -> add(desiredPortion.toOverrideEntity(productId.id, false))
                }
            }
            desired.filter { it.normalizedLabel() !in importedByLabel }.forEach {
                add(it.toOverrideEntity(productId.id, false))
            }
        }
        productPortionDao.deleteOverrides(productId.id)
        if (overrides.isNotEmpty()) productPortionDao.insertOverrides(overrides)
    }
}

private fun ProductEntity.toModel(
    portions: List<ProductPortionEntity> = emptyList(),
    overrides: List<ProductPortionOverrideEntity> = emptyList(),
): Product =
    Product(
        id = FoodId.Product(this.id),
        name = this.name,
        brand = this.brand,
        barcode = this.barcode,
        note = this.note,
        isLiquid = this.isLiquid,
        packageWeight = this.packageWeight,
        servingWeight = this.servingWeight,
        portions = portions.effectivePortions(overrides),
        source = FoodSource(type = this.sourceType.toDomain(), url = this.sourceUrl),
        isFavorite = this.isFavorite,
        isQuickCapture = this.isQuickCapture,
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
        isFavorite = isFavorite,
        isQuickCapture = isQuickCapture,
    )
}

private fun ProductPortion.toEntity(
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
                ProductPortion.Unit.Gram -> "g"
                ProductPortion.Unit.Milliliter -> "ml"
            },
    )

private fun ProductPortionEntity.toModel(): ProductPortion? {
    val unit =
        when (unit) {
            "g" -> ProductPortion.Unit.Gram
            "ml" -> ProductPortion.Unit.Milliliter
            else -> return null
        }

    return ProductPortion(label = label, amount = amount, unit = unit)
}

private fun List<ProductPortionEntity>.effectivePortions(
    overrides: List<ProductPortionOverrideEntity>
): List<ProductPortion> {
    val byLabel = overrides.associateBy { it.normalizedLabel }
    val result = mapNotNull { base ->
        val override = byLabel[base.normalizedLabel]
        when {
            override?.isDeleted == true -> null
            override != null -> override.toModel()
            else -> base.toModel()
        }
    }.toMutableList()
    val importedLabels = map { it.normalizedLabel }.toSet()
    overrides.filter { !it.isDeleted && it.normalizedLabel !in importedLabels }.forEach { override ->
        override.toModel()?.let(result::add)
    }
    return result.distinctByNormalizedLabel()
}

private fun ProductPortionOverrideEntity.toModel(): ProductPortion? =
    when (unit) {
        "g" -> ProductPortion(label, amount, ProductPortion.Unit.Gram)
        "ml" -> ProductPortion(label, amount, ProductPortion.Unit.Milliliter)
        else -> null
    }

private fun ProductPortion.toOverrideEntity(productId: Long, isDeleted: Boolean) =
    ProductPortionOverrideEntity(productId, normalizedLabel(), label, amount, unit.toStorageUnit(), isDeleted)

private fun ProductPortion.Unit.toStorageUnit() = when (this) {
    ProductPortion.Unit.Gram -> "g"
    ProductPortion.Unit.Milliliter -> "ml"
}
