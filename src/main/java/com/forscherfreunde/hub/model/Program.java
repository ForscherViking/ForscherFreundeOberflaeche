package com.forscherfreunde.hub.model;

import com.forscherfreunde.hub.service.ExternalApp;
import com.forscherfreunde.hub.service.WatchedProgram;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein Programm aus {@code shared/programme.json}. Die Feldnamen sind deutsch, weil Betreuer:innen
 * die Datei direkt bearbeiten.
 */
public final class Program implements WatchedProgram {

    private static final Pattern ENV = Pattern.compile("%([^%]+)%");

    String id;
    String name;
    String beschreibung;
    String farbe;
    String symbol;
    List<String> pfade;
    String protokoll;
    String prozess;
    String ordner;
    NeueDatei neueDatei;
    List<String> dateiendungen;
    String webAdresse;
    List<String> camps;
    Boolean einmalig;
    Boolean strengeNamen;

    /** Vorlage für „Neue Datei“: eine leere Datei, die direkt im Ordner des Kindes angelegt wird. */
    public static final class NeueDatei {
        String name = "bild";
        String endung = "png";
        int breite = 1280;
        int hoehe = 720;

        public String name() { return name == null || name.isBlank() ? "Datei" : name.strip(); }
        public String endung() { return endung == null ? "png" : endung.strip().toLowerCase(Locale.ROOT); }
        public int breite() { return Math.max(16, Math.min(8000, breite)); }
        public int hoehe() { return Math.max(16, Math.min(8000, hoehe)); }
    }

    public String id() { return id; }

    @Override public String title() { return name; }

    public String description() { return beschreibung == null ? "" : beschreibung; }

    public String color() { return farbe; }

    public String symbol() { return symbol == null ? "" : symbol; }

    public List<String> paths() { return pfade == null ? List.of() : pfade; }

    public String protocol() { return protokoll == null || protokoll.isBlank() ? null : protokoll.strip(); }

    public String webUrl() {
        return webAdresse != null && webAdresse.strip().startsWith("https://") ? webAdresse.strip() : null;
    }

    public NeueDatei newFile() { return neueDatei; }

    /** Unterordner in „Meine Dateien“ – ohne Pfadtrenner, damit niemand aus dem Profil herausschreibt. */
    public String folderName() {
        String f = ordner == null || ordner.isBlank() ? name : ordner;
        return f.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("^\\.+", "").strip();
    }

    public List<String> extensions() { return dateiendungen == null ? List.of() : dateiendungen; }

    /** Nur einmal starten, wenn es schon läuft? Programme, die eine Datei öffnen, leiten diese selbst weiter. */
    /** Dateinamen nur klein, ohne Leerzeichen (für Minecraft-Texturen in IntelliJ)? */
    public boolean strictNames() { return Boolean.TRUE.equals(strengeNamen); }

    public boolean singleInstance() { return einmalig != null ? einmalig : neueDatei == null; }

    public boolean isForCamp(String campId) {
        return camps == null || camps.isEmpty() || camps.stream().anyMatch(c -> c.equalsIgnoreCase(campId));
    }

    boolean isValid() { return id != null && !id.isBlank() && name != null && !name.isBlank(); }

    /** Name der Programmdatei, an der ein laufendes Programm erkannt wird. */
    public String processName() {
        if (prozess != null && !prozess.isBlank()) return prozess.strip();
        for (String p : paths()) {
            int i = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
            return p.substring(i + 1);
        }
        return null;
    }

    @Override
    public boolean isRunning() {
        String exe = processName();
        if (exe == null) return false;
        String want = exe.toLowerCase(Locale.ROOT);
        return ExternalApp.anyProcessMatches(cmd -> {
            int i = Math.max(cmd.lastIndexOf('\\'), cmd.lastIndexOf('/'));
            return cmd.substring(i + 1).toLowerCase(Locale.ROOT).equals(want);
        });
    }

    /** Ersetzt %ProgramFiles% usw. durch die Werte der Umgebungsvariablen. */
    public static String expand(String path) {
        Matcher m = ENV.matcher(path);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = System.getenv(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
