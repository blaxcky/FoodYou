package com.maksimowiczm.foodyou.goals.domain.usecase

import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class BasalMetabolicRateCalculatorTest {
    @Test
    fun calculatesMaleBasalMetabolicRate() {
        val suggestion =
            calculateBasalMetabolicRateSuggestion(
                profile =
                    profile(
                        weightKg = 80.0,
                        heightCm = 180.0,
                        birthDate = LocalDate(1996, 6, 5),
                        sex = BiologicalSex.Male,
                    ),
                today = LocalDate(2026, 6, 5),
            )

        assertEquals(1780, suggestion?.basalMetabolicRateKcal)
        assertEquals(1869, suggestion?.minimalActivityKcal)
    }

    @Test
    fun calculatesFemaleBasalMetabolicRate() {
        val suggestion =
            calculateBasalMetabolicRateSuggestion(
                profile =
                    profile(
                        weightKg = 65.0,
                        heightCm = 165.0,
                        birthDate = LocalDate(1996, 6, 5),
                        sex = BiologicalSex.Female,
                    ),
                today = LocalDate(2026, 6, 5),
            )

        assertEquals(1370, suggestion?.basalMetabolicRateKcal)
        assertEquals(1439, suggestion?.minimalActivityKcal)
    }

    @Test
    fun ageIncludesBirthdayEarlierThisYear() {
        val suggestion =
            calculateBasalMetabolicRateSuggestion(
                profile = profile(birthDate = LocalDate(1990, 6, 4)),
                today = LocalDate(2026, 6, 5),
            )

        assertEquals(1750, suggestion?.basalMetabolicRateKcal)
    }

    @Test
    fun ageIncludesBirthdayToday() {
        val suggestion =
            calculateBasalMetabolicRateSuggestion(
                profile = profile(birthDate = LocalDate(1990, 6, 5)),
                today = LocalDate(2026, 6, 5),
            )

        assertEquals(1750, suggestion?.basalMetabolicRateKcal)
    }

    @Test
    fun ageExcludesBirthdayLaterThisYear() {
        val suggestion =
            calculateBasalMetabolicRateSuggestion(
                profile = profile(birthDate = LocalDate(1990, 6, 6)),
                today = LocalDate(2026, 6, 5),
            )

        assertEquals(1755, suggestion?.basalMetabolicRateKcal)
    }

    @Test
    fun incompleteProfileDoesNotCalculateSuggestion() {
        assertNull(
            calculateBasalMetabolicRateSuggestion(
                profile = BasalMetabolicRateProfile.Empty,
                today = LocalDate(2026, 6, 5),
            )
        )
    }

    @Test
    fun invalidProfileDoesNotCalculateSuggestion() {
        assertNull(
            calculateBasalMetabolicRateSuggestion(
                profile = profile(weightKg = 0.0),
                today = LocalDate(2026, 6, 5),
            )
        )
        assertNull(
            calculateBasalMetabolicRateSuggestion(
                profile = profile(birthDate = LocalDate(2027, 6, 5)),
                today = LocalDate(2026, 6, 5),
            )
        )
    }

    private fun profile(
        weightKg: Double = 80.0,
        heightCm: Double = 180.0,
        birthDate: LocalDate = LocalDate(1996, 6, 5),
        sex: BiologicalSex = BiologicalSex.Male,
    ) =
        BasalMetabolicRateProfile(
            weightKg = weightKg,
            heightCm = heightCm,
            birthDate = birthDate,
            sex = sex,
        )
}
