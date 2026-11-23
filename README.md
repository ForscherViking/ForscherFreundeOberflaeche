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
| `-Dffhub.updates=false` | automatische Updates aus |
| `-Dffhub.updateUrl=URL` / `FFHUB_UPDATE_URL` | andere Update-Quelle (z. B. ein lokaler Server im Camp) |

## Updates (Fernwartung der Laptops)

Die Laptops aktualisieren sich selbst – niemand muss mehr 130 Geräte einzeln anfassen.

**So funktioniert es auf dem Laptop**

1. Ca. 20 s – 2 min nach dem Start (zufällig verteilt) und danach alle 4 Stunden fragt der Hub
   `releases/latest/download/update.json` dieses Repos ab. Das ist ein normaler Download-Link, kein API-Aufruf –
   es gibt also kein Abfragelimit, auch wenn alle Laptops dieselbe Internetverbindung nutzen.
2. Ist dort eine neuere Version, wird sie im Hintergrund nach `%LOCALAPPDATA%\ForscherFreundeHub\updates`
   geladen (Programme-Ordner bleibt unangetastet; Kinder-Konten brauchen keine Admin-Rechte).
3. Installiert wird sie **erst beim nächsten Start** – nie mitten in der Arbeit. In der Kopfzeile erscheint
   „Update X installieren“; ein Klick startet den Hub sofort neu (Minecraft-Welten werden vorher gesichert).
4. **Sicherheit:** Jedes Update ist mit einem Ed25519-Schlüssel signiert, und die JAR muss exakt die
   signierte SHA-256-Prüfsumme haben. Ohne gültige Signatur wird nichts gestartet.
5. **Automatischer Rollback:** Startet eine neue Version zweimal hintereinander nicht, wird sie übersprungen
   und die vorherige läuft weiter. Die installierte JAR bleibt immer als Rückfallebene erhalten.

Welche Version läuft, steht oben rechts in der Kopfzeile.

**Einmalige Einrichtung** (macht ihr, nicht der Code – der private Schlüssel darf nie ins Repo):

```bash
./gradlew updateKeygen        # Windows: gradlew.bat updateKeygen
```

* schreibt den **öffentlichen** Schlüssel nach `src/main/resources/update/public-key.txt` → committen
* schreibt den **privaten** Schlüssel nach `~/forscherfreunde-update-signing-key.txt` → Inhalt auf GitHub unter
  *Settings → Secrets and variables → Actions* als `UPDATE_SIGNING_KEY` hinterlegen, Datei danach sicher
  verwahren (Passwortmanager) und vom Rechner löschen.

Solange kein öffentlicher Schlüssel eingebaut ist, sind Updates deaktiviert. **Wichtig:** Die erste Version,
die auf die Laptops kommt, muss den Schlüssel schon enthalten – danach geht alles automatisch.

**Ein Update veröffentlichen**

```bash
git tag -a v2.0.1 -m "Fehler beim Backup behoben"   # Nachricht wird beim Update angezeigt
git push origin v2.0.1
```

Die GitHub Action (`.github/workflows/release.yml`) baut, testet, signiert und veröffentlicht das Release.
Die Versionsnummer kommt aus dem Tag und muss höher sein als die aktuelle.

**Ein fehlerhaftes Update zurückziehen:** einfach eine neue, höhere Version mit dem alten Stand veröffentlichen
(z. B. `v2.0.2` vom Commit von `v2.0.0`). Versionen werden nie heruntergestuft.

## Aufbau

```
com.forscherfreunde.hub
├── FFHub              Einstieg: Einzelinstanz prüfen → Oberfläche starten
├── SingleInstance     Dateisperre + lokaler Socket: zweiter Start holt nur das Fenster nach vorne
├── AppPaths           alle Pfade an einer Stelle
├── model/             Camp (alle Camps/Module), UserProfile, ProfileStore (profiles.json)
├── service/           Dateioperationen, Backups, Minecraft-Welten, Programmstarts – ohne UI-Code
├── update/            Auto-Update: Prüfen, Laden, Signatur, Start der neuesten Version, Release-Werkzeug
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
