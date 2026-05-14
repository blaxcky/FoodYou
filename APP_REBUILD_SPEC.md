# Food You Rebuild Specification

Dieses Dokument beschreibt die App so detailliert, dass eine neue Implementierung ohne Übernahme des alten Fork-Codes gebaut werden kann. Es ist bewusst als Übergabe an eine KI oder ein Entwicklerteam geschrieben.

Ziel ist eine frische, performante Nutrition-Tracker-App, die die nützlichen Konzepte der bestehenden App übernimmt, aber keine Altlasten, keinen unklaren Fork-Code und keine versteckten Hintergrundarbeiten mitnimmt.

## 1. Produktziel

Die App ist ein persönliches Ernährungstagebuch mit:

- Tagesübersicht für gegessene Energie, verbrannte Energie und verbleibende Energie.
- Mahlzeitenkarten für Breakfast, Lunch, Dinner, Snacks oder vom Nutzer angelegte Mahlzeiten.
- Lebensmittel-Datenbank mit Produkten, Rezepten, externen Quellen und manueller Anlage.
- Schnellem Eintragen von Lebensmitteln in Mahlzeiten.
- Manuellen Einträgen, falls kein Lebensmittel angelegt werden soll.
- Tages- und Wochenernährungszielen.
- Aktivitätskarte mit Schritten, manuell verbrannten Kalorien und optionalem Health-Connect-Sync.
- Import/Export von Produktdaten.
- Personalisierung von Startseite, Nährwert-Reihenfolge, Darstellung und Farben.

Die wichtigste Rebuild-Anforderung: Die Home-Ansicht muss jederzeit flüssig scrollen. Keine Netzwerk-, Health-Connect-, Import-, Export-, Datenbank-Migrations- oder teure Aggregationsarbeit darf beim bloßen Anzeigen oder Scrollen der Home-Ansicht ausgelöst werden.

## 2. Nicht Verhandelbare Performance-Regeln

Die neue App muss diese Regeln einhalten:

1. Kein automatischer Health-Connect-Sync beim App-Start.
2. Kein automatischer Health-Connect-Sync beim Sichtbarwerden der ActivitiesCard.
3. Kein automatischer Health-Connect-Sync beim Datumwechsel im Home Feed.
4. Health Connect synchronisiert nur manuell über einen Sync-Button rechts oben in der Home-Topbar.
5. Der Sync-Button ist rot, wenn der letzte erfolgreiche Sync fehlt oder älter als 5 Minuten ist.
6. Der Sync-Button ist grün, wenn der letzte erfolgreiche Sync höchstens 5 Minuten alt ist.
7. Während ein manueller Sync läuft, ist der Button deaktiviert und visuell neutral oder grau.
8. Home-Scroll darf nur UI-Rendering und bereits lokale Daten lesen, niemals Netzwerk oder Health-Connect-Binder-Calls starten.
9. Home-Feed-Listen müssen stabile Keys und Content-Types verwenden.
10. Mahlzeitenkarten dürfen keine schweren Berechnungen direkt in Composables ausführen.
11. Alle aggregierten Tageswerte müssen aus ViewModel/Repository-Flows kommen, nicht aus UI-Schleifen.
12. Externe Produktdatenbanken, CSV-Import, CSV-Export und Health Connect müssen als explizite Nutzeraktionen laufen.
13. Lange Jobs müssen Fortschritt anzeigen und dürfen Back-Navigation blockieren oder bestätigen.
14. Datenbankabfragen für Home müssen indexiert sein: Datum, Mahlzeit, Food-IDs und Aktivitätsdatum.
15. Die App soll nach Möglichkeit nur lokale Daten im Hauptpfad anzeigen und entfernte Daten nur in dedizierten Screens laden.

## 3. Plattform und Technik

Empfohlener Stack:

- Android-first.
- Kotlin.
- Jetpack Compose.
- Material 3.
- Room oder SQLDelight als lokale Datenbank.
- DataStore für Preferences.
- Coroutines und Flow.
- Koin oder Hilt für DI.
- Navigation Compose.
- Health Connect optional, nur für manuelle Schritt-Synchronisierung.
- Barcode-Scanner optional über CameraX oder ZXing.

Falls die App wieder multiplatform gebaut werden soll, trenne Domain/Repository-Interfaces sauber von Android-spezifischen Implementierungen. Für den Neustart ist Android-first sinnvoller, weil Performance und Health Connect das Kernproblem sind.

## 4. Globale Informationsarchitektur

Startdestination ist Home.

Top-Level-Bereiche:

- Home
- Settings
- Food Diary Search
- Add Food Entry
- Update Food Entry
- Create Quick Add
- Update Quick Add
- Create Product
- Update Product
- Create Recipe
- Update Recipe
- Meal Settings
- Daily Goals
- Goals Detail
- Activity Settings
- Manual Activity Entry
- Database Settings
- External Databases
- Swiss Food Composition Database Import
- Import CSV Products
- Export CSV Products
- Personalization
- Theme Settings
- Home Personalization
- Nutrition Facts Personalization
- Language
- About
- Sponsor/Support, optional

Navigation-Verhalten:

- Back-Button oben links auf allen Subscreens.
- Unsaved changes zeigen einen Discard-Dialog.
- Save-Actions liegen rechts oben als IconButton oder FilledIconButton.
- Home-Titel klickt optional auf About.
- Long-Press auf Home-Karten öffnet deren Personalisierung oder Settings.

## 5. Home Screen

Home ist die wichtigste Ansicht.

### 5.1 Topbar

Links oder mittig:

- Titel: `Food You`
- Klick auf Titel öffnet About.

