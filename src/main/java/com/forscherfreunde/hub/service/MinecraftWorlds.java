package com.forscherfreunde.hub.service;

import com.forscherfreunde.hub.AppPaths;
import com.forscherfreunde.hub.model.UserProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tauscht die Minecraft-Welten eines Profils in den {@code .minecraft/saves}-Ordner ein und wieder aus.
 *
 * <p>Beim Einchecken wird eine Markierungsdatei geschrieben, die festhält, welchem Profil die Welten
 * gehören. Stürzt die App ab oder wird der Rechner ausgeschaltet, werden die Welten beim nächsten
 * Start automatisch zurück in das richtige Profil gesichert – statt verloren zu gehen.
 */
public final class MinecraftWorlds {

    private static final String MARKER = ".forscherfreunde-profil";

    private MinecraftWorlds() { }

    private static Path worldsOf(Path workDir) {
        return workDir.resolve("saves").resolve("Welten");
    }

    /** Lädt die Welten des Profils nach .minecraft/saves. */
    public static synchronized void checkIn(UserProfile profile, Progress progress) throws IOException {
        checkOut(progress); // Reste einer vorherigen Sitzung zuerst sichern
        Path saves = AppPaths.minecraftSaves();
        Files.createDirectories(saves);
        FileOps.clearDirectory(saves, p -> false);
        Path source = worldsOf(profile.workDir());
        if (Files.isDirectory(source)) {
            FileOps.copyTree(source, saves, progress, "Welten werden geladen …");
        }
        Files.writeString(saves.resolve(MARKER), profile.workDir().toString(), StandardCharsets.UTF_8);
    }

    /** Sichert die Welten zurück in das Profil, dem sie gehören, und leert .minecraft/saves. */
    public static synchronized void checkOut(Progress progress) throws IOException {
        Path saves = AppPaths.minecraftSaves();
        Path marker = saves.resolve(MARKER);
        if (!Files.exists(marker)) return;

        Path owner = Paths.get(Files.readString(marker, StandardCharsets.UTF_8).trim());
        if (Files.isDirectory(owner)) {
            Path target = worldsOf(owner);
            Files.createDirectories(target);
            FileOps.copyTree(saves, target, MinecraftWorlds::isWorldFile, progress, "Welten werden gesichert …");
        }
        try {
            FileOps.clearDirectory(saves, p -> p.getFileName().toString().equals(MARKER));
        } catch (IOException e) {
            throw new IOException("Die Welten wurden gesichert, aber Minecraft scheint noch zu laufen. "
                    + "Bitte schließe Minecraft zuerst.", e);
        }
        Files.deleteIfExists(marker);
    }

    /**
     * Übernimmt den aktuellen Stand aus .minecraft/saves in das Profil, <em>ohne</em> die Welten
     * aus Minecraft zu entfernen (z. B. vor dem Export auf einen USB-Stick). Tut nichts, wenn
     * gerade die Welten eines anderen Profils eingecheckt sind.
     */
    public static synchronized void syncToProfile(UserProfile profile, Progress progress) throws IOException {
        Path saves = AppPaths.minecraftSaves();
        Path marker = saves.resolve(MARKER);
        if (!Files.exists(marker)) return;
        Path owner = Paths.get(Files.readString(marker, StandardCharsets.UTF_8).trim());
        if (!owner.toAbsolutePath().normalize().equals(profile.workDir().toAbsolutePath().normalize())) return;
        Path target = worldsOf(owner);
        Files.createDirectories(target);
        FileOps.copyTree(saves, target, MinecraftWorlds::isWorldFile, progress, "Aktuelle Welten werden übernommen …");
    }

    /** Markierung und die von Minecraft gesperrte session.lock nicht mitkopieren. */
    private static boolean isWorldFile(Path p) {
        String n = p.getFileName().toString();
        return !n.equals(MARKER) && !n.equals("session.lock");
    }

    /** Ordner mit den Welten eines Profils. */
    public static Path worldsDir(UserProfile profile) {
        return worldsOf(profile.workDir());
    }

    public static boolean hasCheckedOutWorlds() {
        return Files.exists(AppPaths.minecraftSaves().resolve(MARKER));
    }
}
