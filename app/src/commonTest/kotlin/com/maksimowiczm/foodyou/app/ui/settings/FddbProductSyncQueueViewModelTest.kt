package com.maksimowiczm.foodyou.app.ui.settings

import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FddbProduct
import com.maksimowiczm.foodyou.food.domain.entity.FddbProductSyncQueueItem
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.ProductPortion
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ResyncFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.FddbProductSyncCoordinator
import com.maksimowiczm.foodyou.food.domain.usecase.SyncDueFddbProductsUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.SyncFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UnlinkFddbProductUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.UpdateFddbProductLinkUseCase
import com.maksimowiczm.foodyou.settings.domain.entity.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class FddbProductSyncQueueViewModelTest {
    @Test
    fun modelShowsNextAutomaticSyncOnlyWhileCooldownIsActive() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.model.collect()
        }
        try {
            fixture.settings.update {
                copy(
                    fddbProductSyncLastAttemptEpochSeconds =
                        FixedDateProvider.nowInstant().epochSeconds
                )
            }
            advanceUntilIdle()

            assertEquals(
                Instant.parse("2026-09-25T12:30:00Z"),
                viewModel.model.value.nextAutomaticSyncAt,
            )

            fixture.settings.update {
                copy(
                    fddbProductSyncLastAttemptEpochSeconds =
                        FixedDateProvider.nowInstant().epochSeconds - 1_800
                )
            }
            advanceUntilIdle()

            assertNull(viewModel.model.value.nextAutomaticSyncAt)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changingLinkImmediatelySyncsExactlyOnce() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        try {
            viewModel.updateLink(ProductId, NewUrl)
            advanceUntilIdle()

            assertEquals(NewUrl, fixture.products[ProductId].source.url)
            assertEquals(listOf(NewUrl), fixture.gateway.requestedUrls)
            assertEquals(listOf(ProductId), fixture.status.successes)
            assertFalse(viewModel.actionState.value.inProgress)
            assertNull(viewModel.actionState.value.error)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidLinkSurfacesValidationErrorWithoutSync() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        try {
            viewModel.updateLink(ProductId, "https://example.com/product")
            advanceUntilIdle()

            assertEquals(FddbProductSyncActionError.InvalidUrl, viewModel.actionState.value.error)
            assertEquals(emptyList(), fixture.gateway.requestedUrls)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unlinkKeepsProductAndClearsUrlAndStatus() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        val original = fixture.products[ProductId]
        val viewModel = fixture.viewModel()
        try {
            viewModel.unlink(ProductId)
            advanceUntilIdle()

            assertEquals(original.copy(source = original.source.copy(url = null)), fixture.products[ProductId])
            assertEquals(listOf(ProductId), fixture.status.cleared)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class Fixture {
        val products = FakeProductRepository(product())
        val status = FakeStatusRepository()
        val gateway = FakeGateway()
        val settings = FakeSettingsRepository()

        fun viewModel(): FddbProductSyncQueueViewModel {
            val resync =
                ResyncFddbProductUseCase(products, gateway, ImmediateTransactionProvider, NoopLogger)
            val sync = SyncFddbProductUseCase(status, resync, FixedDateProvider, settings)
            val coordinator =
                FddbProductSyncCoordinator(
                    settingsRepository = settings,
                    statusRepository = status,
                    syncDueFddbProductsUseCase = SyncDueFddbProductsUseCase(status, sync),
                    syncFddbProductUseCase = sync,
                    dateProvider = FixedDateProvider,
                )
            return FddbProductSyncQueueViewModel(
                statusRepository = status,
                settingsRepository = settings,
                dateProvider = FixedDateProvider,
                fddbProductSyncCoordinator = coordinator,
                updateFddbProductLinkUseCase =
                    UpdateFddbProductLinkUseCase(products, ImmediateTransactionProvider),
                unlinkFddbProductUseCase =
                    UnlinkFddbProductUseCase(products, status, ImmediateTransactionProvider),
            )
        }
    }

    private class FakeGateway : FddbProductGateway {
        val requestedUrls = mutableListOf<String>()

        override suspend fun getProduct(url: String): FddbProduct {
            requestedUrls += url
            return FddbProduct(
                name = "Remote",
                brand = null,
                barcode = null,
                isLiquid = false,
                packageWeight = 250.0,
                servingWeight = 25.0,
                portions = emptyList(),
                nutritionFacts = nutrition(),
            )
        }
    }

    private class FakeStatusRepository : FddbProductSyncStatusRepository {
        val successes = mutableListOf<FoodId.Product>()
        val cleared = mutableListOf<FoodId.Product>()

        override fun observeQueue(): Flow<List<FddbProductSyncQueueItem>> = flowOf(emptyList())

        override suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueItem> = emptyList()

        override suspend fun markAttempt(productId: FoodId.Product, attemptedAt: Instant) = Unit

        override suspend fun markSuccess(productId: FoodId.Product, syncedAt: Instant) {
            successes += productId
        }

        override suspend fun markFailure(
            productId: FoodId.Product,
            attemptedAt: Instant,
            error: String,
        ) = Unit

        override suspend fun clear(productId: FoodId.Product) {
            cleared += productId
        }
    }

    private class FakeProductRepository(initialProduct: Product) : ProductRepository {
        private val values = MutableStateFlow(listOf(initialProduct))

        operator fun get(id: FoodId.Product): Product = values.value.single { it.id == id }

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            values.map { products -> products.firstOrNull { it.id == id } }

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)
        override suspend fun getProductByBarcode(barcode: String): Product? = null
        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? =
            values.value.firstOrNull { it.source.type == type && it.source.url == url }
        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> = flowOf(values.value)
        override fun observeProductsBySource(type: FoodSource.Type, limit: Int, offset: Int): Flow<List<Product>> =
            values.map { products -> products.filter { it.source.type == type } }
        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
            values.map { products -> products.count { it.source.type == type } }
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
        ): FoodId.Product = error("Not used")
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
        ): FoodId.Product? = error("Not used")
        override suspend fun updateProduct(product: Product) {
            values.value = values.value.map { if (it.id == product.id) product else it }
        }
        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<ProductPortion>,
        ) = Unit
        override suspend fun deleteProduct(product: Product) = error("Must not delete")
        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = error("Must not delete")
    }

    private class FakeSettingsRepository : UserPreferencesRepository<Settings> {
        private val settings = MutableStateFlow(defaultSettings())
        override fun observe(): Flow<Settings> = settings
        override suspend fun update(transform: Settings.() -> Settings) {
            settings.value = transform(settings.value)
        }
    }

    private object FixedDateProvider : DateProvider {
        private val now = Instant.parse("2026-09-25T12:00:00Z")
        override fun nowInstant(): Instant = now
        override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(now)
        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(LocalDate(2026, 9, 25))
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(object : TransactionScope<T> {
                override suspend fun rollback(result: T) = Unit
            })
    }

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        val ProductId = FoodId.Product(1)
        const val OldUrl = "https://fddb.info/db/de/lebensmittel/old_product/index.html"
        const val NewUrl = "https://fddb.info/db/de/lebensmittel/new_product/index.html"

        fun product() = Product(
            id = ProductId,
            name = "Product",
            brand = "Brand",
            barcode = "123",
            note = "Keep",
            isLiquid = false,
            packageWeight = 100.0,
            servingWeight = 10.0,
            portions = emptyList(),
            source = FoodSource(FoodSource.Type.FDDB, OldUrl),
            isFavorite = true,
            isQuickCapture = true,
            nutritionFacts = nutrition(),
        )

        fun nutrition() = NutritionFacts(
            energy = NutrientValue.Complete(100.0),
            proteins = NutrientValue.Complete(1.0),
            carbohydrates = NutrientValue.Complete(2.0),
            fats = NutrientValue.Complete(3.0),
        )

        fun defaultSettings() = Settings(
            lastRememberedVersion = null,
            hidePreviewDialog = false,
            showTranslationWarning = false,
            nutrientsOrder = NutrientsOrder.defaultOrder,
            secureScreen = false,
            homeCardOrder = HomeCard.defaultOrder,
            expandGoalCard = false,
            goalDisplayMode = GoalDisplayMode.Normal,
            dietEnergyDeficitKcal = null,
            onboardingFinished = true,
            energyFormat = EnergyFormat.DEFAULT,
            appLaunchInfo = AppLaunchInfo(null, null, 0),
            stepsCaloriesPerStepKcal = null,
            healthConnectStepsEnabled = false,
            healthConnectStepsLastSyncedEpochSeconds = null,
        )
    }
}