Rechts:

- Health-Connect-Sync-Button mit Sync-Icon.
- Settings-Button mit Zahnrad.

Sync-Button:

- Führt `syncSteps(selectedDate)` aus.
- Synchronisiert nur das aktuell ausgewählte Home-Datum.
- Keine automatische Synchronisierung für gestern oder heute im Hintergrund.
- Rot, wenn `lastHealthConnectSyncEpochSeconds == null` oder `now - lastSync > 300`.
- Grün, wenn `now - lastSync <= 300`.
- Grau während laufendem Sync.
- Disabled während laufendem Sync, um Doppel-Syncs zu verhindern.
- Bei Fehler keine App-Blockade. Optional Snackbar: Sync fehlgeschlagen, Berechtigung fehlt, Health Connect nicht verfügbar.

Settings-Button:

- Öffnet Settings.

### 5.2 Home Feed Reihenfolge

Standardreihenfolge:

1. Calendar
2. Goals
3. Meals
4. Activities

Der Nutzer kann diese Reihenfolge unter Home Personalization ändern.

Optional Polls/News/Support-Karte:

- Wenn übernommen, muss sie rein lokal oder gecached sein.
- Keine Netzwerkanfrage im Home-Render.
- Sie darf den Home-Feed nicht blockieren.

### 5.3 Calendar Card

Funktion:

- Zeigt eine Wochen-/Tagesauswahl.
- Ausgewähltes Datum wird im gesamten HomeState gehalten.
- Datumwechsel aktualisiert Goals, Meals und Activities lokal.

Darstellung:

- Monat und Jahr.
- Kalender-Icon rechts.
- Horizontale Tageschips mit Wochentag und Tag.
- Ausgewählter Tag farbig hervorgehoben.

Verhalten:

- Tip auf Tag setzt `selectedDate`.
- Tip auf Kalender-Icon kann später einen DatePicker öffnen.
- Datumwechsel darf keine Health-Connect-Synchronisierung auslösen.

### 5.4 Goals Card

Zweck:

- Zeigt Tageszusammenfassung für Energie und Makros.

Daten:

- Eaten energy: Summe aller Diary Entries des Tages.
- Burned energy: Summe aus Aktivitäten des Tages.
- Net energy: eaten - burned.
- Energy goal: aus DailyGoal des gewählten Datums.
- Protein, Fats, Carbohydrates: Tageswerte und Ziele.

Darstellung:

- Große Gauge/Arc für Energie.
- Links: gegessen und Prozent erreicht.
- Mitte: verbleibende Energie.
- Rechts: verbrannt und Ziel.
- Unten: Makro-Fortschrittsbalken für Fett, Kohlenhydrate, Protein.

Interaktion:

- Klick öffnet Goals Detail für das ausgewählte Datum.
- Long-Press öffnet Goals/Home-Card-Personalisierung.

Performance:

- Gauge muss leichtgewichtig sein.
- Keine animierten Dauereffekte im Feed.
- Werte werden im ViewModel voraggregiert und als primitives UI-Modell geliefert.

### 5.5 Meals Cards

Zweck:

- Zeigt alle Mahlzeiten des ausgewählten Tages mit den Einträgen.

Layouts:

- Vertical: jede Mahlzeit als eigene Karte in der Home-LazyColumn.
- Horizontal: ein horizontaler Pager/Row für Mahlzeiten.

Standard:

- Vertical.

Mahlzeiten:

- Standard initial: Breakfast, Lunch, Dinner, Snacks.
- Zeiten:
  - Breakfast: 06:00 - 10:00
  - Lunch: 10:00 - 15:00
  - Dinner: 15:00 - 21:00
  - Snacks: ganztägig oder gleiche Start-/Endzeit
- Nutzer kann Mahlzeiten anlegen, bearbeiten, löschen und sortieren.

Meal Card Inhalt:

- Name der Mahlzeit.
- Zeitbereich.
- Liste der Einträge.
- Summe kcal, Protein, Fett, Kohlenhydrate.
- Quick Add Button.
- Add Food Button.
- Long-Press öffnet Meal/Home-Personalisierung.

Eintragstypen:

- Food Entry: Produkt oder Rezept mit Measurement.
- Manual Entry: Quick Add mit Name und Nährwerten.

Food Entry Anzeige:

- Name.
- Messmenge, z.B. 100 g, 1 serving, 1 package.
- kcal.
- Protein, Fett, Kohlenhydrate.
- Optional Icon/Label für Rezept.
- Optional Fehlerdarstellung, wenn Pflichtdaten fehlen.

Manual Entry Anzeige:

- Name.
- kcal.
- Protein, Fett, Kohlenhydrate.
- Kein Measurement.

Interaktionen:

- Add Food öffnet Food Search für Datum und Meal ID.
- Quick Add öffnet Create Quick Add für Datum und Meal ID.
- Tip auf Entry öffnet Update Entry oder Update Quick Add.
- Swipe/Overflow/Delete löscht Entry mit Bestätigung oder Undo.

Performance:

- Für Vertical Layout: `LazyColumn.items` mit key `meal-${meal.id}` und ContentType `meal-empty`/`meal-foods`.
- Entry Rows sollen möglichst stabile, kleine Composables sein.
- Nutrition Strings im ViewModel oder in memoisierten Formatter-Helpers erzeugen.
- Keine teure `NutritionFacts`-Summierung im Composable.

### 5.6 Activities Card

Zweck:

- Zeigt Schritte, aus Schritten berechnete Kalorien, manuell verbrannte Kalorien und Gesamtverbrauch.

Daten:

