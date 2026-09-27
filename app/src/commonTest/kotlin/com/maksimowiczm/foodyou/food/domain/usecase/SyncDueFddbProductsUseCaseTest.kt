package com.maksimowiczm.foodyou.food.domain.usecase

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
import com.maksimowiczm.foodyou.food.domain.repository.FddbAccessBlockedException
import com.maksimowiczm.foodyou.food.domain.repository.FddbHttpException
import com.maksimowiczm.foodyou.food.domain.repository.FddbParseException
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductGateway
import com.maksimowiczm.foodyou.food.domain.repository.FddbProductSyncStatusRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbRequestPriority
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.settings.domain.entity.AppLaunchInfo
import com.maksimowiczm.foodyou.settings.domain.entity.EnergyFormat
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncManualFrequency
import com.maksimowiczm.foodyou.settings.domain.entity.FddbProductSyncMode
import com.maksimowiczm.foodyou.settings.domain.entity.GoalDisplayMode
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class SyncDueFddbProductsUseCaseTest {
    @Test
    fun manualSyncDoesNothingWhenDisabled() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                mode = FddbProductSyncMode.Disabled,
            )

        assertEquals(
            ManualFddbProductSyncResult.NotEnabled,
            fixture.coordinator.onManualFddbSyncCompleted(),
        )
        assertEquals(emptyList(), fixture.gateway.requestedUrls)
    }

    @Test
    fun manualTriggerCanRunEverySuccessfulDiarySync() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                mode = FddbProductSyncMode.WithManualFddbSync,
                frequency = FddbProductSyncManualFrequency.EverySync,
            )

        assertEquals(
            ManualFddbProductSyncResult.Completed(
                SyncDueFddbProductsResult(synced = 2, failed = 0, blocked = false)
            ),
            fixture.coordinator.onManualFddbSyncCompleted(),
        )
        assertEquals(listOf(Url(1), Url(2)), fixture.gateway.requestedUrls)
    }

    @Test
    fun manualTriggerRunsOnEveryThirdSuccessfulDiarySyncAndResetsCounter() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                mode = FddbProductSyncMode.WithManualFddbSync,
                frequency = FddbProductSyncManualFrequency.EveryThirdSync,
            )

        assertEquals(
            ManualFddbProductSyncResult.Waiting(1),
            fixture.coordinator.onManualFddbSyncCompleted(),
        )
        assertEquals(
            ManualFddbProductSyncResult.Waiting(2),
            fixture.coordinator.onManualFddbSyncCompleted(),
        )
        assertEquals(
            ManualFddbProductSyncResult.Completed(
                SyncDueFddbProductsResult(synced = 2, failed = 0, blocked = false)
            ),
            fixture.coordinator.onManualFddbSyncCompleted(),
        )
        assertEquals(0, fixture.settings.current.fddbProductSyncManualTriggerCount)
        assertEquals(listOf(Url(1), Url(2)), fixture.gateway.requestedUrls)
    }

    @Test
    fun explicitBatchBypassesDisabledModeAndReportsProgress() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                mode = FddbProductSyncMode.Disabled,
            )
        val progress = mutableListOf<FddbProductSyncBatchProgress>()

        val result = fixture.coordinator.syncNext(limit = 3, onProgress = progress::add)

        assertEquals(
            ManualFddbProductSyncResult.Completed(
                SyncDueFddbProductsResult(synced = 3, failed = 0, blocked = false)
            ),
            result,
        )
        assertEquals(listOf(0, 1, 2, 3), progress.map { it.processed })
        assertEquals(listOf(Url(1), Url(2), Url(3)), fixture.gateway.requestedUrls)
        assertEquals(0, fixture.settings.current.fddbProductSyncManualTriggerCount)
    }

    @Test
    fun manualBatchLauncherKeepsAndPublishesCompletedState() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                mode = FddbProductSyncMode.Disabled,
            )
        val launcher =
            FddbProductSyncManualBatchLauncher(
                applicationScope = this,
                coordinator = fixture.coordinator,
                logger = NoopLogger,
            )

        launcher.start(3)
        advanceUntilIdle()

        assertEquals(
            FddbProductSyncManualBatchState.Completed(
                FddbProductSyncBatchProgress(
                    total = 3,
                    processed = 3,
                    synced = 3,
                    failed = 0,
                    blocked = false,
                )
            ),
            launcher.state.value,
        )
    }

    @Test
    fun manualSyncProcessesOnlyTheFirstTwoProducts() = runTest {
        val fixture = coordinatorFixture(now = Instant.fromEpochSeconds(10_000))

        val result = fixture.coordinator.onManualFddbSyncCompleted()

        assertEquals(
            ManualFddbProductSyncResult.Completed(
                SyncDueFddbProductsResult(synced = 2, failed = 0, blocked = false)
            ),
            result,
        )
        assertEquals(listOf(Url(1), Url(2)), fixture.gateway.requestedUrls)
        assertEquals(10_000, fixture.settings.current.fddbProductSyncLastAttemptEpochSeconds)
    }

    @Test
    fun emptyQueueDoesNotRecordAttempt() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                dueProducts = emptyList(),
            )

        assertEquals(
            ManualFddbProductSyncResult.NoProducts,
            fixture.coordinator.onManualFddbSyncCompleted(),
        )
        assertEquals(null, fixture.settings.current.fddbProductSyncLastAttemptEpochSeconds)
    }

    @Test
    fun explicitSyncRecordsAttempt() = runTest {
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                lastAttempt = 9_999,
            )

        fixture.coordinator.syncNow(FoodId.Product(3))

        assertEquals(listOf(Url(3)), fixture.gateway.requestedUrls)
        assertEquals(10_000, fixture.settings.current.fddbProductSyncLastAttemptEpochSeconds)
    }

    @Test
    fun interruptedRequestHasRecordedAttempt() = runTest {
        val started = CompletableDeferred<Unit>()
        val fixture =
            coordinatorFixture(
                now = Instant.fromEpochSeconds(10_000),
                beforeResponse = {
                    started.complete(Unit)
                    awaitCancellation()
                },
            )

        val job = launch { fixture.coordinator.syncNow(FoodId.Product(1)) }
        started.await()

        assertEquals(10_000, fixture.settings.current.fddbProductSyncLastAttemptEpochSeconds)
        assertEquals(listOf(FoodId.Product(1)), fixture.statusRepository.attempts)
        job.cancelAndJoin()
    }

    @Test
    fun syncsAtMostTwoDueProducts() = runBlocking {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2, 3))
        val gateway = FakeFddbProductGateway()
        val useCase = useCase(statusRepository = statusRepository, gateway = gateway)

        val result = useCase.sync(limit = 2)

        assertEquals(SyncDueFddbProductsResult(synced = 2, failed = 0, blocked = false), result)
        assertEquals(listOf(Url(1), Url(2)), gateway.requestedUrls)
        assertEquals(listOf(FoodId.Product(1), FoodId.Product(2)), statusRepository.successes)
        assertEquals(
            mapOf(
                FoodId.Product(1) to FixedDateProvider.nowInstant(),
                FoodId.Product(2) to FixedDateProvider.nowInstant(),
            ),
            statusRepository.successTimes,
        )
    }

    @Test
    fun continuesWithSecondProductAfterRegularFailure() = runBlocking {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2))
        val gateway = FakeFddbProductGateway(failingUrls = setOf(Url(1)))
        val useCase = useCase(statusRepository = statusRepository, gateway = gateway)

        val result = useCase.sync(limit = 2)

        assertEquals(SyncDueFddbProductsResult(synced = 1, failed = 1, blocked = false), result)
        assertEquals(listOf(Url(1), Url(2)), gateway.requestedUrls)
        assertEquals(listOf(FoodId.Product(2)), statusRepository.successes)
        assertEquals(
            mapOf(FoodId.Product(1) to "Network request failed: Failed"),
            statusRepository.failures,
        )
        assertEquals(
            mapOf(FoodId.Product(1) to FixedDateProvider.nowInstant()),
            statusRepository.failureTimes,
        )
    }

    @Test
    fun stopsCurrentRunAfterBlockedAccess() = runBlocking {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2))
        val gateway = FakeFddbProductGateway(blockedUrls = setOf(Url(1)))
        val useCase = useCase(statusRepository = statusRepository, gateway = gateway)

        val result = useCase.sync(limit = 2)

        assertEquals(SyncDueFddbProductsResult(synced = 0, failed = 1, blocked = true), result)
        assertEquals(listOf(Url(1)), gateway.requestedUrls)
        assertEquals(mapOf(FoodId.Product(1) to "FDDB access blocked"), statusRepository.failures)
    }

    @Test
    fun storesDistinctHttpParseAndTransportDiagnostics() = runBlocking {
        val statusRepository =
            FakeFddbProductSyncStatusRepository(dueProducts = dueItems(1, 2, 3, 4))
        val gateway =
            FakeFddbProductGateway(
                errorsByUrl =
                    mapOf(
                        Url(1) to FddbHttpException(404),
                        Url(2) to FddbHttpException(502),
                        Url(3) to FddbParseException("Product name missing"),
                        Url(4) to IllegalStateException("Connection reset"),
                    )
            )

        val result = useCase(statusRepository = statusRepository, gateway = gateway).sync(limit = 4)

        assertEquals(SyncDueFddbProductsResult(synced = 0, failed = 4, blocked = false), result)
        assertEquals(
            mapOf(
                FoodId.Product(1) to "FDDB page not found (HTTP 404)",
                FoodId.Product(2) to "FDDB request failed (HTTP 502)",
                FoodId.Product(3) to "FDDB page could not be parsed: Product name missing",
                FoodId.Product(4) to "Network request failed: Connection reset",
            ),
            statusRepository.failures,
        )
    }

    private fun useCase(
        statusRepository: FakeFddbProductSyncStatusRepository,
        gateway: FddbProductGateway,
    ) =
        FakeSettingsRepository().let { settingsRepository ->
            SyncDueFddbProductsUseCase(
                statusRepository = statusRepository,
                syncFddbProductUseCase =
                    SyncFddbProductUseCase(
                        statusRepository = statusRepository,
                        resyncFddbProductUseCase =
                            ResyncFddbProductUseCase(
                                productRepository = FakeProductRepository(products(1, 2, 3, 4)),
                                fddbProductGateway = gateway,
                                transactionProvider = ImmediateTransactionProvider,
                                logger = NoopLogger,
                            ),
                        dateProvider = FixedDateProvider,
                        settingsRepository = settingsRepository,
                    ),
            )
        }

    private fun coordinatorFixture(
        now: Instant,
        lastAttempt: Long? = null,
        mode: FddbProductSyncMode = FddbProductSyncMode.WithManualFddbSync,
        frequency: FddbProductSyncManualFrequency =
            FddbProductSyncManualFrequency.EverySync,
        manualTriggerCount: Int = 0,
        dueProducts: List<FddbProductSyncQueueItem> = dueItems(1, 2, 3),
        beforeResponse: suspend (String) -> Unit = {},
    ): CoordinatorFixture {
        val statusRepository = FakeFddbProductSyncStatusRepository(dueProducts)
        val gateway = FakeFddbProductGateway(beforeResponse = beforeResponse)
        val date = MutableDateProvider(now)
        val settings =
            FakeSettingsRepository(
                defaultSettings().copy(
                    fddbProductSyncLastAttemptEpochSeconds = lastAttempt,
                    fddbProductSyncMode = mode,
                    fddbProductSyncManualFrequency = frequency,
                    fddbProductSyncManualTriggerCount = manualTriggerCount,
                )
            )
        val sync =
            SyncFddbProductUseCase(
                statusRepository = statusRepository,
                resyncFddbProductUseCase =
                    ResyncFddbProductUseCase(
                        productRepository = FakeProductRepository(products(1, 2, 3, 4)),
                        fddbProductGateway = gateway,
                        transactionProvider = ImmediateTransactionProvider,
                        logger = NoopLogger,
                    ),
                dateProvider = date,
                settingsRepository = settings,
            )
        return CoordinatorFixture(
            coordinator =
                FddbProductSyncCoordinator(
                    settingsRepository = settings,
                    statusRepository = statusRepository,
                    syncDueFddbProductsUseCase =
                        SyncDueFddbProductsUseCase(statusRepository, sync),
                    syncFddbProductUseCase = sync,
                ),
            settings = settings,
            statusRepository = statusRepository,
            gateway = gateway,
            date = date,
        )
    }

    private data class CoordinatorFixture(
        val coordinator: FddbProductSyncCoordinator,
        val settings: FakeSettingsRepository,
        val statusRepository: FakeFddbProductSyncStatusRepository,
        val gateway: FakeFddbProductGateway,
        val date: MutableDateProvider,
    )

    private class FakeFddbProductSyncStatusRepository(
        private val dueProducts: List<FddbProductSyncQueueItem>
    ) : FddbProductSyncStatusRepository {
        val successes = mutableListOf<FoodId.Product>()
        val successTimes = mutableMapOf<FoodId.Product, Instant>()
        val failures = mutableMapOf<FoodId.Product, String>()
        val failureTimes = mutableMapOf<FoodId.Product, Instant>()
        val attempts = mutableListOf<FoodId.Product>()

        override fun observeQueue(): Flow<List<FddbProductSyncQueueItem>> = flowOf(dueProducts)

        override suspend fun getDueProducts(limit: Int): List<FddbProductSyncQueueItem> =
            dueProducts.take(limit)

        override suspend fun markAttempt(productId: FoodId.Product, attemptedAt: Instant) {
            attempts += productId
        }

        override suspend fun markSuccess(productId: FoodId.Product, syncedAt: Instant) {
            successes += productId
            successTimes[productId] = syncedAt
        }

        override suspend fun markFailure(
            productId: FoodId.Product,
            attemptedAt: Instant,
            error: String,
        ) {
            failures[productId] = error
            failureTimes[productId] = attemptedAt
        }

        override suspend fun clear(productId: FoodId.Product) = Unit
    }

    private class FakeSettingsRepository(initialSettings: Settings = defaultSettings()) :
        UserPreferencesRepository<Settings> {
        private val settings = MutableStateFlow(initialSettings)
        val current: Settings
            get() = settings.value

        override fun observe(): Flow<Settings> = settings

        override suspend fun update(transform: Settings.() -> Settings) {
            settings.value = transform(settings.value)
        }
    }

    private class FakeFddbProductGateway(
        private val failingUrls: Set<String> = emptySet(),
        private val blockedUrls: Set<String> = emptySet(),
        private val errorsByUrl: Map<String, Throwable> = emptyMap(),
        private val beforeResponse: suspend (String) -> Unit = {},
    ) : FddbProductGateway {
        val requestedUrls = mutableListOf<String>()

        override suspend fun getProduct(
            url: String,
            priority: FddbRequestPriority,
        ): FddbProduct {
            requestedUrls += url
            beforeResponse(url)
            if (url in blockedUrls) throw FddbAccessBlockedException("Blocked")
            errorsByUrl[url]?.let { throw it }
            if (url in failingUrls) error("Failed")
            return FddbProduct(
                name = "Remote",
                brand = null,
                barcode = null,
                isLiquid = false,
                packageWeight = null,
                servingWeight = null,
                portions = emptyList(),
                nutritionFacts = nutrition(),
            )
        }
    }

    private class FakeProductRepository(initialProducts: List<Product>) : ProductRepository {
        private val products = MutableStateFlow(initialProducts)

        override fun observeProduct(id: FoodId.Product): Flow<Product?> =
            products.map { products -> products.firstOrNull { it.id == id } }

        override fun observeProductByBarcode(barcode: String): Flow<Product?> = flowOf(null)

        override suspend fun getProductByBarcode(barcode: String): Product? = null

        override suspend fun getProductBySource(type: FoodSource.Type, url: String): Product? = null

        override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
            products.map { it.drop(offset).take(limit) }

        override fun observeProductsBySource(
            type: FoodSource.Type,
            limit: Int,
            offset: Int,
        ): Flow<List<Product>> =
            products.map { products ->
                products.filter { it.source.type == type }.drop(offset).take(limit)
            }

        override fun observeProductCountBySource(type: FoodSource.Type): Flow<Int> =
            products.map { products -> products.count { it.source.type == type } }

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
            products.value = products.value.map { if (it.id == product.id) product else it }
        }

        override suspend fun replaceProductPortions(
            productId: FoodId.Product,
            sourceType: FoodSource.Type,
            portions: List<ProductPortion>,
        ) = Unit

        override suspend fun updateProductPortions(
            productId: FoodId.Product,
            portions: List<com.maksimowiczm.foodyou.food.domain.entity.ProductPortion>,
        ) = Unit

        override suspend fun deleteProduct(product: Product) = Unit

        override suspend fun deleteProductsBySource(type: FoodSource.Type): Int = 0
    }

    private object ImmediateTransactionProvider : TransactionProvider {
        override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
            block(
                object : TransactionScope<T> {
                    override suspend fun rollback(result: T) = Unit
                }
            )
    }

    private object FixedDateProvider : DateProvider {
        private val now = Instant.fromEpochSeconds(1_000)

        override fun nowInstant(): Instant = now

        override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(now)

        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> =
            flowOf(LocalDate(2026, 6, 22))
    }

    private class MutableDateProvider(var now: Instant) : DateProvider {
        override fun nowInstant(): Instant = now

        override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(now)

        override fun observeDate(timeZone: TimeZone): Flow<LocalDate> =
            flowOf(LocalDate(2026, 6, 22))
    }

    private object NoopLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        fun Url(id: Long) = "https://fddb.info/db/de/lebensmittel/product_$id/index.html"

        fun dueItems(vararg ids: Long): List<FddbProductSyncQueueItem> =
            ids.map { id ->
                FddbProductSyncQueueItem(
                    productId = FoodId.Product(id),
                    name = "Product $id",
                    brand = null,
                    sourceUrl = Url(id),
                    lastSyncedAt = null,
                    lastAttemptAt = null,
                    lastError = null,
                )
            }

        fun products(vararg ids: Long): List<Product> =
            ids.map { id ->
                Product(
                    id = FoodId.Product(id),
                    name = "Product $id",
                    brand = null,
                    barcode = null,
                    note = null,
                    isLiquid = false,
                    packageWeight = null,
                    servingWeight = null,
                    portions = emptyList(),
                    source = FoodSource(FoodSource.Type.FDDB, Url(id)),
                    nutritionFacts = nutrition(),
                )
            }

        fun nutrition() =
            NutritionFacts(
                energy = NutrientValue.Complete(100.0),
                proteins = NutrientValue.Complete(1.0),
                carbohydrates = NutrientValue.Complete(2.0),
                fats = NutrientValue.Complete(3.0),
            )

        fun defaultSettings() =
            Settings(
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
