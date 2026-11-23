package com.forscherfreunde.hub.service;

import com.forscherfreunde.hub.AppPaths;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Startet externe Programme (IntelliJ, Minecraft, Dateien). Alle Methoden blockieren kurz → im Hintergrund aufrufen. */
public final class Launcher {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static volatile Path cachedIdea;

    private Launcher() { }

    public static void openIntelliJ(Path project) throws IOException {
        if (!Files.isDirectory(project)) throw new IOException("Projektordner nicht gefunden:\n" + project);
        Path idea = findIntelliJ();
        String exe = idea != null ? idea.toString() : (WINDOWS ? "idea64.exe" : "idea");
        try {
            new ProcessBuilder(exe, project.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new IOException("IntelliJ wurde nicht gefunden. Bitte IntelliJ IDEA installieren.", e);
        }
    }

    public static void openMinecraft() throws IOException {
        Path lnk = AppPaths.desktop().resolve("Minecraft.lnk");
        if (Files.exists(lnk)) {
            openFile(lnk);
            return;
        }
        if (WINDOWS) {
            String pf86 = System.getenv("ProgramFiles(x86)");
            if (pf86 != null) {
                Path legacy = Paths.get(pf86, "Minecraft Launcher", "MinecraftLauncher.exe");
                if (Files.exists(legacy)) {
                    new ProcessBuilder(legacy.toString()).start();
                    return;
                }
            }
        }
        throw new IOException("Minecraft-Verknüpfung nicht gefunden:\n" + lnk);
    }

    public static void openFile(Path file) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file.toFile());
        } else if (WINDOWS) {
            new ProcessBuilder("cmd", "/c", "start", "\"\"", file.toString()).start();
        } else {
            new ProcessBuilder("xdg-open", file.toString()).start();
        }
    }

    private static Path findIntelliJ() {
        Path cached = cachedIdea;
        if (cached != null && Files.exists(cached)) return cached;

        String exe = WINDOWS ? "idea64.exe" : "idea";
        List<Path> candidates = new ArrayList<>();
        String override = System.getProperty("ffhub.idea");
        if (override != null) candidates.add(Paths.get(override));

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    try { candidates.add(Paths.get(dir.trim(), exe)); } catch (RuntimeException ignored) { }
                }
            }
        }
        if (WINDOWS) {
            addInstallations(candidates, System.getenv("ProgramFiles"), "JetBrains");
            addInstallations(candidates, System.getenv("LOCALAPPDATA"), "Programs");
            addInstallations(candidates, System.getenv("LOCALAPPDATA"), "JetBrains\\Toolbox\\apps\\IDEA-C\\ch-0");
            addInstallations(candidates, System.getenv("LOCALAPPDATA"), "JetBrains\\Toolbox\\apps\\IDEA-U\\ch-0");
        }
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) {
                cachedIdea = c;
                return c;
            }
        }
        return null;
    }

    /** Fügt {@code base/sub/<neueste IntelliJ-Version>/bin/idea64.exe} hinzu. */
    private static void addInstallations(List<Path> out, String base, String sub) {
        if (base == null) return;
        Path dir = Paths.get(base, sub);
        if (!Files.isDirectory(dir)) return;
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString().toLowerCase();
                if (n.contains("intellij") || n.matches("[0-9.]+")) found.add(p.resolve("bin").resolve("idea64.exe"));
            }
        } catch (IOException ignored) {
            return;
        }
        found.sort(Comparator.comparing(Path::toString).reversed()); // neueste Version zuerst
        out.addAll(found);
    }
}