- `steps`: letzte lokal gespeicherte Health-Connect-Step-Summary für das ausgewählte Datum.
- `stepEnergyKcal`: `steps * stepsCaloriesPerStepKcal`, wenn Faktor gesetzt, sonst 0.
- `manualEnergyKcal`: Summe aller manuellen Aktivitätseinträge des Tages.
- `totalEnergyKcal`: `stepEnergyKcal + manualEnergyKcal`.
- Liste der letzten manuellen Einträge, maximal 3 in der Card.

Darstellung:

- Header: Icon `DirectionsWalk`, Titel `Activities`, Add-Button.
- Drei Kennzahlen: Steps, Step kcal, Manual.
- Große Zeile: `Burned X kcal`.
- Bis zu drei manuelle Einträge mit Name und kcal.

Interaktion:

- Klick auf Card oder Plus öffnet Manual Activity Entry für ausgewähltes Datum.
- Klick auf manuellen Eintrag öffnet Manual Activity Entry im Edit-Modus.
- Long-Press öffnet Activity Settings.

Wichtig:

- `ActivitiesCard` setzt nur das Datum im ViewModel.
- `ActivitiesCard` darf niemals Health Connect syncen.
- Health Connect sync läuft ausschließlich über Home-Topbar-Button.

## 6. Food Search

Food Search ist der Flow zum Auswählen von Produkten/Rezepten für einen Tagebucheintrag.

Aufruf:

- Von einer Meal Card über Add Food.
- Parameter: `date`, `mealId`.

Topbar:

- Titel: Name der Mahlzeit.
- Subtitle: Datum.
- Back.

Content:

- Such-App mit lokaler Datenbank, Suchhistorie und optionalen Remote-Quellen.
- Suchergebnis kann Product oder Recipe sein.
- Klick auf Ergebnis öffnet Measurement-Auswahl oder direkte Messform.
- Nach Auswahl ruft Screen `onMeasure(foodId, measurement)` auf und navigiert zu Add Entry.

FAB:

- Expandierbarer Create-FAB.
- Optionen:
  - Recipe erstellen.
  - Product erstellen.

Snackbar:

- Wenn Add Entry erfolgreich ein neues Diary Entry erstellt, Food Search zeigt `Measurement added` oder ähnliche Bestätigung.

Remote Quellen:

- OpenFoodFacts optional.
- USDA optional.
- Swiss Food Composition Database importiert lokal.
- Remote-Fehler müssen verständlich angezeigt werden: Rate limit, API key missing, API key invalid, service unavailable.

## 7. Add Entry

Zweck:

- Fügt ein Product oder Recipe in das Tagebuch ein.

Parameter:

- `foodId`
- `mealId`
- `date`
- optional `measurement`

Daten:

- Food wird lokal beobachtet.
- Meal-Liste wird geladen.
- Measurement Suggestions werden geladen.
- Mögliche Measurement Types werden aus Food abgeleitet:
  - Solid: Gram, Ounce.
  - Liquid: Milliliter, FluidOunce.
  - Serving nur wenn servingWeight vorhanden.
  - Package nur wenn total/packageWeight vorhanden.

UI:

- Topbar mit Back, optional Edit Food, Delete Food, Overflow.
- Food-Details.
- Date Chips: gestern, heute, morgen und ursprüngliches Datum.
- Meal Picker.
- Measurement Picker.
- Save/Add Button.

Verhalten:

- Save erzeugt FoodDiaryEntry.
- Dabei wird eine Diary-Kopie des Produktes/Rezeptes gespeichert, damit historische Diary Entries stabil bleiben, auch wenn das Stammdaten-Food später geändert wird.
- Measurement Suggestion wird gespeichert.
- EventBus benachrichtigt Food Search, damit Snackbar erscheint.

Delete Food:

- Löscht Stammdaten-Produkt/Rezept.
- Diary-Kopien bestehender Einträge bleiben erhalten.

Recipe Ingredient Flow:

- Wenn Food ein Rezept ist, kann der Nutzer Zutaten sehen.
- Optional kann ein Rezept in Zutaten entpackt werden.

## 8. Update Entry

Zweck:

- Bearbeitet einen existierenden FoodDiaryEntry.

UI:

- Ähnlich Add Entry.
- Vorbelegt mit existierendem Datum, Mahlzeit und Measurement.
- Save aktualisiert Entry und `updatedAt`.
- Delete löscht Entry.
- Back mit Discard-Dialog bei Änderungen.

## 9. Quick Add / Manual Diary Entry

Zweck:

- Schneller manueller Tagebucheintrag ohne Produkt oder Rezept.

Create Quick Add Parameter:

- `date`
- `mealId`

Form:

- Name, Pflichtfeld.
- Protein, Carbohydrates, Fats optional/nicht negativ.
- Energy optional/nicht negativ.
- Toggle: auto calculate energy vs manual energy input.

Auto Calculate:

- kcal = `proteins * 4 + carbohydrates * 4 + fats * 9`.
- Wenn Auto aktiviert ist, wird Energy-Feld aus Makros berechnet.

Save:

- Erzeugt ManualDiaryEntry.
- Nährwerte ohne Eingabe werden als 0 behandelt.

Update Quick Add:

- Lädt ManualDiaryEntry.
- Gleiche Form.
- Save aktualisiert Entry.

## 10. Products

Product ist ein Stammdaten-Lebensmittel.

Felder:

- id
- name, Pflichtfeld
- brand, optional
- barcode, optional
- note, optional
- isLiquid
- packageWeight, optional
- servingWeight, optional
- source type
- source url, optional
- nutritionFacts pro 100 g oder 100 ml

