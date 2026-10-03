package com.maksimowiczm.foodyou.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiDiagnosticSummaryTest {
    @Test fun explainsResponseFormatFailureWithoutClaimingMemoryPressure() {
        val report = """
            100 pid=7 Durchlauf=42; Modellladen=10.953 s; Status=Completed
            110 pid=7 Durchlauf=42; Foto 1: Gesamt=8.123 s; Erste Antwort=2.753 s; Status=Completed
            120 pid=7 Durchlauf=42; Foto 2: Gesamt=2.755 s; Erste Antwort=1.846 s; Status=Completed
            130 pid=7 Durchlauf=42; Foto 3: Gesamt=2.414 s; Erste Antwort=1.864 s; Status=Error
            129 pid=7 phase=result_ResponseFormat availableMiB=3046 lowMemory=false pssKiB=937301 nativeHeapKiB=3474414
            Antwort (JSON-kodiert): "The weight is 269 g"
        """.trimIndent()

        val summary = summarizeAiDiagnosticReport(report)

        assertEquals("1 von 3 Fotos fehlgeschlagen", summary.headline)
        assertTrue(summary.details.any { "2 von 3 Fotos erkannt" in it })
        assertTrue(summary.details.any { "nicht im erwarteten Datenformat" in it })
        assertTrue(summary.details.any { "The weight is 269 g" in it })
        assertTrue(summary.details.any { "keinen Speichermangel" in it })
    }

    @Test fun showsLastAnswerAndImageSizeGemmaReceived() {
        val report = """
            100 pid=7 Durchlauf=42; Modellladen=10.953 s; Status=Completed
            120 pid=7 Durchlauf=42; Foto 1: Gesamt=8.123 s; Erste Antwort=2.753 s; Status=Unreadable
            115 result=unreadable model=E2B image=960x1280 vision=gpu raw="{\"value\":null}"
            1759525000.100  4242  4300 I litert  : Resize image from 960x1280 to 672x912 which will result in 2394 patches
        """.trimIndent()

        val summary = summarizeAiDiagnosticReport(report)

        assertEquals("1 von 1 Fotos nicht lesbar", summary.headline)
        assertTrue(summary.details.any {
            "Letzte Modellantwort: \"{\\\"value\\\":null}\" (Gemma E2B, Foto 960x1280, Bildanalyse GPU)" in it
        })
        assertTrue(summary.details.any { "672x912 Pixeln (vorbereitet: 960x1280)" in it })
    }

    @Test fun onlySummarizesLatestRun() {
        val report = """
            100 pid=7 Durchlauf=1; Modellladen=5.000 s; Status=Completed
            110 pid=7 Durchlauf=1; Foto 1: Gesamt=2.000 s; Erste Antwort=1.000 s; Status=Error
            200 pid=8 Durchlauf=2; Modellladen=4.000 s; Status=Completed
            210 pid=8 Durchlauf=2; Foto 1: Gesamt=1.500 s; Erste Antwort=0.700 s; Status=Completed
        """.trimIndent()

        val summary = summarizeAiDiagnosticReport(report)

        assertEquals("Letzter Durchlauf ohne Fehler", summary.headline)
        assertTrue(summary.details.single().contains("1 von 1 Fotos erkannt"))
    }
}
