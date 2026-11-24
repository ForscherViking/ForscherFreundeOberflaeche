# ForscherFreunde Hub

Startoberfläche für die ForscherFreunde-Camps: Kinder wählen ihr Profil und starten IntelliJ
(Code-Camps) oder Minecraft (Minecraft-Module). Läuft bewusst schlank, auch auf leistungsschwachen Laptops.

## Installation auf einem Laptop

**`ForscherFreunde-Installieren.bat`** aus dem neuesten GitHub-Release herunterladen und doppelklicken.
Keine Admin-Rechte nötig. Das Skript (`installer/install.ps1`):

1. legt `Desktop\ForscherFreundeOberflaeche` mit `shared\templates|saves|backup|documentation` an –
   **bestehende Ordner und Daten werden nie überschrieben**,
2. lädt den Hub nach `Desktop\ForscherFreundeOberflaeche\hub` (nur wenn er dort noch fehlt; SHA-256-geprüft),
3. lädt eine Java-Laufzeit (Temurin 21, SHA-256-geprüft) nach `%LOCALAPPDATA%\ForscherFreundeHub\jre`
   (nicht auf den Desktop, damit OneDrive keine Laufzeitdateien synchronisiert oder auslagert),
4. lädt die Camp-Vorlagen aus den Repos **CampMinecraftMods** und **CampMinecraftAI** (jeweils Standard-Branch)
   nach `shared\templates\<Name>` und entpackt die enthaltene `run.zip` dorthin – nur wenn die Vorlage noch fehlt,
5. erstellt die Verknüpfung **„ForscherFreunde Hub“** mit ForscherFreunde-Symbol auf dem Desktop und im Startmenü.

Es kann beliebig oft ausgeführt werden (z. B. um eine gelöschte Verknüpfung wiederherzustellen).

| Aufruf | Zweck |
|---|---|
| `ForscherFreunde-Installieren.bat -Quiet` | ohne Rückfragen, für die Verteilung auf viele Laptops |
| `... -Start` | Hub danach direkt starten |
| `... -UpdateApp` | vorhandene Hub-JAR durch die neueste ersetzen (sonst macht das der Auto-Updater) |
| `... -UpdateTemplates` | Vorlagen neu von GitHub laden (bestehende Profile bleiben unverändert) |
| `... -OfflineDir E:\FF` | ohne Internet, z. B. vom USB-Stick |

**Offline / USB-Stick** (spart 130 × 45 MB Download): `install.ps1`, `ForscherFreunde-Installieren.bat`,
`ForscherFreundeHub.jar`, `ForscherFreunde.ico`, eine Temurin-JRE als ZIP
(`OpenJDK21U-jre_x64_windows_hotspot_*.zip` von adoptium.net) sowie die Vorlagen als `CampMinecraftMods.zip` und
`CampMinecraftAI.zip` (auf GitHub: *Code → Download ZIP*, Datei passend umbenennen) in einen Ordner legen und dort die `.bat` starten –
das Skript erkennt den Ordner automatisch.

> Windows SmartScreen kann beim ersten Start einer heruntergeladenen `.bat` warnen („Weitere Informationen“ →
> „Trotzdem ausführen“).

## Bauen & Starten

```bash
./gradlew jar          # erzeugt build/libs/ForscherFreundeHub.jar (eine Datei, alle Abhängigkeiten enthalten)
./gradlew test         # Tests (laufen in einem Sandbox-Ordner, nicht auf dem echten Desktop)
./gradlew run          # direkt aus dem Quellcode starten
```