Nutrition Pflichtfelder für Product:

- energy
- proteins
- carbohydrates
- fats

Weitere Nährwerte optional:

- saturatedFats
- transFats
- monounsaturatedFats
- polyunsaturatedFats
- omega3
- omega6
- sugars
- addedSugars
- dietaryFiber
- solubleFiber
- insolubleFiber
- salt
- cholesterol
- caffeine
- vitaminA
- vitaminB1
- vitaminB2
- vitaminB3
- vitaminB5
- vitaminB6
- vitaminB7
- vitaminB9
- vitaminB12
- vitaminC
- vitaminD
- vitaminE
- vitaminK
- manganese
- magnesium
- potassium
- calcium
- copper
- zinc
- sodium
- iron
- phosphorus
- selenium
- iodine
- chromium

Form-Verhalten:

- Barcode-Feld mit Scanner-Button.
- Checkbox `Treat as liquid`.
- Measurement Default:
  - solid: 100 g
  - liquid: 100 ml
- Package Weight erforderlich, wenn Measurement `Package` verwendet wird.
- Serving Weight erforderlich, wenn Measurement `Serving` verwendet wird.
- Energy kann automatisch aus Makros berechnet werden.
- Energie wird in der vom Nutzer gewählten Einheit angezeigt, intern kcal.

Create Product:

- Erstellt Product.
- Speichert FoodHistory.Created oder Downloaded/Imported.
- Nach Erstellung kann direkt Add Entry gestartet werden.

Update Product:

- Lädt Product.
- Bearbeitet alle Felder.
- Speichert FoodHistory.Edited.

Delete Product:

- Löscht Stammdaten-Product.
- Cascade löscht RecipeIngredients, in denen das Product Bestandteil ist, falls Datenmodell das erzwingt.
- Bestehende Diary Entries bleiben über DiaryProduct erhalten.

## 11. Recipes

Recipe ist ein Stammdaten-Lebensmittel aus Zutaten.

Felder:

- id
- name, Pflichtfeld
- servings, positive Ganzzahl
- note, optional
- isLiquid
- ingredients

Ingredient:

- foodId Product oder Recipe
- measurement

Berechnungen:

- totalWeight = Summe der Ingredient-Gewichte.
- servingWeight = totalWeight / servings.
- nutritionFacts = Summe Ingredient-NutritionFacts / totalWeight * 100.
- Recipe kann in Zutaten entpackt werden.

Validierung:

- Name nicht leer.
- Servings > 0.
- Ingredients nicht leer.
- Keine zirkulären Zutaten.
- Ein Rezept darf nicht sich selbst direkt oder indirekt enthalten.

Create Recipe:

- Form mit General-Daten und Ingredient-Liste.
- Add Ingredient öffnet Food Search oder Ingredient Picker.
- Ingredient kann bearbeitet oder gelöscht werden.
- Save erstellt Recipe und FoodHistory.Created.

Update Recipe:

- Lädt Recipe.
- Gleiche Form.
- Save aktualisiert Recipe und Ingredients atomar.

## 12. Meals Settings

Zweck:

- Nutzer kann Mahlzeiten konfigurieren.

Funktionen:

- Mahlzeit erstellen.
- Mahlzeit löschen.
- Mahlzeit umbenennen.
- Start- und Endzeit bearbeiten.
- Ganztägig-Flag über gleiche Start-/Endzeit oder explizit.
- Reihenfolge ändern.

Standarddaten beim ersten App-Start:

- Lokalisiert nach Systemsprache.
- Default ungefähr Breakfast, Lunch, Dinner, Snacks.

Reorder:

- Drag Handles.
- Save oder sofort persistiert, aber keine teuren Side Effects.

Home Meal Layout Settings:

- Layout: Horizontal oder Vertical.
- Standard: Vertical.
- Time-based sorting:
  - Wenn aktiv, aktuell laufende Mahlzeiten zuerst.
  - Optional all-day meals ans Ende.

## 13. Goals

### 13.1 Daily Goal Modell

DailyGoal besteht aus:

- MacronutrientGoal.
- Map zusätzlicher NutritionFactsField-Ziele.

MacronutrientGoal:

- Manual:
  - energyKcal
  - proteinsGrams
  - fatsGrams
  - carbohydratesGrams
- Distribution:
  - energyKcal
  - proteinsPercentage
  - fatsPercentage
  - carbohydratesPercentage
  - Umrechnung:
    - Protein g = percentage * kcal / 4
    - Carbs g = percentage * kcal / 4
    - Fat g = percentage * kcal / 9

Default:

- 2000 kcal.
- Protein 20%.
- Fat 30%.
- Carbs 50%.

### 13.2 Weekly Goals

WeeklyGoals:

- useSeparateGoals.
- monday bis sunday je DailyGoal.

Wenn `useSeparateGoals=false`:

- Montag-Goal gilt für alle Wochentage.
- UI zeigt nur einen Satz Felder.

Wenn `true`:

- UI zeigt Day Picker und separate Ziele je Wochentag.

### 13.3 Goals Setup UI

Funktionen:

- Toggle Gram vs Percentage für Makro-Ziele.
- Energy Field.
- Protein/Fat/Carbs Fields oder Sliders/Percentage Controls.
- Additional Nutrients nach Nährwert-Reihenfolge.
- Save Button nur aktiv bei validem Formular.
- Discard-Dialog bei Änderungen.

### 13.4 Goals Detail

Zweck:

- Detailansicht eines Tages.
- Zeigt gegessene Nährwerte, verbrannte Energie, Netto-Energie, Ziele und Fortschritt.
- Sollte dieselben Aggregationen wie Home Goals Card verwenden.

