package com.maksimowiczm.foodyou.common.domain.measurement

enum class MeasurementType {
    Gram,
    Package,
    Serving,
    Milliliter,
    Ounce,
    FluidOunce,
}

internal val MeasurementType.isUserSelectable: Boolean
    get() =
        when (this) {
            MeasurementType.Gram,
            MeasurementType.Package,
            MeasurementType.Serving,
            MeasurementType.Milliliter -> true
            MeasurementType.Ounce,
            MeasurementType.FluidOunce -> false
        }
