# KI-Waagenerkennung

Die Schnellerfassung analysiert offene Fotos nach manuellem Start. Ergebnisse sind
unbestätigte Gewichtsvorschläge. Name und Gewicht werden weiterhin vom Nutzer bestätigt.
Es werden weder Lebensmittel noch Nährwerte geschätzt.

## Anbieter und Dateien

- Lokal: `com.google.ai.edge.litertlm:litertlm-android:0.16.1`, GPU und Vision-GPU,
  4096 Kontexttokens, maximal ein Bild und 128 Ausgabetokens pro Sitzung, Thinking aus.
  Eine Engine pro Durchlauf, eine frische Conversation pro Foto.
- Modelle: `litert-community/gemma-4-E4B-it-litert-lm`, Revision
  `2eee7ac325f20eb8c9ac1d0e972f7c84663062da`, Datei `gemma-4-E4B-it.litertlm`.
- Größe: `3659530240` Bytes; SHA-256:
  `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`.
  Außerdem `litert-community/gemma-4-E2B-it-litert-lm`, Revision
  `6e5c4f1e395deb959c494953478fa5cec4b8008f`, Datei `gemma-4-E2B-it.litertlm`,
  `2588147712` Bytes; SHA-256:
  `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
  Metadaten stammen von den öffentlichen Hugging-Face-Modellseiten am 2026-09-28.
- Quellen: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm und
  https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
  (Apache 2.0). Modellgewichte werden nicht mit dem APK ausgeliefert.
- Online: Gemini `generateContent` mit JPEG-Inline-Daten und `x-goog-api-key`.
  Modellname ist editierbar; Vorauswahl `gemini-3.8-flash`. Kein Cloud-Fallback.

Modell, Teildownload, KI-Konfiguration und verschlüsselter API-Key liegen unter
`Context.noBackupFilesDir/ai`. Der vorhandene vollständige Backup-Export kopiert diesen
Ordner nicht. Auf einem neuen Gerät sind Download und API-Key erneut einzurichten.
Der dedizierte Ktor-Client verwendet keine HTTP- oder Body-Logger.

Nur ein verifizierter Download wird atomar zur jeweiligen aktiven Modelldatei umbenannt.
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
  --tests '*ScalePhotoDecoderTest' --tests '*NativeGenerationTest' \
  --tests '*LocalAiProtocolTest' --tests '*LocalAiClientTest' --tests '*AiRunTimingsTest' \
  --tests '*QuickCaptureAiMigrationTest' --tests '*QuickCaptureDaoTest' \
  --tests '*QuickCaptureViewModelTest' \
  --tests '*AiSettingsScreenshotTest' --tests '*QuickCaptureScreenshotTest' \
  -Proborazzi.test.verify=true
./gradlew :app:assembleDevRelease
git diff --check
```

## Noch erforderlicher Gerätetest

Ziel: Nothing Phone (2), 12 GB RAM. Runtime und Modellrevisionen sind festgelegt;
ihre gemeinsame Ausführung auf diesem Gerät ist noch nicht praktisch bestätigt.
Es wurden keine Erkennungsquote und keine Inferenzzeiten erfunden oder aus
Desktop-/Emulatortests abgeleitet. Ein echter Gemini-Test benötigt einen Nutzer-Key.

1. Einstellungen → KI → beide Gemma-Varianten nacheinander herunterladen. Währenddessen
   App verlassen, erneut öffnen und fortsetzen. Vor fertiger Prüfsummenprüfung darf
   keine Analyse mit dem jeweils ausgewählten Modell starten.
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

## Prozessisolierung und Absturzdiagnose

Gemma läuft ausschließlich im nicht exportierten Dienst `LocalAiService` im Prozess
`:local_ai`. `FoodYouApplication` überspringt dort Koin, Room, Widgets und den
UI-Crashhandler. Der Hauptprozess übergibt Fotos mit `ParcelFileDescriptor`; nur der
Worker dekodiert das Bild. Die IPC-Antworten enthalten kleine strukturierte Ergebnisse,
Sitzungs- und Anfragenummern. Verspätete Antworten werden verworfen.

Die native Engine wird pro Durchlauf geöffnet, Conversations pro Foto. Abbruch wartet
auf den terminalen Generierungs-Callback und das Ende von `cancelProcess()` vor dem
Schließen. Ein unabhängiger Service-Watchdog beendet nach fünf Sekunden ausschließlich
den Worker, wenn Initialisierung, Generierung oder Aufräumen hängt. Prozessverlust
stoppt den Durchlauf ohne Neustart oder Anbieterwechsel. Android kann bei systemweitem
Speichermangel trotzdem auch den Hauptprozess beenden.

Einstellungen → KI → KI-Diagnosebericht → Kopieren enthält Runtime/Modellrevision,
Gerät, Phasen, Bildabmessungen sowie verfügbaren RAM und Prozessspeicher. Die begrenzten
Phasenprotokolle liegen im privaten `noBackupFilesDir/ai/diagnostics`. Fotos, rohe
Antworten erfolgreicher Analysen, Exception-Texte und Schlüssel werden nicht protokolliert.
Die letzte wegen Format, Ganzzahligkeit oder Kürzung verworfene Modellantwort wird
JSON-kodiert gespeichert, im Bericht mit einem Datenschutzhinweis angezeigt und beim
nächsten solchen Fehler ersetzt. Sie kann von Gemma gelesenen Bildtext enthalten.
Der Dialog wertet den letzten Durchlauf zusätzlich lokal als kurze, verständliche
Zusammenfassung aus; der vollständige technische Bericht bleibt aufklapp- und kopierbar.
Ab Android 11 werden `ApplicationExitInfo`-Datensätze ergänzt. LOW_MEMORY und native
Abstürze werden unterschieden; SIGKILL allein oder fehlende Informationen ergeben
keine RAM-Diagnose. Frühere Datensätze sind über Zeitstempel und PID zuzuordnen.

