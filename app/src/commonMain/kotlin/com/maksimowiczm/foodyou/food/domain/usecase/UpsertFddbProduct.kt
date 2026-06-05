package com.maksimowiczm.foodyou.food.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository

internal suspend fun ProductRepository.upsertFddbProduct(
    url: String,
    fddbProduct: FddbProduct,
): FddbProductUpsertResult {
    val existingProduct =
        getProductBySource(FoodSource.Type.FDDB, url)
            ?: fddbProduct.barcode?.let { getProductByBarcode(it) }

    if (existingProduct != null) {
        val product = existingProduct.withMissingFddbWeights(fddbProduct)
        if (product != existingProduct) {
            updateProduct(product)
        }
        replaceProductPortions(
            productId = product.id,
            sourceType = FoodSource.Type.FDDB,
            portions = fddbProduct.portions,
        )
        return FddbProductUpsertResult.Updated(
            product = product.copy(portions = fddbProduct.portions),
            changed = product != existingProduct || fddbProduct.portions.isNotEmpty(),
        )
    }

    val id =
        insertProduct(
            name = fddbProduct.name,
            brand = fddbProduct.brand,
            barcode = fddbProduct.barcode,
            note = null,
            isLiquid = fddbProduct.isLiquid,
            packageWeight = fddbProduct.packageWeight,
            servingWeight = fddbProduct.servingWeight,
            source = FoodSource(type = FoodSource.Type.FDDB, url = url),
            nutritionFacts = fddbProduct.nutritionFacts,
        )
    replaceProductPortions(
        productId = id,
        sourceType = FoodSource.Type.FDDB,
        portions = fddbProduct.portions,
    )

    return FddbProductUpsertResult.Inserted(
        Product(
            id = id,
            name = fddbProduct.name,
            brand = fddbProduct.brand,
            barcode = fddbProduct.barcode,
            note = null,
            isLiquid = fddbProduct.isLiquid,
            packageWeight = fddbProduct.packageWeight,
            servingWeight = fddbProduct.servingWeight,
            portions = fddbProduct.portions,
            source = FoodSource(type = FoodSource.Type.FDDB, url = url),
            nutritionFacts = fddbProduct.nutritionFacts,
        )
    )
}

internal sealed interface FddbProductUpsertResult {
    val product: Product

    data class Inserted(override val product: Product) : FddbProductUpsertResult

    data class Updated(override val product: Product, val changed: Boolean) : FddbProductUpsertResult
}

private fun Product.withMissingFddbWeights(product: FddbProduct): Product =
    copy(
        packageWeight = packageWeight ?: product.packageWeight,
        servingWeight = servingWeight ?: product.servingWeight,
    )
