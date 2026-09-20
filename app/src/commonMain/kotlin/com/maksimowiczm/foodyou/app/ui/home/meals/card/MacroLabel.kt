package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacro
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealCardMacroStyle
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val MealCardMacro.icon: ImageVector
    get() =
        when (this) {
            MealCardMacro.Fats -> Icons.Outlined.WaterDrop
            MealCardMacro.Carbohydrates -> Icons.Outlined.Grain
            MealCardMacro.Proteins -> Icons.Outlined.Egg
        }

internal val MealCardMacro.labelResource: StringResource
    get() =
        when (this) {
            MealCardMacro.Fats -> Res.string.nutriment_fats
            MealCardMacro.Carbohydrates -> Res.string.nutriment_carbohydrates
            MealCardMacro.Proteins -> Res.string.nutriment_proteins
        }

internal val MealCardMacro.shortLabelResource: StringResource
    get() =
        when (this) {
            MealCardMacro.Fats -> Res.string.nutriment_fats_short
            MealCardMacro.Carbohydrates -> Res.string.nutriment_carbohydrates_short
            MealCardMacro.Proteins -> Res.string.nutriment_proteins_short
        }

/**
 * Renders a macro value either as `value X` (letter abbreviation) or as `[icon] value`, depending
 * on [style]. The icon is sized to the line height of [textStyle] so it aligns with the text.
 */
@Composable
internal fun MacroValueLabel(
    macro: MealCardMacro,
    value: String,
    style: MealCardMacroStyle,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    when (style) {
        MealCardMacroStyle.Letters ->
            Text(
                text = "$value ${stringResource(macro.shortLabelResource)}",
                color = color,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = modifier,
            )

        MealCardMacroStyle.Icons -> {
            val iconSize = with(LocalDensity.current) { textStyle.fontSize.toDp() + 2.dp }
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = macro.icon,
                    contentDescription = stringResource(macro.labelResource),
                    tint = color,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = value,
                    color = color,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
