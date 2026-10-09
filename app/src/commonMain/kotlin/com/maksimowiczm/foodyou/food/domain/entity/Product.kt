package com.maksimowiczm.foodyou.food.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType

/**
 * Represents a product in the food domain.
 *
 * @param id Unique identifier for the product.
 * @param name Name of the product.
 * @param brand Brand of the product, if available.
 * @param barcode Barcode of the product, if available.
 * @param note Additional note about the product, if available.
 * @param isLiquid Indicates whether the product is liquid (e.g., juice, milk).
 * @param packageWeight Weight of the product package, if available.
 * @param servingWeight Weight of a single serving of the product, if available.
 * @param source Source of the product.
 * @param isFavorite Whether the product is marked as a local favorite.
 * @param isQuickCapture Whether the product is available from the meal card quick-capture menu.
 * @param nutritionFacts Nutrition facts of the product per 100g or 100ml, depending on whether the
 *   product is solid or liquid.
 */
data class Product(
    override val id: FoodId.Product,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val note: String?,
    override val isLiquid: Boolean,
    val packageWeight: Double?,
    override val servingWeight: Double?,
    val portions: List<ProductPortion> = emptyList(),
    val source: FoodSource,
    val isFavorite: Boolean = false,
    val isQuickCapture: Boolean = false,
    override val nutritionFacts: NutritionFacts,
    val isPackagePortionHidden: Boolean = false,
    val isServingPortionHidden: Boolean = false,
) : Food {
    override val totalWeight: Double? = packageWeight

    override val headline = run {
        val brandSuffix =
            if (!brand.isNullOrEmpty()) {
                " ($brand)"
            } else {
                ""
            }

        "${name}$brandSuffix"
    }
}

/** Hides a deleted reference portion while retaining its weight for existing measurements. */
fun Food.isStandardPortionHidden(type: MeasurementType): Boolean =
    this is Product &&
        when (type) {
            MeasurementType.Package -> isPackagePortionHidden
            MeasurementType.Serving -> isServingPortionHidden
            else -> false
        }
