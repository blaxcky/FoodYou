# FDDB-Produktabgleich mit gedrosselter Warteschlange

## Zusammenfassung

Bei jedem manuell ausgelösten FDDB-Tagebuch-Sync – sowohl über die
Synchronisations-Einstellungen als auch über den manuellen Home-Sync – wird ein
persistenter Zähler erhöht. Ab dem dritten gestarteten Sync werden nach einem
erfolgreich abgeschlossenen Tagebuch-Sync höchstens zwei FDDB-Produkte neu
geladen und vollständig aktualisiert.

## Implementierung

- Eine interne, gemeinsame Orchestrierung für beide manuellen
  Sync-Einstiegspunkte einführen:
  - Zähler in den Settings/DataStore persistieren.
  - Sync 1 und 2: nur Tagebuch synchronisieren.
  - Ab Sync 3: nach erfolgreichem Tagebuch-Sync die zwei fälligsten
    FDDB-Produkte abgleichen und den Zähler zurücksetzen.
  - Schlägt der Tagebuch-Sync fehl, keine zusätzlichen Produktanfragen starten
    und den fälligen Zyklus beibehalten.
  - Automatische oder nicht manuell ausgelöste Abläufe bleiben unverändert.

- Einen separaten internen Statusspeicher für FDDB-Produktabgleiche in Room
  ergänzen, statt das öffentliche `Product`-Modell zu erweitern:
  - Produkt-ID, letzter erfolgreicher Abgleich, letzter Versuch und letzter
    Fehler.
  - Datenbankmigration und DAO-Abfragen ergänzen.
  - Fällige Produkte: nur FDDB-Produkte mit gültiger FDDB-URL; zuerst noch nie
    erfolgreich abgeglichene, danach nach ältestem Erfolgsdatum, stabil nach
    Produkt-ID.
  - Erfolg setzt das Erfolgsdatum und löscht den Fehler; Fehler speichert
    Versuch und Fehler, lässt das Erfolgsdatum unverändert.
  - Bei einer FDDB-Sperre den aktuellen Zwei-Produkte-Lauf sofort stoppen; bei
    anderen einzelnen Produktfehlern den zweiten Kandidaten weiter prüfen.

- Den vorhandenen Einzelprodukt-Resync wiederverwenden, damit Nährwerte,
  Gewichte, Flüssigkeitsstatus und FDDB-Portionen identisch zum bestehenden
  manuellen Produkt-Resync aktualisiert werden.

- Eine neue, schreibgeschützte Unterseite unter den
  Synchronisations-Einstellungen hinzufügen:
  - Navigationseintrag in den Synchronisations-Einstellungen.
  - Gesamte priorisierte FDDB-Warteschlange anzeigen, inklusive Produktname/
    Marke, „noch nie“ bzw. letztem erfolgreichen Abgleich, letztem Versuch und
    Fehlerstatus.
  - Die ersten zwei Einträge klar als „Nächste zwei“ markieren.
  - Den aktuellen Fortschritt bis zum nächsten Produktabgleich (`x/3`)
    anzeigen.
  - Neue Strings zunächst auf Englisch und Deutsch ergänzen; übrige
    Lokalisierungen verwenden die vorhandene Fallback-Sprache.

## Schnittstellen und Daten

- Keine externen oder öffentlichen APIs ändern.
- Interne Repository-/Use-Case-Schnittstellen für Statusliste,
  Kandidatenauswahl, Erfolg/Fehler-Markierung und den manuellen
  Drei-Sync-Rhythmus ergänzen.
- Die bestehende FDDB-Tagebuch-Sync-Statusanzeige bleibt erhalten; der
  Produktabgleich erhält keine eigene globale Fehlermeldung, sondern zeigt
  Fehler pro Produkt in der neuen Liste.

## Tests und Verifikation

- Unit-Tests für:
  - Auswahlreihenfolge: nie abgeglichen vor ältestem erfolgreichen Abgleich.
  - Genau zwei zusätzliche Produktanfragen beim dritten manuellen Sync.
  - Zählerpersistenz, Reset nach behandeltem Lauf und Beibehaltung bei
    Tagebuch-Sync-Fehler.
  - Erfolgs- und Fehlerstatus sowie erneute Priorisierung fehlerhafter
    Produkte.
  - Abbruch nach `FddbAccessBlockedException`.
  - Beide manuellen Einstiegspunkte verwenden dieselbe Orchestrierung.

- Room-Migrationstest für die neue Status-Tabelle und ihre
  Fremdschlüssel-/Indexstruktur.

- UI-/ViewModel-Tests für die Warteschlangenansicht mit nächsten zwei
  Einträgen, „noch nie“-Status und Fehleranzeige.

- Alle bestehenden `ProductRepository`-Test-Fakes an die bereits vorhandene
  abstrakte Methode `observeProductsBySource(...)` anpassen; sie blockieren
  aktuell die Android-Test-Kompilierung.

- Mit JDK 21 und Workspace-Gradle-Cache kompilieren und relevante Tests
  ausführen; nach der zusammenhängenden Änderung committen und per GitHub CLI
  pushen.

## Annahmen

- „2 Werte“ bedeutet zwei FDDB-Lebensmittel pro fälligem Lauf.
- Ein „gestarteter“ manueller Sync zählt, auch wenn keine neuen
  Tagebucheinträge importiert werden.
- Ein fehlgeschlagenes Produkt wird erst beim nächsten fälligen
  Drei-Sync-Lauf erneut versucht, nicht sofort wiederholt.
