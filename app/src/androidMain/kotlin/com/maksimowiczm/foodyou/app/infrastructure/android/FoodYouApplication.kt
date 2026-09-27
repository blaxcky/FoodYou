package com.maksimowiczm.foodyou.app.infrastructure.android

import android.app.Application
import android.content.Intent
import android.os.Build
import com.maksimowiczm.foodyou.app.BuildConfig
import com.maksimowiczm.foodyou.app.infrastructure.backup.BackupBootstrap
import com.maksimowiczm.foodyou.app.di.initKoin
import com.maksimowiczm.foodyou.app.widget.CalorieWidgetUpdater
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.event.EventBus
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.event.AppLaunchEvent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

class FoodYouApplication : Application() {

    private val coroutineScope by lazy {
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("FoodYouApplication"))
    }

    override fun onCreate() {
        super.onCreate()
        // The native worker must not initialize Room, backup restore, widgets or the UI crash handler.
        if (getProcessName() == packageName + com.maksimowiczm.foodyou.ai.LOCAL_AI_PROCESS_SUFFIX) return

        initKoin(coroutineScope) {
            androidContext(this@FoodYouApplication)
            modules(
                module {
                    single<com.maksimowiczm.foodyou.training.TrainingSync> {
                        com.maksimowiczm.foodyou.training.createTrainingSync(this@FoodYouApplication,
                            get<com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase>().trainingImportDao)
                    }
                    single<com.maksimowiczm.foodyou.ai.AiController> {
                        com.maksimowiczm.foodyou.ai.AndroidAiController(this@FoodYouApplication, get(), get())
                    }
                    factory {
                        CalorieWidgetUpdater(
                            observeDiaryMealsUseCase = get(),
                            goalsRepository = get(),
                            activityRepository = get(),
                            settingsRepository = userPreferencesRepository<Settings>(),
                            dateProvider = get(),
                        )
                    }
                }
            )
        }
        // Must run before launch events or UI consumers observe the restored DataStore.
        runBlocking {
            BackupBootstrap.finalize(
                context = this@FoodYouApplication,
                crypto = GlobalContext.get().get(),
                sessions = GlobalContext.get().get(),
                fddb = GlobalContext.get().get(),
                openFoodFacts = GlobalContext.get().get(),
            )
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: android.app.Activity) { started++ }
            override fun onActivityStopped(activity: android.app.Activity) {
                started--
                if (started == 0 && !activity.isChangingConfigurations) {
                    GlobalContext.get().get<com.maksimowiczm.foodyou.ai.AiController>().onBackground()
                }
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        publishLaunchEvent()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            handleUncaughtException(e)
            defaultHandler?.uncaughtException(t, e)
        }
    }

    private fun publishLaunchEvent() {
        val dateProvider: DateProvider by inject()
        val eventBus: EventBus by inject()

        val event = AppLaunchEvent(timestamp = dateProvider.nowInstant())
        eventBus.publish(event)
    }

    private fun handleUncaughtException(e: Throwable) {
        val intent = Intent(this, CrashReportActivity::class.java)

        val report = buildString {
            appendLine("Version: ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine(e.stackTraceToString())
        }

        intent.putExtra("report", report)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        startActivity(intent)
    }
}
