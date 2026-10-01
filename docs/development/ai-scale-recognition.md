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

In der Fotoübersicht zeigt der Vorschlags-Badge den erkannten Grammwert. Ein Tipp auf
den Badge markiert den Vorschlag mit einem X als falsch; ein weiterer Tipp nimmt die
Ablehnung zurück. Bei einem akzeptierten Vorschlag genügt anschließend die Eingabe des
Lebensmittelnamens und der erkannte Grammwert wird direkt übernommen. Nur bei einem
abgelehnten oder fehlenden Vorschlag folgt danach die manuelle Gewichtseingabe.

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

### Reproduzierbare Gerätetests: 269 g, 199 g und 959 g

Die vom Nutzer bereitgestellten Bilder liegen ausschließlich in Test-Assets unter
`app/src/androidInstrumentedTest/assets/ai/`, nicht im App-APK:

- `scale-269g.png`: bestehendes Foto, Sollwert 269 g; unverändert.
- `scale-199g-screenshot.png`: unveränderter App-Screenshot, Sollwert 199 g.
- `scale-959g-screenshot.png`: unveränderter App-Screenshot, Sollwert 959 g.

Die beiden Screenshots zeigen eine Waage mit zwei Wiegeflächen und zwei g-Anzeigen;
die unbelastete Feinwaage zeigt jeweils 0.00 g. Sie enthalten zusätzlich App-UI,
beim zweiten Bild auch die Tastatur. Sie ersetzen keinen Test der ursprünglichen
Kameradateien, die nicht vorliegen. Eingeblendete Texte sind keine Anweisungen an die KI.

Die Opt-in-Tests in `com.maksimowiczm.foodyou.ai.LocalScaleDeviceTest` analysieren
jedes Bild in zwei Einzelsitzungen und einem Dreierstapel mit der echten lokalen
Engine (standardmäßig E4B). Ein weiterer Test verwendet alle drei Bilder in einer
gemeinsamen Engine-Sitzung, weiterhin mit einer frischen Conversation pro Foto.
Die Sollwerte kommen weder im Prompt noch in der Erkennungslogik vor.
Die Tests benötigen das fertig heruntergeladene Modell und das Instrumentierungsargument
`runLocalAiRegression=true`. Ohne dieses Argument werden sie übersprungen. Nur auf einem
bewusst ausgewählten physischen Gerät starten. Danach auch den UI-Ablauf prüfen:
Vorschlag 269 g → manuell korrigieren/bestätigen, Abbrechen, Hintergrundwechsel und
Fortsetzen eines Stapels. Ladezeit und Bildzeiten lassen sich aus den Phasenzeitstempeln
ablesen. Nach Fehlern den vollständigen Bericht kopieren.

Diese Gerätetests stehen noch aus. Weder der ursprüngliche Absturzgrund noch die
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

Beide Anbieter verwenden denselben Prompt und das kompakte Antwortformat
`{"value":161,"unit":"g"}` bzw. `{"value":null}` für unlesbare Anzeigen. Es werden
nur positive ganze Gramm vorgeschlagen. `16.1 g` wird als `NonWholeGrams` verworfen,
weder gerundet noch in `161 g` umgeschrieben. kg-Anzeigen bleiben erlaubt, wenn ihre
exakte Umrechnung ganze Gramm ergibt (z. B. `1.001 kg` → `1001 g`). Die Verschiebung
des Dezimalpunkts erfolgt vor der Double-Konvertierung, um Rundungsfehler zu vermeiden.
Da Küchenwaagen die Einheit häufig nicht im Display zeigen, wird eine positive ganze
Zahl ohne `unit` als Gramm behandelt; eine explizite Einheit hat weiterhin Vorrang.
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

## Plausible Vorschläge bei mehreren Anzeigen

Der gemeinsame Prompt verlangt jetzt den plausibelsten sichtbaren Gewichtswert
als verwerfbaren Vorschlag. Bei mehreren Wiegeflächen soll das Modell anhand der
Position von Lebensmittel oder Behälter die zugehörige Anzeige auswählen. Eine
unbelastete Feinwaage mit 0.00 g oder einem kleinen Restwert wie 0.04 g blockiert
die Hauptanzeige nicht. Die Auswahl verwendet weder eine feste obere Zeile noch
pauschal den größten Wert; Anzeigen werden nicht addiert.

Auch bei unsicheren Ziffern ist die wahrscheinlichste visuell begründete Lesart
erlaubt. Mehrere Anzeigen oder Unsicherheit allein sollen nicht zu `{"value":null}`
führen; dieses Ergebnis bleibt für Bilder ohne plausibel ablesbare Gewichtsanzeige.
Gewichtsschätzungen aus Lebensmitteln, erfundene oder entfernte Dezimalpunkte und
Runden bleiben ausgeschlossen. Nebenanzeigen dürfen Dezimalstellen enthalten;
der ausgewählte Zielwert unterliegt weiterhin der Ganzgramm-Prüfung des Parsers.
Antwortformat, Runtime, Modellrevisionen, Bildaufbereitung, Inferenzanzahl und
Verwerfen von Vorschlägen sind unverändert.

Am 2026-10-02 bestanden alle neun Tests der Klasse `ScaleRecognitionTest` mit
`:app:testDevReleaseUnitTest --tests com.maksimowiczm.foodyou.ai.ScaleRecognitionTest`.
Die erweiterten Gerätetests kompilierten erfolgreich mit
`./gradlew -I dev/ai-device-test.init.gradle :app:compileDevReleaseAndroidTestKotlinAndroid`.
Beide Gradle-Prüfungen verwendeten JDK 21 und den Workspace-Gradlecache;
`git diff --check` war ebenfalls erfolgreich.

Es war kein Android-Gerät angeschlossen; die Verbesserung ist daher
noch nicht durch echte Modellinferenz bestätigt. Zusätzlich zu den vorhandenen
Testbildern stehen folgende praktische Gegenfälle aus, da echte Fotos fehlen:

- Belastete Hauptwaage und etwa 0.04 g auf der unbelasteten Feinwaage: Hauptwert.
- Lebensmittel auf der anderen Wiegefläche mit einem positiven Ganzgrammwert:
  Anzeige dieser Wiegefläche, unabhängig von ihrer Position und Zifferngröße.
- Keine plausibel ablesbare Waagenanzeige: `{"value":null}`.

Diese Fälle mit echten Fotos auf dem bewusst ausgewählten physischen Gerät prüfen
und Modell, Soll-/Istwerte und Laufzeiten festhalten. JVM-Parser-Tests belegen nur
die Verarbeitung der Modellantworten, nicht die Erkennungsqualität.
