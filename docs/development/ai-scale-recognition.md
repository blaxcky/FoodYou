# KI-Waagenerkennung

Die Schnellerfassung analysiert offene Fotos nach manuellem Start. Ergebnisse sind
unbestätigte Gewichtsvorschläge. Name und Gewicht werden weiterhin vom Nutzer bestätigt.
Es werden weder Lebensmittel noch Nährwerte geschätzt.

## Anbieter und Dateien

- Lokal: `com.google.ai.edge.litertlm:litertlm-android:0.16.1`, GPU und Vision-GPU,
  4096 Kontexttokens, maximal ein Bild und 128 Ausgabetokens pro Sitzung, Thinking aus.
  Eine Engine pro Durchlauf, eine frische Conversation pro Foto.
- Modell: `litert-community/gemma-4-E4B-it-litert-lm`, Revision
  `2eee7ac325f20eb8c9ac1d0e972f7c84663062da`, Datei `gemma-4-E4B-it.litertlm`.
- Größe: `3659530240` Bytes; SHA-256:
  `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`.
  Metadaten stammen vom öffentlichen Hugging-Face-Modell-API am 2026-09-27.
- Quelle: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
  (Apache 2.0). Modellgewichte werden nicht mit dem APK ausgeliefert.
- Online: Gemini `generateContent` mit JPEG-Inline-Daten und `x-goog-api-key`.
  Modellname ist editierbar; Vorauswahl `gemini-3.8-flash`. Kein Cloud-Fallback.

Modell, Teildownload, KI-Konfiguration und verschlüsselter API-Key liegen unter
`Context.noBackupFilesDir/ai`. Der vorhandene vollständige Backup-Export kopiert diesen
Ordner nicht. Auf einem neuen Gerät sind Download und API-Key erneut einzurichten.
Der dedizierte Ktor-Client verwendet keine HTTP- oder Body-Logger.

Nur der verifizierte Download wird atomar zur aktiven Modelldatei umbenannt.
Range-Antworten müssen zum gespeicherten Offset passen; ignoriert ein Server Range,
beginnt die Datei neu. Pausieren und App-Hintergrund brechen den Coroutine-Job ab;
ein laufender Socket-Read kann bis zum 15-Sekunden-Timeout benötigen.

Migration 50 → 51 ergänzt ausschließlich nullable Vorschlags-/Analysefelder.
DAO-Updates prüfen atomar Foto-ID, Pfad und offenen Zustand. Technische Fehler bei
Wiederholungen bewahren einen vorhandenen Vorschlag. Manuelle Formulareingaben haben
Vorrang vor nachträglich eintreffenden Ergebnissen.

## Automatisierte Prüfung

Mit `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` und
`GRADLE_USER_HOME=/home/markus/GitHub/FoodYou/.gradle`:

```bash
./gradlew :app:testDevReleaseUnitTest \
  --tests '*ScaleRecognitionTest' --tests '*AiInfrastructureTest' \
  --tests '*ScalePhotoDecoderTest' \
  --tests '*QuickCaptureAiMigrationTest' --tests '*QuickCaptureDaoTest' \
  --tests '*QuickCaptureViewModelTest' \
  --tests '*AiSettingsScreenshotTest' --tests '*QuickCaptureScreenshotTest' \
  -Proborazzi.test.verify=true
./gradlew :app:assembleDevRelease
git diff --check
```

## Noch erforderlicher Gerätetest

Ziel: Nothing Phone (2), 12 GB RAM. Die Runtime und Modellrevision sind festgelegt;
ihre gemeinsame Ausführung auf diesem Gerät ist noch nicht praktisch bestätigt.
Es wurden keine Erkennungsquote und keine Inferenzzeiten erfunden oder aus
Desktop-/Emulatortests abgeleitet. Ein echter Gemini-Test benötigt einen Nutzer-Key.

1. Einstellungen → KI → Gemma herunterladen. Währenddessen App verlassen, erneut
   öffnen und fortsetzen. Vor fertiger Prüfsummenprüfung darf keine Analyse starten.
2. Mindestens zehn Fotos verwenden: klare g-Anzeige, Dezimalwert, kg-Anzeige,
   schräges/gedrehtes Foto, unscharfe Anzeige, keine Waage, mehrere Waagenanzeigen.
3. Flugmodus einschalten, Sammelanalyse starten. Ersten Modellstart und Zeit pro Foto
   messen; Erkennungsquote gegen die manuell abgelesenen Werte dokumentieren.
4. Während der Analyse ein Foto bestätigen/löschen und das Display ausschalten.
   Fertige Vorschläge müssen erhalten bleiben, der Durchlauf muss stoppen.
5. App neu starten. Vorschläge öffnen, Namen bestätigen, Gewicht korrigieren und
   bestätigen. Erst danach darf der Eintrag im Log bereit sein.
6. Mit eigenem Google-Key Anbieter wechseln, speichern, Verbindung testen und
   denselben Satz erneut analysieren. Kein Foto darf ohne ausdrücklichen Analysestart
   übertragen werden; der Verbindungstest enthält kein Foto.

Bei lokalem Ladefehler werden Modell und freier Arbeitsspeicher als Prüfpunkte genannt.
Ein Fehler aktiviert weder ein kleineres Modell noch den Online-Anbieter automatisch.
