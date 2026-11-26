package com.forscherfreunde.hub.service;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.Program;
import com.forscherfreunde.hub.model.ProgramCatalog;
import com.forscherfreunde.hub.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProgramLauncherTest {

    private static Program paint() {
        return ProgramCatalog.load().all().stream().filter(p -> p.id().equals("paintnet")).findFirst().orElseThrow();
    }

    @Test
    void blankImagesLandInKidsFolderAndUntouchedOnesAreRemoved() throws Exception {
        UserProfile kid = new UserProfile("Malerin", Camp.MODS);
        Program paint = paint();
        Path a = ProgramLauncher.createBlankFile(paint, kid);
        Path b = ProgramLauncher.createBlankFile(paint, kid);
        assertEquals(kid.workDir().resolve("Meine Dateien").resolve("Bilder").resolve("Bild 1.png"), a);
        assertEquals("Bild 2.png", b.getFileName().toString());
        BufferedImage img = ImageIO.read(a.toFile());
        assertEquals(1280, img.getWidth());
        assertEquals(0xFFFFFF, img.getRGB(10, 10) & 0xFFFFFF, "weiße Leinwand");
        assertTrue(ProgramLauncher.recentFiles(kid, List.of(paint), 10).isEmpty(), "leere Vorlagen zählen nicht als Dateien");

        // Kind malt in Bild 2 und speichert
        Files.write(b, Files.readAllBytes(b));
        Files.setLastModifiedTime(b, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertEquals(1, ProgramLauncher.cleanupUntouchedBlanks());
        assertFalse(Files.exists(a), "unbemaltes Bild wird weggeräumt");
        assertTrue(Files.exists(b), "bemaltes Bild bleibt");

        Files.writeString(b.resolveSibling("notizen.txt"), "x");
        List<ProgramLauncher.KidFile> files = ProgramLauncher.recentFiles(kid, List.of(paint), 10);
        assertEquals(1, files.size(), "nur Dateien mit passender Endung");
        assertEquals(b, files.get(0).path());
    }

    @Test
    void startsProgramWithFileInKidsFolder(@TempDir Path tmp) throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"));
        Path out = tmp.resolve("aufruf.txt");
        Path exe = tmp.resolve("malprogramm");
        Files.writeString(exe, "#!/bin/sh\necho \"$PWD|$1\" > '" + out + "'\n");
        Files.setPosixFilePermissions(exe, PosixFilePermissions.fromString("rwxr-xr-x"));
        Program fake = new com.google.gson.Gson().fromJson("{\"id\":\"x\",\"name\":\"Mal\",\"ordner\":\"Bilder\",\"pfade\":[\""
                + exe + "\"],\"neueDatei\":{\"name\":\"Bild\",\"endung\":\"png\",\"breite\":20,\"hoehe\":20}}", Program.class);
        UserProfile kid = new UserProfile("Starter", Camp.AI);
        assertEquals(ProgramLauncher.How.PROGRAM, ProgramLauncher.availability(fake));
        Path file = ProgramLauncher.createBlankFile(fake, kid);
        assertEquals(ProgramLauncher.How.PROGRAM, ProgramLauncher.open(fake, kid, file));
        for (int i = 0; i < 50 && !Files.exists(out); i++) TimeUnit.MILLISECONDS.sleep(100);
        String[] call = Files.readString(out).strip().split("\\|");
        assertEquals(ProgramLauncher.folder(fake, kid).toRealPath().toString(), Path.of(call[0]).toRealPath().toString(),
                "Arbeitsordner = Ordner des Kindes");
        assertEquals(file.toString(), call[1], "die neue Datei wird geöffnet");
    }

    @Test
    void notInstalledWithoutFallback() {
        Program none = new com.google.gson.Gson().fromJson(
                "{\"id\":\"y\",\"name\":\"Fehlt\",\"pfade\":[\"/gibt/es/nicht.exe\"]}", Program.class);
        assertEquals(null, ProgramLauncher.availability(none));
    }
}
