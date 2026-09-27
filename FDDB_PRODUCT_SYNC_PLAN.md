# Konfigurierbarer FDDB-Produktabgleich

## Verhalten

- Der Produktabgleich kann entweder beim App-Vordergrundwechsel (frühestens alle
  30 Minuten), bei jedem bzw. jedem dritten manuellen FDDB-Tagebuch-Sync oder gar
  nicht automatisch ausgeführt werden.
- Automatische und an den Tagebuch-Sync gekoppelte Läufe verarbeiten die nächsten
  zwei Produkte. Bei einer FDDB-Sperre wird ein Lauf sofort beendet.
- Es gibt keinen Dauertimer, WorkManager, Vordergrunddienst und keine
  Benachrichtigung. Bleibt die App geöffnet, erfolgt kein weiterer Lauf.
- Der Abrufzeitpunkt wird vor jeder Netzwerkanfrage gespeichert. Wird der
  Prozess währenddessen beendet, kann derselbe Eintrag beim nächsten
  Vordergrundwechsel nach Ablauf der 30 Minuten erneut versucht werden.

## Manuelle Aktionen

- Die Warteschlange kann unabhängig vom ausgewählten Modus mit einer frei
  gewählten Anzahl von Produkten gestartet werden. Dieser Stapel läuft im
  Application-Scope weiter, solange der App-Prozess lebt.
- Der Sofortabruf eines Warteschlangenprodukts darf die Wartezeit umgehen,
  läuft aber serialisiert mit automatischen Abrufen und startet den
  30-Minuten-Zeitraum neu.
- Der Warteschlangenbildschirm zeigt passend zum Modus die nächste automatische
  Zeit, den `x/3`-Zähler oder den deaktivierten Zustand sowie den Fortschritt
  eines manuellen Stapels.

## Persistenz

- Der letzte begonnene Produktabruf wird als Epoch-Zeitstempel in den Settings
  gespeichert.
- Modus, manuelle Frequenz und der aktuelle `x/3`-Zähler werden in den Settings
  gespeichert. Der neue Zähler-Key übernimmt bewusst keinen veralteten Stand
  aus der früheren Implementierung.
- Produktspezifische Versuche, Erfolge und Fehler bleiben in
  `FddbProductSyncStatus` gespeichert.
- Eine Room-Migration ist nicht erforderlich.
