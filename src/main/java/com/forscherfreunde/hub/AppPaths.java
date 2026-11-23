package com.forscherfreunde.hub;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Stelle für alle Pfade der Anwendung.
 *
 * <p>Der Datenordner liegt wie bisher unter {@code Desktop/ForscherFreundeOberflaeche}.
 * Zusätzlich wird ein per OneDrive umgeleiteter Desktop erkannt, und der Ordner kann mit
 * {@code -Dffhub.home=...} oder der Umgebungsvariable {@code FFHUB_HOME} überschrieben werden.
 */
public final class AppPaths {

    public static final String DATA_FOLDER_NAME = "ForscherFreundeOberflaeche";

    private static final Path HOME = Paths.get(System.getProperty("user.home"));
    private static final Path DESKTOP = resolveDesktop();
    private static final Path ROOT = resolveRoot();

    private AppPaths() { }

    /** Datenordner mit profiles.json, Profilordnern und {@code shared/}. */
    public static Path root() { return ROOT; }

    public static Path desktop() { return DESKTOP; }

    public static Path profilesFile() { return ROOT.resolve("profiles.json"); }

    public static Path codeTemplates() { return ROOT.resolve("shared").resolve("templates"); }

    public static Path worldTemplates() { return ROOT.resolve("shared").resolve("saves"); }

    public static Path backups() { return ROOT.resolve("shared").resolve("backup"); }

    public static Path documentation() { return ROOT.resolve("shared").resolve("documentation"); }

    /** Ordner für interne Zustandsdateien (Einzelinstanz-Sperre etc.). */
    public static Path stateDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Paths.get(local, "ForscherFreundeHub");
        }
        return HOME.resolve(".forscherfreundehub");
    }

    /** Der saves-Ordner des Minecraft-Launchers. */
    public static Path minecraftSaves() {
        String override = System.getProperty("ffhub.minecraft");
        if (override != null && !override.isBlank()) return Paths.get(override);
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank() ? Paths.get(appData) : HOME;
        return base.resolve(".minecraft").resolve("saves");
    }

    /** Ist {@code p} ein echter Unterordner des Datenordners? Schutz vor versehentlichem Löschen. */
    public static boolean isInsideRoot(Path p) {
        if (p == null) return false;
        Path root = ROOT.toAbsolutePath().normalize();
        Path n = p.toAbsolutePath().normalize();
        return n.startsWith(root) && !n.equals(root);
    }

    private static Path resolveDesktop() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(HOME.resolve("Desktop"));
        for (String env : new String[] {"OneDrive", "OneDriveConsumer", "OneDriveCommercial"}) {
            String od = System.getenv(env);
            if (od != null && !od.isBlank()) {
                candidates.add(Paths.get(od, "Desktop"));
                candidates.add(Paths.get(od, "Schreibtisch"));
            }
        }
        // Bevorzugt den Desktop, auf dem der Datenordner tatsächlich liegt.
        for (Path c : candidates) {
            if (Files.isDirectory(c.resolve(DATA_FOLDER_NAME))) return c;
        }
        for (Path c : candidates) {
            if (Files.isDirectory(c)) return c;
        }
        return candidates.get(0);
    }

    private static Path resolveRoot() {
        String override = System.getProperty("ffhub.home");
        if (override == null || override.isBlank()) override = System.getenv("FFHUB_HOME");
        if (override != null && !override.isBlank()) return Paths.get(override);
        return DESKTOP.resolve(DATA_FOLDER_NAME);
    }
}
