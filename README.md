# ForscherFreunde Hub

Startoberfläche für die ForscherFreunde-Camps: Kinder wählen ihr Profil und starten IntelliJ
(Code-Camps) oder Minecraft (Minecraft-Module). Läuft bewusst schlank, auch auf leistungsschwachen Laptops.

## Bauen & Starten

```bash
./gradlew jar          # erzeugt build/libs/ForscherFreundeHub.jar (eine Datei, alle Abhängigkeiten enthalten)
./gradlew test         # Tests (laufen in einem Sandbox-Ordner, nicht auf dem echten Desktop)
./gradlew run          # direkt aus dem Quellcode starten
```

Ausgeliefert wird wie bisher `ForscherFreundeHub.jar` + `jre/` + `start.bat`. **Benötigt Java 17 oder neuer.**

### Optionen

| Option | Wirkung |
|---|---|
| `-Dffhub.home=PFAD` / Umgebungsvariable `FFHUB_HOME` | anderer Datenordner (Standard: `Desktop/ForscherFreundeOberflaeche`, OneDrive-Desktop wird erkannt) |
| `-Dffhub.animations=false` | alle Animationen aus (für sehr alte Rechner) |
| `-Dffhub.idea=PFAD\idea64.exe` | IntelliJ-Pfad fest vorgeben (sonst: PATH, `Program Files\JetBrains`, Toolbox) |

## Aufbau

```
com.forscherfreunde.hub
├── FFHub              Einstieg: Einzelinstanz prüfen → Oberfläche starten
├── SingleInstance     Dateisperre + lokaler Socket: zweiter Start holt nur das Fenster nach vorne
├── AppPaths           alle Pfade an einer Stelle
├── model/             Camp (alle Camps/Module), UserProfile, ProfileStore (profiles.json)
├── service/           Dateioperationen, Backups, Minecraft-Welten, Programmstarts – ohne UI-Code
└── ui/                Theme, Animationen, Bild-Cache, Karten-Komponenten
    └── screens/       Profilübersicht, Profil anlegen, Profilseite, Dokumentation
```

Neues Camp hinzufügen: Eintrag in `model/Camp.java` + Bild (JPG, ~720 px breit) unter
`src/main/resources/img/`. Mehr ist nicht nötig.

## Datenordner (unverändert)

```
ForscherFreundeOberflaeche/
├── profiles.json                       (Format kompatibel mit der alten Version)
├── <Name>/<Camp>/                      Code-Profile
├── <Name>/saves/Welten/                Minecraft-Profile
└── shared/templates | saves | backup | documentation
```

## Warum ist das schneller als vorher?

* **Ein Fenster, eine Instanz.** Früher öffnete jede Ansicht ein eigenes `JFrame`, und jeder Doppelklick
  startete eine weitere JVM. Jetzt beendet sich ein zweiter Start nach ~70 ms, bevor Swing überhaupt geladen ist.
* **Bilder:** 25 MB PNG (bis 1024×1536) → 1,1 MB JPG. Sie werden im Hintergrund dekodiert, einmal pro
  Größe hochwertig skaliert und als hardwarebeschleunigte Bilder gecacht. Vorher wurde bei jedem Bildschirmwechsel
  mit dem sehr langsamen `getScaledInstance(SCALE_SMOOTH)` neu skaliert und jedes Bild zusätzlich in Graustufen umgerechnet.
* **Keine Dauer-Timer:** Es gibt einen einzigen Animations-Timer, der nur läuft, solange etwas animiert wird.
  Vorher liefen ein 20-Hz-Puls-Timer und ein 10-Hz-Abfrage-Timer endlos. Im Leerlauf braucht die App jetzt praktisch keine CPU.
* **Dateioperationen** laufen auf einem eigenen Hintergrund-Thread mit gedrosselten Fortschrittsmeldungen;
  die Oberfläche friert beim Kopieren nicht mehr ein.
* **Schlanke Abhängigkeiten:** Gson + FlatLaf statt Gson, Jackson, org.json, commons-io und PDFBox.
