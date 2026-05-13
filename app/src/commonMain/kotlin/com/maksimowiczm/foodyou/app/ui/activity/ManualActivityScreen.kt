package com.maksimowiczm.foodyou.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ManualActivityScreen(
    date: LocalDate,
    id: Long?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ManualActivityViewModel = koinViewModel()
    LaunchedEffect(id) {
        if (id != null) viewModel.load(id)
    }
    val name = viewModel.name.collectAsStateWithLifecycle().value
    val energyKcal = viewModel.energyKcal.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (id == null) "Add activity" else "Edit activity") },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
            )
            OutlinedTextField(
                value = energyKcal,
                onValueChange = viewModel::setEnergyKcal,
                label = { Text("Burned kcal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Button(onClick = { viewModel.save(date, id, onSave) }) { Text("Save") }
        }
    }
}
