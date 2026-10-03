package com.maksimowiczm.foodyou.app.ui.settings

import com.maksimowiczm.foodyou.sync.SyncLogRun
import com.maksimowiczm.foodyou.sync.SyncLogStatus
import com.maksimowiczm.foodyou.sync.SyncLogStep
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncLogFormattingTest {
    @Test
    fun exportIncludesOrderedStepsParallelOffsetsDurationsAndReasons() {
        val run = SyncLogRun(1, "Startseite", 0, 1200, SyncLogStatus.Failed, listOf(
            SyncLogStep(1, null, "FDDB", 0, 0, 1200, SyncLogStatus.Failed, "Zugriff blockiert"),
            SyncLogStep(2, null, "Gewicht", 100, 100, 350, SyncLogStatus.Success),
            SyncLogStep(3, 1, "Produktnachsync", 1200, 1200, 0, SyncLogStatus.Skipped, "Noch nicht fällig"),
        ))
        val text = formatSyncLog(listOf(run), 1200)
        assertContains(text, "Gesamtdauer 1,2 s")
        assertContains(text, "+100 ms")
        assertContains(text, "Dauer 350 ms")
        assertContains(text, "Teil von Schritt 1")
        assertContains(text, "Zugriff blockiert")
        assertContains(text, "Übersprungen · Noch nicht fällig")
        assertTrue(text.indexOf("1. FDDB") < text.indexOf("2. Gewicht"))
    }

    @Test
    fun interruptedDurationIsUnknownRatherThanZero() {
        assertContains(formatSyncLog(listOf(SyncLogRun(1, "Sync", 0, status = SyncLogStatus.Interrupted)), 5000),
            "Gesamtdauer Dauer unbekannt")
        assertEquals("2 min 3,4 s", formatSyncDuration(123400))
    }
}