## 14. Activities

### 14.1 Datenmodell

DailyStepSummary:

- date
- steps
- syncedAt

ManualActivityEntry:

- id
- date
- name
- energyKcal
- createdAt
- updatedAt

DailyActivitySummary:

- steps
- stepEnergyKcal
- manualEnergyKcal
- totalEnergyKcal

### 14.2 Activity Settings

Funktionen:

- kcal per step eingeben.
- Health Connect aktivieren/deaktivieren.
- Status anzeigen:
  - Checking
  - Available
  - Unavailable
  - UpdateRequired
  - PermissionMissing
  - PermissionDenied
  - SyncFailed
  - Synced
- Letzten Sync anzeigen.

Wichtig:

- Aktivieren von Health Connect darf nicht automatisch synchronisieren.
- Nach Permission Grant wird nur `healthConnectStepsEnabled=true` gesetzt.
- Der eigentliche Sync passiert nur über Home Sync Button.

### 14.3 Manual Activity Entry

Form:

- Name, Pflichtfeld.
- Energy kcal, positive oder nicht negativ.
- Date.

Create:

- Speichert ManualActivityEntry.

Update:

- Bearbeitet existierenden Entry.

Delete:

- Löscht Entry.

### 14.4 Health Connect

Permissions:

- READ_STEPS.
- Optional Background Read darf entfallen, weil kein automatischer Background-Sync mehr gewünscht ist.

Sync-Verhalten:

- Nur manuell.
- Home Button ruft `syncSteps(listOf(selectedDate))`.
- `syncSteps` prüft:
  - Health Connect enabled?
  - Health Connect verfügbar?
  - READ_STEPS Permission vorhanden?
  - Aggregate StepsRecord.COUNT_TOTAL für Start/Ende des ausgewählten Datums.
  - Upsert DailyStepSummary.
  - Update `healthConnectStepsLastSyncedEpochSeconds` nur bei Erfolg.

Fehler:

- Missing permission: Health Connect optional deaktivieren oder Status setzen.
- Unavailable/Update required: Status setzen, nicht crashen.
- IOException/RemoteException/RuntimeException: failed status, keine UI-Blockade.

## 15. Database Settings und Import/Export

Database Settings Screen:

- External Databases.
- Import CSV Products.
- Export CSV Products.
- Database Backup.

### 15.1 External Databases

OpenFoodFacts:

- Toggle enable/disable.
- Privacy card.
- Optional Login Dialog für Credentials.
- Login Dialog:
  - username required.
  - password required.
  - password visibility toggle.
  - Linear progress während Auth.
  - Fehler bei Auth-Failure.
  - Credentials verschlüsselt speichern.

USDA:

- Toggle enable/disable.
- API key Dialog.
- Fehler für missing/invalid/rate limit/unauthorized/etc.

Swiss Food Composition Database:

- Lokaler Import-Screen.
- Sprachen: English, German, French, Italian.
- Je Sprache ca. 1190 Produkte.
- Import läuft explizit.
- Progress anzeigen.

### 15.2 CSV Import

Anforderungen:

- Nutzer wählt CSV-Datei.
- Nutzer mapped Spalten auf ProductField.
- Option skip header.
- Quelle wählbar, mindestens User oder SwissFoodCompositionDatabase.
- Import läuft in Transaktion.
- Fortschritt zählt importierte Produkte.
- Duplicate vermeiden über name, brand, barcode, source.

ProductField:

- Name
- Brand
- Barcode
- Note
- IsLiquid
- PackageWeight
- ServingWeight
- SourceUrl
- alle NutritionFacts-Felder

Parsing:

- Leere Werte, `-`, `null`, `<x`, `<=x` als null oder Zahl interpretieren.
- Boolean: `true`, `1`, `false`, `0`.

### 15.3 CSV Export

Anforderungen:

- Nutzer wählt Felder.
- Export schreibt Header.
- Exportiert Produkte paginiert, z.B. 250 pro Seite.
- Strings CSV-quoten.
- Boolean als 1/0.
- Null als leer.
- Progress anzeigen.

## 16. Personalization

Settings > Personalization:

- Startseite.
- Nährwertangaben.
- Energieeinheit.
- Farben/Theme.
- Sicherer Bildschirm.

### 16.1 Home Personalization

Zeigt reorderbare Liste:

- Kalender
- Tägliche Ziele
- Mahlzeiten
- Activities

Jeder Eintrag:

- Icon.
- Label.
- Drag Handle.
- Bei Goals/Meals/Activities zusätzlicher More-Button für Detailsettings.

Reorder:

- Persistiert in Settings.homeCardOrder.
- Debounced, z.B. 50 ms.

### 16.2 Meals Card Personalization

Siehe Meals Settings:

- Layout Horizontal/Vertical.
- Time-based sorting.
- Ignore all-day meals.
- Link zu Mahlzeiten-Einstellung.

### 16.3 Goals Card Personalization

Optionen:

- Expanded/compact Goals Card, falls übernommen.
- Link zu Daily Goals Setup.

### 16.4 Nutrition Facts Personalization

Nutzer kann Reihenfolge der Nährwertgruppen ändern:

- Proteins.
- Fats.
- Carbohydrates.
- Other.
- Vitamins.
- Minerals.

Diese Reihenfolge beeinflusst:

- Product Form.
- Goal Form.
- Food detail.
- Entry detail.

### 16.5 Energy Unit

Optionen:

- Kilocalories.
- Kilojoules.

Intern:

- Immer kcal speichern.
- Anzeige mit Formatter:
  - kcal: Wert unverändert.
  - kJ: kcal * 4.184.