Benötigt Java 17 oder neuer (der Installer bringt Java 21 mit). `start.bat` ist für die alte Installationsart
(`jre\` neben der JAR) weiterhin enthalten.

### Optionen

| Option | Wirkung |
|---|---|
| `-Dffhub.home=PFAD` / Umgebungsvariable `FFHUB_HOME` | anderer Datenordner (Standard: `Desktop/ForscherFreundeOberflaeche`, OneDrive-Desktop wird erkannt) |
| `-Dffhub.animations=false` | alle Animationen aus (für sehr alte Rechner) |
| `-Dffhub.idea=PFAD\idea64.exe` | IntelliJ-Pfad fest vorgeben (sonst: PATH, `Program Files\JetBrains`, Toolbox) |
| `-Dffhub.updates=false` | automatische Updates aus |
| `-Dffhub.autoMinimize=false` | Hub beim Start von IntelliJ/Minecraft nicht minimieren |
| `-Dffhub.updateUrl=URL` / `FFHUB_UPDATE_URL` | andere Update-Quelle (z. B. ein lokaler Server im Camp) |

## IntelliJ / Minecraft starten

* Solange IntelliJ bzw. Minecraft läuft, liegt der Hub minimiert in der Taskleiste. Wird das Programm
  geschlossen, kommt der Hub nach ca. 3–5 Sekunden von selbst wieder nach vorne.
  (Abschaltbar mit `-Dffhub.autoMinimize=false`.)
* **Einzelinstanz-Sperre:** Läuft IntelliJ bzw. Minecraft schon – auch wenn es noch lädt –, startet ein
  weiterer Klick kein zweites Mal; der Hub minimiert sich nur, damit das laufende Programm sichtbar ist.
* Erkannt wird am Programmpfad: IntelliJ = `idea64.exe`, Minecraft = Launcher oder Spiel (Pfad enthält
  „minecraft“). Geprüft wird alle 2 Sekunden und nur, solange ein Programm beobachtet wird.

## Ergebnisse auf USB-Stick mitnehmen

Am Ende des Camps klickt jedes Kind auf seiner Profilseite auf **„Auf USB-Stick speichern“**
(Betreuer:innen: USB-Symbol auf der Profilkarte in der Übersicht).

* Ist genau ein Stick eingesteckt, wird sofort gespeichert; sonst wartet der Hub und speichert,
  sobald ein Stick erkannt wird. Bei mehreren Sticks wird per Klick gewählt.
  Gestartet wird immer nur über den Knopf – ein eingesteckter Stick wird nie ungefragt beschrieben.
* Ergebnis: `<Stick>\ForscherFreunde\<Name> - <Camp> - <Datum>.zip` mit `LIESMICH.txt`
  (wie man zu Hause weitermacht). Vorhandene Dateien werden nie überschrieben.
* **Code-Camps:** das Projekt ohne `build/`, `.gradle/`, `out/` und ohne die Minecraft-Laufzeit `run/` –
  die selbst gebauten Testwelten (`run/saves`) kommen aber mit.
* **Minecraft-Module:** alle Welten des Profils, inklusive des aktuellen Stands, falls Minecraft gerade läuft.
* Sicher: freier Platz wird vorher geprüft, die Datei wird erst als `.part` geschrieben, auf den Stick
  geschrieben (nicht nur in den Cache), überprüft und erst dann umbenannt.
* Wird ein Stick nicht als Wechseldatenträger erkannt (manche USB-Festplatten):
  „Anderen Ordner wählen …“.

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

Alternativ auf GitHub: *Releases → Draft a new release*, neuen Tag (z. B. `v2.0.3`) eintragen, Beschreibung =
Hinweistext für die Betreuer:innen, *Publish release*. Die Action hängt die Dateien dann an dieses Release an.

Die GitHub Action (`.github/workflows/release.yml`) baut, testet, signiert und veröffentlicht das Release.
Erst wenn sie grün ist, enthält das Release `update.json`, die JAR und den Installer – vorher bitte keine leeren
Releases stehen lassen, sonst zeigt „neuestes Release“ auf ein Release ohne Dateien.
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
├── installer/         (Repo-Wurzel) install.ps1 + .bat + Symbol – werden bei jedem Release mit veröffentlicht
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