Antwortformatfehler und abgeschnittene Antworten sind technische Fehler; nur ein
explizites `{"value":null}` bedeutet unlesbar. Zusatzanzeigen wie Timer sind kein
zweites Gewicht. Runtime 0.16.1 und Kontext 4096 bleiben zur Diagnose unverändert.

### Reproduzierbarer Gerätetest: 269 g

Das vom Nutzer bereitgestellte Bild liegt ausschließlich in Test-Assets unter
`app/src/androidInstrumentedTest/assets/ai/scale-269g.png`, nicht im App-APK. Der
Opt-in-Test `com.maksimowiczm.foodyou.ai.LocalScaleDeviceTest` analysiert es in zwei
Einzelsitzungen und einem Dreierstapel mit der echten lokalen Engine. Er erwartet
269 g; dieser Sollwert kommt weder im Prompt noch in der Erkennungslogik vor.
Der Test benötigt das fertig heruntergeladene Modell und das Instrumentierungsargument
`runLocalAiRegression=true`. Ohne dieses Argument wird er übersprungen. Nur auf einem
bewusst ausgewählten physischen Gerät starten. Danach auch den UI-Ablauf prüfen:
Vorschlag 269 g → manuell korrigieren/bestätigen, Abbrechen, Hintergrundwechsel und
Fortsetzen eines Stapels. Ladezeit und Bildzeiten lassen sich aus den Phasenzeitstempeln
ablesen. Nach Fehlern den vollständigen Bericht kopieren.

Dieser Gerätetest steht noch aus. Weder der ursprüngliche Absturzgrund noch die
Erkennungsqualität auf dem Nothing Phone (2) sind durch JVM-Tests bewiesen.

Für den gezielten echten Test (JDK/Gradle-Variablen wie oben), nach expliziter Auswahl
der USB-Geräteseriennummer und mit installiertem Modell:

```bash
ANDROID_SERIAL=<Nothing-Phone-Seriennummer> ./gradlew -I dev/ai-device-test.init.gradle \
  :app:connectedDevReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.maksimowiczm.foodyou.ai.LocalScaleDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.runLocalAiRegression=true
```

Das optionale Init-Skript wählt nur für diesen Aufruf `devRelease` als Testvariante.
Es startet keinen Emulator. Ein Testfehler darf nicht durch Änderung des Sollwerts
oder automatisches Umschalten auf Google umgangen werden.


## Ganze Gramm und Laufzeiten

Beide Anbieter verwenden den kürzeren Prompt und das kompakte Antwortformat
`{"value":161,"unit":"g"}` bzw. `{"value":null}` für unlesbare Anzeigen. Es werden
nur positive ganze Gramm vorgeschlagen. `16.1 g` wird als `NonWholeGrams` verworfen,
weder gerundet noch in `161 g` umgeschrieben. kg-Anzeigen bleiben erlaubt, wenn ihre
exakte Umrechnung ganze Gramm ergibt (z. B. `1.001 kg` → `1001 g`). Die Verschiebung
des Dezimalpunkts erfolgt vor der Double-Konvertierung, um Rundungsfehler zu vermeiden.
Der persistierte Status `error_whole_grams` benötigt keine Room-Migration und bewahrt
bei erneuter Analyse vorhandene Vorschläge. Kein automatischer Wiederholungsversuch.

Die lokale Messung verwendet `SystemClock.elapsedRealtime()`. Ein Modell-Ladevorgang
und fortlaufende Fotonummern gehören jeweils zu einem Durchlauf. Der Diagnosebericht
zeigt drei Nachkommastellen in Sekunden:

- Modellladen: eigener Zeitraum, nicht in der Fotozeit enthalten.
- Foto-Gesamtzeit: Dekodieren, Sitzung erstellen, Generierung und Sitzung schließen.
- Erste Antwort: vom Generierungsstart bis zum ersten nicht leeren Antwortteil.

`timings.log` enthält höchstens 80 Datensätze im bestehenden privaten No-Backup-Ordner,
ohne Dateinamen, Bildinhalte, Antworten oder Schlüssel. Abbrüche und Fehler bekommen
einen eigenen Status; nach einem Prozessverlust fehlende Abschlusszeiten gelten
nicht als abgeschlossene Messung. Die Diagnose verwendet nur Metadaten, und die
Antwort-Callbacks schreiben selbst keine Dateien.

Für den Vorher/Nachher-Vergleich denselben Stapel auf dem Nothing Phone verwenden:
Modellstart getrennt erfassen und Zeiten der Folgefotos vergleichen. Die vorherige
Version enthält Phasenzeitstempel; alternativ den Ausgangslauf mit einer Stoppuhr
messen. Bedingungen (Gerätetemperatur, Hintergrund-Apps) möglichst konstant halten.
Thinking bleibt aus, Runtime 0.16.1, Kontext 4096, GPU und Bildauflösung unverändert.
Ein kürzerer Prompt und weniger Antworttext sind keine Garantie für eine bestimmte
Beschleunigung. Der reale Vergleich steht aus, weil kein Nothing Phone verbunden ist.
Das vorhandene 269-g-Testbild bleibt unverändert; für den zusätzlichen Fall 161 g
wird das betreffende Originalfoto noch benötigt. Parser-Tests ersetzen diese
Bilderkennungstests nicht.
