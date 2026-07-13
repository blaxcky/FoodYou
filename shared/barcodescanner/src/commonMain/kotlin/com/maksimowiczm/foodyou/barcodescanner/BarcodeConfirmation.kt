package com.maksimowiczm.foodyou.barcodescanner

internal class BarcodeConfirmation(
    private val requiredConsecutiveObservations: Int = 3,
) {
    private var candidate: String? = null
    private var observationCount = 0
    private var confirmed = false

    init {
        require(requiredConsecutiveObservations > 0)
    }

    fun observe(value: String?): String? {
        if (confirmed) return null

        val usableValue = value?.takeIf(String::isNotBlank) ?: run {
            reset()
            return null
        }

        if (usableValue != candidate) {
            candidate = usableValue
            observationCount = 1
            return confirmIfReady(usableValue)
        }

        observationCount += 1
        return confirmIfReady(usableValue)
    }

    fun reset() {
        candidate = null
        observationCount = 0
        confirmed = false
    }

    private fun confirmIfReady(value: String): String? {
        if (observationCount < requiredConsecutiveObservations) return null

        confirmed = true
        return value
    }
}
