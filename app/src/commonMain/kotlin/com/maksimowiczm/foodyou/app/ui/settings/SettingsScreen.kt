package com.maksimowiczm.foodyou.app.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.component.SettingsListItem
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalAppConfig
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.PendingProductPhotoQuality
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSponsor: () -> Unit,
    onAbout: () -> Unit,
    onMeals: () -> Unit,
    onLanguage: () -> Unit,
    onGoals: () -> Unit,
    onSynchronization: () -> Unit,
    onActivities: () -> Unit,
    onPersonalization: () -> Unit,
    onDatabase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settingsRepository: UserPreferencesRepository<Settings> =
        koinInject(named(Settings::class.qualifiedName!!))
    val settings by settingsRepository.observe().collectAsStateWithLifecycle(null)
    val coroutineScope = rememberCoroutineScope()

    val color = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val shape = RectangleShape

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_settings)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues.add(vertical = 8.dp),
        ) {
            item {
                PersonalizationSettingsListItem(
                    onClick = onPersonalization,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                MealSettingsListItem(
                    onClick = onMeals,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                GoalsSettingsListItem(
                    onClick = onGoals,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                SettingsListItem(
                    icon = { Icon(Icons.Outlined.Sync, null) },
                    label = { Text(stringResource(Res.string.headline_synchronization)) },
                    supportingContent = {
                        Text(stringResource(Res.string.description_synchronization))
                    },
                    onClick = onSynchronization,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                SettingsListItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null) },
                    label = { Text(stringResource(Res.string.headline_activities)) },
                    supportingContent = { Text("Steps, manual burned calories, and Health Connect") },
                    onClick = onActivities,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                DatabaseSettingsListItem(
                    onClick = onDatabase,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                PendingProductPhotoQualitySettingsListItem(
                    value = settings?.pendingProductPhotoQuality,
                    onValueChange = { value ->
                        coroutineScope.launch {
                            settingsRepository.update {
                                copy(pendingProductPhotoQuality = value)
                            }
                        }
                    },
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                LanguageSettingsListItem(
                    onClick = onLanguage,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                val uriHandle = LocalUriHandler.current
                val appConfig = LocalAppConfig.current
                SettingsListItem(
                    icon = { Icon(Icons.Outlined.PrivacyTip, null) },
                    label = { Text(stringResource(Res.string.headline_privacy_policy)) },
                    supportingContent = {
                        Text(stringResource(Res.string.description_privacy_policy))
                    },
                    onClick = { uriHandle.openUri(appConfig.privacyPolicyUri) },
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }

            item {
                AboutSettingsListItem(
                    onClick = onAbout,
                    shape = shape,
                    color = color,
                    contentColor = contentColor,
                )
            }
        }
    }
}

@Composable
private fun PendingProductPhotoQualitySettingsListItem(
    value: PendingProductPhotoQuality?,
    onValueChange: (PendingProductPhotoQuality) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = value ?: PendingProductPhotoQuality.Balanced

    SettingsListItem(
        icon = { Icon(Icons.Outlined.PhotoCamera, null) },
        label = { Text(stringResource(Res.string.headline_pending_product_photo_quality)) },
        supportingContent = { Text(selected.label()) },
        trailingContent = {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PendingProductPhotoQuality.entries.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(quality.label()) },
                        onClick = {
                            expanded = false
                            onValueChange(quality)
                        },
                    )
                }
            }
        },
        onClick = { expanded = true },
        shape = shape,
        color = color,
        contentColor = contentColor,
    )
}

@Composable
private fun PendingProductPhotoQuality.label(): String =
    when (this) {
        PendingProductPhotoQuality.Fast -> stringResource(Res.string.headline_photo_quality_fast)
        PendingProductPhotoQuality.Balanced ->
            stringResource(Res.string.headline_photo_quality_balanced)
        PendingProductPhotoQuality.High -> stringResource(Res.string.headline_photo_quality_high)
    }
