# Automatischer FDDB-Produktabgleich im 30-Minuten-Takt

## Verhalten

- Der FDDB-Produktabgleich ist vom manuellen Tagebuch-Sync getrennt.
- Bei jedem `MainActivity.onStart()` wird passiv geprüft, ob der letzte
  Produktabruf mindestens 30 Minuten zurückliegt.
- Ein fälliger Lauf verarbeitet höchstens die nächsten zwei Produkte direkt
  nacheinander. Bei einer FDDB-Sperre wird der Lauf sofort beendet.
- Es gibt keinen Dauertimer, WorkManager, Vordergrunddienst und keine
  Benachrichtigung. Bleibt die App geöffnet, erfolgt kein weiterer Lauf.
- Der Abrufzeitpunkt wird vor jeder Netzwerkanfrage gespeichert. Wird der
  Prozess währenddessen beendet, kann derselbe Eintrag beim nächsten
  Vordergrundwechsel nach Ablauf der 30 Minuten erneut versucht werden.

## Manuelle Aktionen

- Der manuelle FDDB-Tagebuch-Sync bleibt unverändert und löst keinen
  Produktabgleich mehr aus.
- Der Sofortabruf eines Warteschlangenprodukts darf die Wartezeit umgehen,
  läuft aber serialisiert mit automatischen Abrufen und startet den
  30-Minuten-Zeitraum neu.
- Der Warteschlangenbildschirm zeigt statt des früheren `x/3`-Zählers, ob der
  nächste Lauf bereit ist oder ab welchem Zeitpunkt er frühestens möglich ist.

## Persistenz

- Der letzte begonnene Produktabruf wird als Epoch-Zeitstempel in den Settings
  gespeichert.
- Produktspezifische Versuche, Erfolge und Fehler bleiben in
  `FddbProductSyncStatus` gespeichert.
- Der frühere DataStore-Key `settings:fddbProductSyncManualCount` wird ignoriert;
  eine Room-Migration ist nicht erforderlich.
