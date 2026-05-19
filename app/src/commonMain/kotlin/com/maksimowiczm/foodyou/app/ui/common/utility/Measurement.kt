package com.maksimowiczm.foodyou.app.ui.common.utility

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.type
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class ServingUnit {
    Serving,
    Piece,
}

@Composable
fun Measurement.stringResourceWithWeight(
    totalWeight: Double?,
    servingWeight: Double?,
    isLiquid: Boolean,
    servingUnit: ServingUnit = ServingUnit.Serving,
): String? {
    val weight =
        when (this) {
            is Measurement.ImmutableMeasurement -> null

            is Measurement.Package ->
                if (totalWeight == null) {
                    return null
                } else {
                    totalWeight * this.quantity
                }

            is Measurement.Serving ->
                if (servingWeight == null) {
                    return null
                } else {
                    servingWeight * this.quantity
                }
        }

    val measurementString = this.stringResource(servingUnit)
    val suffix =
        if (isLiquid) {
            stringResource(Res.string.unit_milliliter_short)
        } else {
            stringResource(Res.string.unit_gram_short)
        }

    return remember(measurementString, suffix, weight) {
        buildString {
            append(measurementString)
            if (weight != null) {
                append(" (${weight.formatClipZeros()} $suffix)")
            }
        }
    }
}

@Composable
fun Measurement.stringResource(servingUnit: ServingUnit = ServingUnit.Serving) =
    when (this) {
        is Measurement.Package ->
            stringResource(
                Res.string.x_times_y,
                quantity.formatClipZeros(),
                stringResource(Res.string.product_package),
            )

        is Measurement.Serving ->
            stringResource(
                Res.string.x_times_y,
                quantity.formatClipZeros(),
                servingUnit.stringResource(),
            )

        is Measurement.ImmutableMeasurement ->
            value.formatClipZeros() + " " + this.type.stringResource(servingUnit)
    }

@Composable
fun MeasurementType.stringResource(servingUnit: ServingUnit = ServingUnit.Serving): String =
    when (this) {
        MeasurementType.Gram -> stringResource(Res.string.unit_gram_short)
        MeasurementType.Milliliter -> stringResource(Res.string.unit_milliliter_short)
        MeasurementType.Package -> stringResource(Res.string.product_package)
        MeasurementType.Serving -> servingUnit.stringResource()
        MeasurementType.Ounce -> stringResource(Res.string.unit_ounce_short)
        MeasurementType.FluidOunce -> stringResource(Res.string.unit_fluid_ounce_short)
    }

@Composable
private fun ServingUnit.stringResource(): String =
    when (this) {
        ServingUnit.Serving -> stringResource(Res.string.product_serving)
        ServingUnit.Piece -> stringResource(Res.string.product_piece)
    }

val Measurement.Companion.Saver: Saver<Measurement, ArrayList<Any>>
    get() =
        Saver(
            save = {
                val id =
                    when (it) {
                        is Measurement.Gram -> 0
                        is Measurement.Serving -> 2
                        is Measurement.Package -> 1
                        is Measurement.Milliliter -> 3
                        is Measurement.Ounce -> 4
                        is Measurement.FluidOunce -> 5
                    }

                val value =
                    when (it) {
                        is Measurement.Gram -> it.value
                        is Measurement.Serving -> it.quantity
                        is Measurement.Package -> it.quantity
                        is Measurement.Milliliter -> it.value
                        is Measurement.Ounce -> it.value
                        is Measurement.FluidOunce -> it.value
                    }

                arrayListOf(id, value)
            },
            restore = {
                val id = it[0] as Int
                val value = it[1] as Double

                when (id) {
                    0 -> Measurement.Gram(value)
                    1 -> Measurement.Package(value)
                    2 -> Measurement.Serving(value)
                    3 -> Measurement.Milliliter(value)
                    4 -> Measurement.Ounce(value)
                    5 -> Measurement.FluidOunce(value)
                    else -> error("Invalid measurement id: $id")
                }
            },
        )
