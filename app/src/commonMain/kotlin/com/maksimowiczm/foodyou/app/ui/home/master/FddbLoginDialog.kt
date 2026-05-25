package com.maksimowiczm.foodyou.app.ui.home.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.food.domain.repository.FddbCredentialsRepository
import com.maksimowiczm.foodyou.food.domain.repository.FddbDiaryGateway
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun FddbLoginDialog(onDismissRequest: () -> Unit) {
    val credentialsRepository: FddbCredentialsRepository = koinInject()
    val diaryGateway: FddbDiaryGateway = koinInject()
    val scope = rememberCoroutineScope()
    val login = rememberTextFieldState()
    val password = rememberTextFieldState()
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var hasError by rememberSaveable { mutableStateOf(false) }
    val isValid by remember(login, password) {
        derivedStateOf { login.text.isNotBlank() && password.text.isNotBlank() }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        confirmButton = {
            TextButton(
                enabled = isValid && !isLoading,
                onClick = {
                    scope.launch {
                        isLoading = true
                        hasError = false
                        val username = login.text.toString()
                        val passwordText = password.text.toString()
                        runCatching { diaryGateway.login(username, passwordText) }
                            .onSuccess {
                                credentialsRepository.store(username, passwordText)
                                onDismissRequest()
                            }
                            .onFailure { hasError = true }
                        isLoading = false
                    }
                },
            ) {
                Text(stringResource(Res.string.action_sign_in))
            }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        title = { Text(stringResource(Res.string.headline_fddb)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (hasError) {
                    Text(stringResource(Res.string.error_fddb_failed_to_authenticate))
                }
                OutlinedTextField(
                    state = login,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.headline_username)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    isError = hasError,
                )
                SecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.headline_password)) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    textObfuscationMode =
                        if (isPasswordVisible) {
                            TextObfuscationMode.Visible
                        } else {
                            TextObfuscationMode.RevealLastTyped
                        },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector =
                                    if (isPasswordVisible) Icons.Outlined.VisibilityOff
                                    else Icons.Outlined.Visibility,
                                contentDescription =
                                    stringResource(
                                        if (isPasswordVisible) {
                                            Res.string.action_hide_password
                                        } else {
                                            Res.string.action_show_password
                                        }
                                    ),
                            )
                        }
                    },
                    isError = hasError,
                )
            }
        },
    )
}
