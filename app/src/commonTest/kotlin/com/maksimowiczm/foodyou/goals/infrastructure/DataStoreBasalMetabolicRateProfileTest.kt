package com.maksimowiczm.foodyou.goals.infrastructure

import com.maksimowiczm.foodyou.goals.domain.entity.BasalMetabolicRateProfile
import com.maksimowiczm.foodyou.goals.domain.entity.BiologicalSex
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

class DataStoreBasalMetabolicRateProfileTest {
    @Test
    fun decodesProfileStoredBeforeFixedBasalMetabolicRate() {
        val legacyJson =
            """{"weightKg":82.0,"heightCm":180.0,"birthDateEpochDay":7740,"sex":"Male"}"""

        val profile = Json.decodeFromString<DataStoreBasalMetabolicRateProfile>(legacyJson).toProfile()

        assertEquals(82.0, profile.weightKg)
        assertEquals(180.0, profile.heightCm)
        assertEquals(LocalDate(1991, 3, 12), profile.birthDate)
        assertEquals(BiologicalSex.Male, profile.sex)
        assertNull(profile.basalMetabolicRateKcal)
        assertNull(profile.physicalActivityLevelPercent)
        assertFalse(profile.calculateEnergyGoal)
        assertEquals(105, profile.effectivePhysicalActivityLevelPercent)
        assertNull(profile.calculatedEnergyGoalKcal)
    }

    @Test
    fun roundTripsFixedBasalMetabolicRateAndActivityLevel() {
        val profile =
            BasalMetabolicRateProfile(
                weightKg = 80.4,
                heightCm = 180.0,
                birthDate = LocalDate(1991, 3, 12),
                sex = BiologicalSex.Male,
                basalMetabolicRateKcal = 1775.0,
                physicalActivityLevelPercent = 105,
                calculateEnergyGoal = true,
            )

        val json = Json.encodeToString(DataStoreBasalMetabolicRateProfile(profile))
        val decoded = Json.decodeFromString<DataStoreBasalMetabolicRateProfile>(json).toProfile()

        assertEquals(profile, decoded)
    }

    @Test
    fun calculatesEnergyGoalFromFixedBasalMetabolicRateOnly() {
        val profile =
            BasalMetabolicRateProfile(
                weightKg = 60.0,
                heightCm = 160.0,
                birthDate = LocalDate(1991, 3, 12),
                sex = BiologicalSex.Female,
                basalMetabolicRateKcal = 1775.0,
                physicalActivityLevelPercent = 105,
            )

        assertEquals(1864, profile.calculatedEnergyGoalKcal?.roundToInt())
        assertNull(
            BasalMetabolicRateProfile.calculatedEnergyGoalKcal(
                basalMetabolicRateKcal = 0.0,
                physicalActivityLevelPercent = 105,
            )
        )
    }
}