### 16.6 Theme

Anforderungen:

- Material 3 Theme.
- Hell/Dunkel/System.
- Akzentfarbe.
- Optional zufällige Farben beim Start.
- Nutrient colors:
  - Protein.
  - Fat.
  - Carbs.

### 16.7 Secure Screen

Optional Android-only:

- Verhindert Screenshots/Screen recording über FLAG_SECURE.
- Toggle in Settings.

## 17. Settings

Settings Screen Einträge:

- Donate/Support.
- Personalization.
- Meals.
- Daily Goals.
- Activities.
- Data Import/Export.
- Language.
- Privacy Policy.
- About.

UI:

- Large top app bar.
- Back Button.
- List Items mit Icon, Titel, Subtitle.

## 18. Language und Localization

Die bestehende App hat viele Übersetzungen. Rebuild-Anforderung:

- Alle UI-Texte über Resource-System.
- Mindestens Deutsch und Englisch.
- Weitere Sprachen optional aus altem Ressourcensatz übernehmen.
- Sprache in Settings wählbar.
- App locale persistent setzen.
- Translation warning optional anzeigen, falls Übersetzung nicht verifiziert.

## 19. About und Support

About:

- App Name.
- Version.
- Lizenz/Datenschutz/Quellen.
- Optional Credits.

Sponsor:

- Optional.
- Darf Home nicht blockieren.
- Netzwerkdaten nur in Sponsor-Screen laden oder gecached.

## 20. Datenmodell für Neue Implementierung

### 20.1 Products

Tabelle `Product`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- name TEXT NOT NULL
- brand TEXT NULL
- barcode TEXT NULL
- note TEXT NULL
- isLiquid INTEGER NOT NULL
- packageWeight REAL NULL
- servingWeight REAL NULL
- sourceType TEXT or INTEGER NOT NULL
- sourceUrl TEXT NULL
- proteins REAL NULL
- carbohydrates REAL NULL
- energy REAL NULL
- fats REAL NULL
- all additional nutrient fields REAL NULL

Indexes:

- name.
- barcode.
- sourceType.
- FTS table for name, brand, note.

### 20.2 Recipes

Tabelle `Recipe`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- name TEXT NOT NULL
- servings INTEGER NOT NULL
- note TEXT NULL
- isLiquid INTEGER NOT NULL

Tabelle `RecipeIngredient`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- recipeId INTEGER NOT NULL REFERENCES Recipe ON DELETE CASCADE
- ingredientProductId INTEGER NULL REFERENCES Product ON DELETE CASCADE
- ingredientRecipeId INTEGER NULL REFERENCES Recipe ON DELETE CASCADE
- measurementType TEXT or INTEGER NOT NULL
- quantity REAL NOT NULL

Constraint:

- Genau eine von ingredientProductId oder ingredientRecipeId ist nicht null.

### 20.3 Food History

Tabelle `FoodEvent`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- type INTEGER NOT NULL
- epochSeconds INTEGER NOT NULL
- extra TEXT NULL
- productId INTEGER NULL
- recipeId INTEGER NULL

Types:

- Created
- Downloaded
- Imported
- Edited
- ImportedFromLegacy, optional

### 20.4 Measurement Suggestions

Tabelle `MeasurementSuggestion`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- productId INTEGER NULL
- recipeId INTEGER NULL
- type TEXT or INTEGER NOT NULL
- value REAL NOT NULL
- epochSeconds INTEGER NOT NULL

Index:

- productId.
- recipeId.
- epochSeconds.

### 20.5 Diary Snapshots

Wichtig: Diary Entries dürfen nicht kaputtgehen, wenn Products/Recipes später geändert oder gelöscht werden. Deshalb muss beim Eintragen eine Snapshot-Kopie gespeichert werden.

Tabelle `DiaryProduct`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- name TEXT NOT NULL
- sourceType
- sourceUrl
- packageWeight
- servingWeight
- isLiquid
- note
- alle NutritionFacts-Felder

Tabelle `DiaryRecipe`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- name TEXT NOT NULL
- servings INTEGER NOT NULL
- isLiquid INTEGER NOT NULL
- note TEXT NULL

Tabelle `DiaryRecipeIngredient`:

- id
- recipeId REFERENCES DiaryRecipe ON DELETE CASCADE
- ingredientProductId REFERENCES DiaryProduct
- ingredientRecipeId REFERENCES DiaryRecipe
- measurementType
- quantity

### 20.6 Diary Entries

Tabelle `Measurement` oder `FoodDiaryEntry`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- mealId INTEGER NOT NULL REFERENCES Meal ON DELETE CASCADE
- epochDay INTEGER NOT NULL
- productId INTEGER NULL REFERENCES DiaryProduct
- recipeId INTEGER NULL REFERENCES DiaryRecipe
- measurementType TEXT/INTEGER NOT NULL
- quantity REAL NOT NULL
- createdAt INTEGER NOT NULL
- updatedAt INTEGER NOT NULL

Index:

- mealId.
- epochDay.
- `(epochDay, mealId)`.

Tabelle `ManualDiaryEntry`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- mealId INTEGER NOT NULL REFERENCES Meal
- dateEpochDay INTEGER NOT NULL
- name TEXT NOT NULL
- nutrition fields
- createdEpochSeconds INTEGER NOT NULL
- updatedEpochSeconds INTEGER NOT NULL

### 20.7 Meals

Tabelle `Meal`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- name TEXT NOT NULL
- fromHour INTEGER NOT NULL
- fromMinute INTEGER NOT NULL
- toHour INTEGER NOT NULL
- toMinute INTEGER NOT NULL
- rank INTEGER NOT NULL

