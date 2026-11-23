package com.maksimowiczm.foodyou.app.ui.food.quickcapture

import com.maksimowiczm.foodyou.ai.AiSource
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickCaptureCsvPreviewTest {
    @Test
    fun leadingPromptWeightIsSplitFromTheName() {
        assertEquals(QuickCaptureCsvRowName("Krapfen", 131.0), "131g Krapfen".toQuickCaptureCsvRowName())
        assertEquals(QuickCaptureCsvRowName("Skyr Natur", 12.5), "12,5 g Skyr Natur".toQuickCaptureCsvRowName())
        assertEquals(QuickCaptureCsvRowName("Apfel", null), " Apfel ".toQuickCaptureCsvRowName())
        assertEquals(QuickCaptureCsvRowName("7 Zwerge Müsli", null), "7 Zwerge Müsli".toQuickCaptureCsvRowName())
    }

    @Test
    fun sourceLabelIsTheHostWithoutWww() {
        fun host(url: String) = AiSource(url, url).host
        assertEquals("heissundsuess.at", host("https://www.heissundsuess.at/wp-content/uploads/2026/01/a.pdf"))
        assertEquals("erlenbacher.de", host("https://erlenbacher.de?x=1"))
        assertEquals("example.org", host("http://user@example.org:8080/path"))
        assertEquals("not a url", host("not a url"))
    }
}
