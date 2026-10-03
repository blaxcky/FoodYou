# Manueller Trainingsimport

FoodYou meldet sich mit dem **bereits vorhandenen** E-Mail-/Passwort-Konto der
Trainings-App an. Es gibt keine Registrierung in FoodYou. Die öffentliche
Client-Konfiguration in `app/src/androidMain/assets/training-firebase.json`
stammt aus dem bestehenden Projekt `krafttraining-59773`. Ein benanntes
FirebaseApp-Objekt initialisiert Auth und Firestore explizit; das ist keine neue
App-Registrierung in der Firebase-Konsole. Keine neue Datenbank und kein
weiteres Firebase-Projekt werden benötigt.

## Ablauf

Einstellungen → Synchronisierung → Trainings-App → Anmelden. Der Schalter ist
beim ersten Login aktiviert. Der manuelle Sync auf der Startseite lädt zunächst
die gesamte Historie in Seiten von höchstens 200 Dokumenten mit `Source.SERVER`,
parallel zu Health Connect, Gewicht und FDDB. Danach fragt er nur Dokumente nach
dem gespeicherten Cursor aus `receivedAt` (Sekunden/Nanosekunden) und Dokument-ID
ab. Die Kaloriendifferenz wartet auf Schritte und Trainings; FDDB und Gewicht
müssen dafür nicht abgeschlossen sein. Es gibt keinen Firestore-Listener und keinen
automatischen Import beim App-Start. Offline/Timeout/Berechtigungsfehler werden
im FoodYou-Importstatus sichtbar, ohne andere Anbieter abzubrechen.

Schema-v1-Dokumente werden strikt geprüft. Ausschließlich `activityDate` bestimmt
den Buchungstag. Positive Krafttraining-/Cardiowerte bilden getrennte, editierbare
Verbrauchseinträge; beide 0 erzeugt nur einen Importbeleg.
Room 52 speichert beide Kategorien und Beleg atomar. Eindeutige Schlüssel aus
Projekt, UID und Import-ID verhindern Doppelzählung bei Wiederholung oder Neustart.
Veränderte Dokumente erzeugen einen Konflikt und ersetzen vorhandene Werte nicht.
Andere gültige Dokumente werden trotzdem verarbeitet. Cursor und offene IDs werden
nach jeder vollständig verarbeiteten Seite gemeinsam gespeichert. Bei Abbruch vor
dem Speichern wird die Seite erneut gelesen; Importbelege schützen auch manuell
bearbeitete oder gelöschte Verbrauchseinträge vor erneutem Import.

Neue Seiten haben Vorrang vor Fehlerwiederholungen. Pro manuellem Lauf werden danach
höchstens 20 beim Laufstart offene IDs in Gruppen von zehn erneut geprüft, mit
insgesamt zehn Sekunden Zeitbudget. Fehlgeschlagene IDs werden fair ans Ende der
Liste gestellt und im Status als weiterhin offen ausgewiesen. Jede Seitenabfrage
hat ein Timeout von 60 Sekunden; der vollständige Erstimport hat kein Gesamtlimit.
Verspätete Uploads werden über `receivedAt` gefunden und weiterhin unter ihrem
ursprünglichen `activityDate` gebucht. Die Abfrage setzt den bestehenden Vertrag
unveränderlicher Dokumente mit serverseitigem Empfangszeitpunkt voraus.

Nur das aktive Konto zählt zur Bilanz. Abmelden blendet dessen Importe aus;
Anmelden blendet sie wieder ein. Der Sync-Schalter verhindert lediglich neue
Abrufe. Manuelle Aktivitäten bleiben unabhängig. Ein Kontowechsel bricht den
alten Abruf ab; verspätete Ergebnisse werden nicht dem neuen Konto zugeordnet.
Widgets werden nach Import und Kontowechsel aktualisiert.

