package com.maksimowiczm.foodyou.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoAnalysisSummaryTest {
    @Test
    fun describesOnlyWhatNeedsAttention() {
        assertNull(PhotoAnalysisSummary().describe())
        assertEquals("1 Foto bereit", PhotoAnalysisSummary(total = 1).describe())
        assertEquals("3 Fotos bereit", PhotoAnalysisSummary(total = 3).describe())
        assertEquals("1 Foto ohne Vorschlag", PhotoAnalysisSummary(total = 1, withoutSuggestion = 1).describe())
        assertEquals("2 Gewichte erkannt", PhotoAnalysisSummary(total = 2, suggested = 2).describe())
        assertEquals("1 erkannt · 1 ohne Vorschlag", PhotoAnalysisSummary(total = 2, suggested = 1, withoutSuggestion = 1).describe())
        assertEquals("1 erkannt · 2 offen", PhotoAnalysisSummary(total = 3, suggested = 1).describe())
    }

    @Test
    fun formatsShortDurations() {
        assertEquals("unter 1 s", formatAnalysisDuration(400))
        assertEquals("2 s", formatAnalysisDuration(2_400))
        assertEquals("02:05", formatAnalysisDuration(125_999))
    }
}