Index:

- rank.

### 20.8 Goals

Kann in DataStore/Preferences oder Tabellen gespeichert werden.

WeeklyGoals:

- useSeparateGoals Boolean.
- DailyGoal für monday...sunday.

DailyGoal:

- macro mode Manual/Distribution.
- energyKcal.
- proteinsGrams/fatsGrams/carbohydratesGrams oder percentages.
- map additional field -> goal grams.

### 20.9 Activities

Tabelle `DailyStepSummary`:

- dateEpochDay INTEGER PRIMARY KEY
- steps INTEGER NOT NULL
- syncedEpochSeconds INTEGER NOT NULL

Tabelle `ManualActivityEntry`:

- id INTEGER PRIMARY KEY AUTOINCREMENT
- dateEpochDay INTEGER NOT NULL
- name TEXT NOT NULL
- energyKcal REAL NOT NULL
- createdEpochSeconds INTEGER NOT NULL
- updatedEpochSeconds INTEGER NOT NULL

Index:

- dateEpochDay.

### 20.10 Preferences

Settings:

- lastRememberedVersion
- hidePreviewDialog
- showTranslationWarning
- nutrientsOrder
- secureScreen
- homeCardOrder
- expandGoalCard
- onboardingFinished
- energyFormat
- appLaunchInfo
- stepsCaloriesPerStepKcal
- healthConnectStepsEnabled
- healthConnectStepsLastSyncedEpochSeconds

MealsPreferences:

- layout: Horizontal/Vertical
- useTimeBasedSorting
- ignoreAllDayMeals

FoodSearchPreferences:

- OpenFoodFacts enabled.
- USDA enabled.
- optional API key state.

ThemeSettings:

- dark mode.
- seed/accent color.
- randomize on launch.
- nutrient colors.

## 21. Berechnungsregeln

### 21.1 Weight

Measurement Types:

- Gram(value), metric = value.
- Milliliter(value), metric = value.
- Ounce(value), metric = value / 0.03527396.
- FluidOunce(value), metric = value / 0.0338140227.
- Package(quantity), weight = packageWeight * quantity.
- Serving(quantity), weight = servingWeight * quantity.

### 21.2 Entry Nutrition

FoodDiaryEntry:

- weight = food.weight(measurement)
- entryNutrition = food.nutritionFacts * (weight / 100)

ManualDiaryEntry:

- nutritionFacts direkt aus Eingabe.

Meal Nutrition:

- Summe aller Entries einer Mahlzeit.

Day Nutrition:

- Summe aller Mahlzeiten des Tages.

Net Energy:

- net = eatenEnergy - burnedEnergy.

### 21.3 Nutrient Completeness

Nährwerte können vollständig oder unvollständig sein.

Empfehlung für Rebuild:

- Intern `Double?` verwenden.
- Bei Summen:
  - Wenn ein Wert fehlt, aber andere Werte vorhanden sind, Summe vorhandener Werte als incomplete markieren oder UI mit `--` anzeigen.
  - Pflichtmakros für Products sollten nicht null sein.
- Für Home Cards fehlende Werte als 0 anzeigen, aber Detailansichten sollten fehlende Daten kenntlich machen.

## 22. Such- und Remote-Datenquellen

### 22.1 Lokale Suche

Suche über:

- Product FTS: name, brand, note.
- Recipe FTS: name, note.
- Barcode exact match.
- Search history.

Sortierung:

- Exact matches zuerst.
- Danach FTS rank.
- Danach zuletzt verwendet/erstellt optional.

### 22.2 OpenFoodFacts

Funktionen:

- Produkt über URL oder Barcode laden.
- Felder mappen:
  - Name.
  - Brand.
  - Barcode.
  - NutritionFacts.
  - Package weight.
  - Serving weight.
  - source = OpenFoodFacts.
  - sourceUrl.
  - isLiquid nach Kategorie oder User-Korrektur.

### 22.3 USDA

Funktionen:

- Suche/Download über USDA API.
- API key erforderlich.
- Rate limit sauber behandeln.

### 22.4 Swiss Food Composition Database

Funktion:

- Lokale CSV-Dateien pro Sprache importieren.
- Produktquelle = SwissFoodCompositionDatabase.
- Duplicate check.

## 23. Fehler- und Loading-Zustände

Jeder Screen braucht:

- Loading.
- Empty.
- Error.
- Content.

Home:

- Skeletons/Shimmer nur für lokale Datenladezustände.
- Kein endloses Loading wegen Remote-Quellen.

Food Search:

- Empty state bei keiner Suche/keinen Ergebnissen.
- Remote-Fehler inline oder Snackbar.

Import/Export:

- Blocking progress screen.
- Back zeigt `Please wait` oder deaktiviert Back.

Forms:

- Inline validation.
- Save disabled bei invalid.
- Discard dialog bei modified.

## 24. UI Design Vorgaben

Allgemein:

- Material 3.
- Ruhige, utilitaristische App-Ästhetik.
- Keine Marketing-Heroes.
- Keine dekorativen Gradient-Orbs.
- Keine verschachtelten Cards.
- Kartenradius maximal moderat, bestehende Home-Karten können 24dp haben, normale Cards eher Material defaults.
- Icons für Aktionen.
- Text muss auf kleinen Displays passen.
- Buttons mit Icons, Tooltips für unklare Icons.

Home:

- Topbar pinned oder exitUntilCollapsed, aber ohne schwere Animation.
- Feed full height.
- Karten mit 8dp horizontalem Padding.
- Bottom spacing zwischen Karten 8dp.