Passwörter werden nur an Firebase Auth übergeben, nicht selbst gespeichert.
Firebase-Auth-Preferences werden beim vollständigen Backup ausgeschlossen und
bei Wiederherstellung entfernt. Die Room-Daten einschließlich Importbelegen
werden mitgesichert; danach ist eine neue Anmeldung nötig. Status und Schalter
liegen zusammen mit Cursor und offenen IDs kontogetrennt im noBackup-Verzeichnis.
Dateizugriffe laufen serialisiert auf dem IO-Dispatcher. Vor einer Wiederherstellung
werden laufende Trainingsimporte abgebrochen und vollständig abgewartet. Erst danach
werden alle Cursor, offenen IDs und Berichte zurückgesetzt und die Datenbank ersetzt;
die Aktivierungseinstellungen bleiben erhalten. Auch bei Restore-Rollback bleibt ein
vollständiger erneuter Abgleich über die erhaltenen Importbelege sicher.
SDK-Fehlertexte werden nicht ungefiltert in Status oder Logs übernommen.

## Gezielte Prüfung

JDK 21 und Workspace-Gradle-Cache gemäß AGENTS.md setzen.

```sh
./gradlew :app:testDevReleaseUnitTest --tests '*TrainingImportTest' --tests '*TrainingImportDaoTest' --tests '*TrainingMigrationTest' --tests '*TrainingSyncCoordinatorTest' --tests '*TrainingAuthBackupTest' --tests '*FileTrainingSyncStorageTest' --tests '*HomeViewModelTest'
./gradlew :app:testDevReleaseUnitTest --tests '*TrainingSettingsScreenshotTest' --tests '*ActivitiesCardScreenshotTest' --tests '*SynchronizationSettingsScreenScreenshotTest' -Proborazzi.test.verify=true
```

Der native Integrationstest nutzt ausschließlich das Demo-Projekt
`demo-training-sync` mit lokalen Auth-/Firestore-Emulatoren und synthetischen
Konten. Die Regeln unter `dev/training-emulator` sind eine Testkopie des
Trainings-App-Vertrags; sie werden nicht ins Produktivprojekt deployt.
Die Firebase CLI muss separat vorhanden sein.

```sh
firebase emulators:start --project demo-training-sync --only auth,firestore --config dev/training-emulator/firebase.json
./dev/android-emulator.sh start
# ANDROID_SERIAL auf die vom Helper ausgegebene Seriennummer setzen:
./gradlew -I dev/training-emulator/test.init.gradle :app:connectedDevReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maksimowiczm.foodyou.training.FirebaseTrainingIntegrationTest
```

Der Emulatornachweis ersetzt nicht die produktive Abnahme: Mit dem bestehenden
Konto anmelden, den vorhandenen 0-kcal-Abschluss und einen positiven Abschluss
mehrfach synchronisieren und Bilanz sowie Importstatus prüfen. Insbesondere
projektseitige API-Key-Beschränkungen/App-Check-Richtlinien können erst dabei
abschließend geprüft werden.

## Verifikation dieser Änderung

Inkrementeller Import:

- 61 gezielte Unit-Tests bestanden: seitenweiser und fortsetzbarer Erstimport,
  begrenzte/fair rotierende Fehlerwiederholung, Abbruch, Kontowechsel,
  Speicherkompatibilität und Restore-Sperre sowie bestehende Import- und
  Startseiten-Sync-Prüfungen.
- Nativer Firebase-Test auf FoodYou_API_36 bestanden: gleiche Serverzeitpunkte
  über Seitengrenzen, gezielte ID-Abfragen, verspäteter Upload, präzise atomare
  Cursor-Speicherung auf Android sowie bestehende Offline- und Kontoprüfungen.

Nachweise des ursprünglichen Trainingsimports:

- Gezielte JVM-Tests für Validator, DAO/Transaktionsrollback, Datenbankneustart,
  Migration 51 → 52, Coordinator/Timeout/Kontowechsel, Backup-Ausschlüsse,
  parallelen Gesamtsync und Widget-Verbrauch bestanden.
- Roborazzi für Trainings-Anmeldung/Status, Synchronisierung und Aktivitätenkarte
  aufgenommen, visuell geprüft und anschließend gegen die Referenzen verifiziert.
- Nativer SDK-Test auf FoodYou_API_36 bestanden: bestehender Login nach Abmeldung,
  fünf Abrufe von positivem und 0-kcal-Abschluss ohne Doppelzählung, anderes Konto,
  Rückkehr zum ersten Konto, Offline-Serverabfrage und verweigerter Fremdzugriff.
- Normales `assembleDevRelease` ohne Test-Netzwerkfreigabe bestanden.
- Produktiver Login und echte Trainingsabschlüsse noch nicht abgenommen.
