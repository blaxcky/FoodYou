package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FoodSearchBarInputField(
    textFieldState: TextFieldState,
    onSearch: (String?) -> Unit,
    onBarcodeScanner: () -> Unit,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TextField(
        state = textFieldState,
        modifier = modifier,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { onSearch(textFieldState.text.toString()) },
        shape = SearchBarDefaults.inputFieldShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        placeholder = { Text(stringResource(Res.string.action_search)) },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                IconButton(onBarcodeScanner) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_barcode_scanner),
                        contentDescription = stringResource(Res.string.action_scan_barcode),
                    )
                }
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.setTextAndPlaceCursorAtEnd("")
                        onClear()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = stringResource(Res.string.action_clear),
                        )
                    }
                }
            }
        },
    )
}
