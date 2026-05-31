package com.maksimowiczm.foodyou.importexport.swissfoodcompositiondatabase.domain

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.importexport.domain.entity.ProductField
import com.maksimowiczm.foodyou.importexport.domain.usecase.ImportCsvProductUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

fun interface ImportSwissFoodCompositionDatabaseUseCase {
    suspend fun import(languages: Set<SwissFoodCompositionDatabaseRepository.Language>): Flow<Int>
}

fun interface DeleteSwissFoodCompositionDatabaseUseCase {
    suspend fun delete(): Int
}

internal class DeleteSwissFoodCompositionDatabaseUseCaseImpl(
    private val transactionProvider: TransactionProvider,
    private val productRepository: ProductRepository,
) : DeleteSwissFoodCompositionDatabaseUseCase {
    override suspend fun delete(): Int =
        transactionProvider.withTransaction {
            productRepository.deleteProductsBySource(FoodSource.Type.SwissFoodCompositionDatabase)
        }
}

internal class ImportSwissFoodCompositionDatabaseUseCaseImpl(
    private val swissFoodCompositionDatabaseRepository: SwissFoodCompositionDatabaseRepository,
    private val importCsvProductUseCase: ImportCsvProductUseCase,
) : ImportSwissFoodCompositionDatabaseUseCase {
    override suspend fun import(
        languages: Set<SwissFoodCompositionDatabaseRepository.Language>
    ): Flow<Int> = flow {
        var count = 0
        languages
            .asFlow()
            .flatMapConcat {
                val lines = swissFoodCompositionDatabaseRepository.readCsvFile(it)
                importCsvProductUseCase.import(
                    mapper = order,
                    stream = lines,
                    source = FoodSource.Type.SwissFoodCompositionDatabase,
                    skipHeader = true,
                )
            }
            .collect { emit(count++) }
    }

    private val order =
        listOf(
            ProductField.Name,
            ProductField.Brand,
            ProductField.Barcode,
            ProductField.Proteins,
            ProductField.Carbohydrates,
            ProductField.Fats,
            ProductField.Energy,
            ProductField.SaturatedFats,
            ProductField.MonounsaturatedFats,
            ProductField.PolyunsaturatedFats,
            ProductField.Omega3,
            ProductField.Omega6,
            ProductField.Sugars,
            ProductField.Salt,
            ProductField.DietaryFiber,
            ProductField.Cholesterol,
            ProductField.Caffeine,
            ProductField.VitaminA,
            ProductField.VitaminB1,
            ProductField.VitaminB2,
            ProductField.VitaminB3,
            ProductField.VitaminB5,
            ProductField.VitaminB6,
            ProductField.VitaminB7,
            ProductField.VitaminB9,
            ProductField.VitaminB12,
            ProductField.VitaminC,
            ProductField.VitaminD,
            ProductField.VitaminE,
            ProductField.VitaminK,
            ProductField.Manganese,
            ProductField.Magnesium,
            ProductField.Potassium,
            ProductField.Calcium,
            ProductField.Copper,
            ProductField.Zinc,
            ProductField.Sodium,
            ProductField.Iron,
            ProductField.Phosphorus,
            ProductField.Selenium,
            ProductField.Iodine,
            ProductField.PackageWeight,
            ProductField.ServingWeight,
        )
}
