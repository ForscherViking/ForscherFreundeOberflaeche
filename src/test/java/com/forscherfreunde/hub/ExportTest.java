package com.forscherfreunde.hub;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.ExportService;
import com.forscherfreunde.hub.service.MinecraftWorlds;
import com.forscherfreunde.hub.service.Progress;
import com.forscherfreunde.hub.service.UsbDrives;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportTest {

    @TempDir Path stick;

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private static Set<String> entries(Path zip) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            Set<String> s = new TreeSet<>();
            Collections.list(z.entries()).forEach(e -> s.add(e.getName()));
            return s;
        }
    }

    @Test
    void codeProjectWithoutBuildOutputButWithTestWorlds() throws IOException {
        UserProfile p = new UserProfile("Exporta", Camp.MODS);
        Path m = p.modulePath();
        write(m.resolve("src/main/java/Mod.java"), "class Mod {}");
        write(m.resolve("build.gradle"), "plugins {}");
        write(m.resolve("build/libs/mod.jar"), "x");
        write(m.resolve(".gradle/cache"), "x");
        write(m.resolve("run/mods/fabric.jar"), "x");
        write(m.resolve("run/options.txt"), "x");
        write(m.resolve("run/saves/Testwelt/level.dat"), "welt");

        Path zip = ExportService.export(p, stick, Progress.NONE);
        assertTrue(zip.startsWith(stick.resolve("ForscherFreunde")));
        assertTrue(zip.getFileName().toString().startsWith("Exporta - Camp Minecraft Mods - "));
        Set<String> e = entries(zip);
        assertEquals(Set.of("LIESMICH.txt",
                "CampMinecraftMods/src/main/java/Mod.java",
                "CampMinecraftMods/build.gradle",
                "CampMinecraftMods/run/saves/Testwelt/level.dat"), e);
        try (var s = Files.list(zip.getParent())) {
            assertTrue(s.noneMatch(f -> f.toString().endsWith(".part")), "keine halbfertigen Dateien");
        }

        Path second = ExportService.export(p, stick, Progress.NONE);
        assertNotEquals(zip, second, "vorhandene Datei wird nicht überschrieben");
        assertTrue(second.getFileName().toString().endsWith("(2).zip"));
    }

    @Test
    void minecraftWorldsIncludingCurrentStateFromRunningGame() throws IOException {
        UserProfile p = new UserProfile("Exportb", Camp.STADT);
        write(MinecraftWorlds.worldsDir(p).resolve("Burg/level.dat"), "alt");
        MinecraftWorlds.checkIn(p, Progress.NONE);
        Path saves = AppPaths.minecraftSaves();
        write(saves.resolve("Burg/level.dat"), "neu gebaut");
        write(saves.resolve("Burg/session.lock"), "gesperrt");
        write(saves.resolve("Insel/level.dat"), "insel");
        try {
            Path zip = ExportService.export(p, stick, Progress.NONE);
            assertEquals(Set.of("LIESMICH.txt", "Welten/Burg/level.dat", "Welten/Insel/level.dat"), entries(zip));
            try (ZipFile z = new ZipFile(zip.toFile())) {
                assertEquals("neu gebaut", new String(z.getInputStream(z.getEntry("Welten/Burg/level.dat")).readAllBytes()));
            }
            assertTrue(Files.exists(saves.resolve("Insel/level.dat")), "Welten bleiben im Spiel");
        } finally {
            MinecraftWorlds.checkOut(Progress.NONE);
        }
    }

    @Test
    void nothingToExport() {
        UserProfile p = new UserProfile("Exportc", Camp.LABOR);
        assertThrows(IOException.class, () -> ExportService.export(p, stick, Progress.NONE));
        assertFalse(Files.exists(stick.resolve("ForscherFreunde").resolve("x")));
    }

    @Test
    void detectsConfiguredDrives() {
        String old = System.getProperty("ffhub.usbDrives");
        try {
            System.setProperty("ffhub.usbDrives", stick + ";" + stick.resolve("gibt-es-nicht"));
            List<UsbDrives.Drive> d = UsbDrives.detect();
            assertEquals(1, d.size());
            assertEquals(stick, d.get(0).root());
            assertTrue(d.get(0).freeBytes() > 0);
        } finally {
            if (old == null) System.clearProperty("ffhub.usbDrives");
            else System.setProperty("ffhub.usbDrives", old);
        }
    }
}