Forms:

- LazyColumn.
- Keyboard/IME Padding.
- Save rechts oben.
- Required helper text.
- Numeric keyboard für Zahlen.

Accessibility:

- Content descriptions für IconButtons.
- Reorder Custom Accessibility Actions.
- Ausreichende Kontraste.
- Dynamic Type soweit möglich.

## 25. Kritische Rebuild-Entscheidungen

Diese Entscheidungen sollen bewusst anders als in der alten App sein:

1. Keine automatische Health-Connect-Synchronisierung.
2. Keine periodischen Worker für Schritte.
3. Keine Home-Card darf beim Anzeigen externe Systeme ansprechen.
4. Home ist read-only plus Navigation, außer explizite Nutzeraktionen wie Sync oder Delete.
5. Diary Entries speichern Snapshots, nicht nur Referenzen auf Stammdaten.
6. Import/Export und Remote Download laufen explizit und mit Progress.
7. Alle Home-Aggregate sollen aus gezielten SQL-Abfragen/Flows kommen.
8. Die neue App soll keine alten Migrationspfade aus FoodYou2/Fork übernehmen, außer wenn Datenmigration bewusst neu spezifiziert wird.

## 26. Empfohlene Implementierungsreihenfolge für eine KI

Phase 1: Fundament

- Neues Android-Projekt.
- Compose Material 3.
- Room.
- DataStore.
- Navigation.
- DI.
- Theme.
- Basis-Form-Komponenten.
- Date/Energy/Nutrition Formatter.

Phase 2: Domain und Datenbank

- NutritionFacts.
- Measurement.
- Product.
- Recipe.
- Meal.
- Diary Snapshot Tabellen.
- FoodDiaryEntry.
- ManualDiaryEntry.
- Goals.
- Activities.
- Settings.

Phase 3: Home MVP

- HomeState mit selectedDate.
- Calendar Card.
- Goals Card mit lokalen Aggregaten.
- Meals Cards Vertical.
- Activities Card ohne automatischen Sync.
- Home Sync Button mit Ampelstatus.

Phase 4: Entry Flows

- Food Search lokal.
- Add Entry.
- Update Entry.
- Quick Add create/update.
- Measurement Suggestions.

Phase 5: Stammdaten

- Create Product.
- Update Product.
- Create Recipe.
- Update Recipe.
- Barcode Scanner.

Phase 6: Settings

- Meal Settings.
- Daily Goals.
- Activity Settings.
- Personalization.
- Energy unit.
- Theme.
- Language.

Phase 7: External Data

- OpenFoodFacts.
- USDA.
- Swiss Food DB.
- CSV import/export.

Phase 8: Performance und Tests

- Macrobenchmark Home scroll.
- Frame timing auf realem Gerät.
- Test: ActivitiesCard sichtbar werden triggert keinen Sync.
- Test: App start triggert keinen Sync.
- Test: Datumwechsel triggert keinen Sync.
- Test: Sync Button triggert genau einen Sync.
- Test: Sync Button Farbe nach lastSync.
- Test: Food snapshots bleiben nach Product Delete stabil.

## 27. Akzeptanzkriterien

Eine neue Implementierung gilt als brauchbar, wenn:

- Home Feed auf einem Mittelklassegerät flüssig scrollt.
- Reproduzierbare Home-Scroll-Messung unter 5% janky frames bleibt.
- Beim Scrollen in Home keine Health-Connect-Logs, keine Netzwerk-Logs und keine langen DB-Transaktionen auftreten.
- ActivitiesCard kann sichtbar werden, ohne sichtbaren Hänger.
- Sync passiert nur nach Tap auf den Home-Sync-Button.
- Nach erfolgreichem Sync wird Button grün.
- Nach 5 Minuten ohne neuen Sync wird Button rot.
- Alle bestehenden Kernflows funktionieren: Produkt anlegen, Rezept anlegen, Food eintragen, Quick Add, Ziele ändern, Mahlzeiten ändern, manuelle Aktivität.
- Import/Export blockiert die App nicht unkontrolliert und zeigt Fortschritt.
- Bestehende Diary Entries bleiben historisch korrekt, auch wenn Stammdaten geändert werden.

## 28. Minimaler KI-Prompt für Umsetzung

Wenn dieses Dokument an eine KI übergeben wird, sollte der Auftrag so formuliert werden:

> Baue eine neue Android-App namens Food You als performanten Nutrition Tracker. Nutze diese Spezifikation als alleinige Produktquelle. Übernimm keinen alten Fork-Code. Implementiere zuerst das Fundament, dann das Domainmodell, dann Home MVP mit lokalem Read-only Feed. Health Connect darf niemals automatisch synchronisieren; nur der rote/grüne Sync-Button in der Home-Topbar darf `syncSteps(selectedDate)` starten. Jede Phase muss kompilieren und mit fokussierten Tests abgeschlossen werden.

## 29. Offene bewusste Produktentscheidungen

Diese Punkte müssen vor oder während der Umsetzung entschieden werden:

- Soll Sponsor/Polls wirklich wieder eingebaut werden oder entfallen?
- Soll iOS/KMP direkt mitgebaut werden oder erst Android stabil werden?
- Soll Background Health Connect dauerhaft entfallen? Empfehlung: ja.
- Soll USDA API Key vom Nutzer kommen oder gebundelt werden? Empfehlung: Nutzer-Key.
- Soll OpenFoodFacts Login Pflicht sein? Empfehlung: nein, nur optional.
- Soll es eine Migration alter Fork-Daten geben? Empfehlung: erst nach stabiler neuer App als separater Importer.

