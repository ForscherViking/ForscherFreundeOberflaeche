package com.forscherfreunde.hub.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramCatalogTest {

    @Test
    void defaultListIsValid() {
        List<Program> all = ProgramCatalog.parse(ProgramCatalog.defaults());
        assertEquals(List.of("paintnet", "blockbench", "mblock", "spike"), all.stream().map(Program::id).toList());
        Program paint = all.get(0);
        assertNotNull(paint.newFile(), "Paint.NET legt neue Bilder im Ordner des Kindes an");
        assertEquals("png", paint.newFile().endung());
        assertEquals("paintdotnet:", paint.protocol());
        assertEquals("Bilder", paint.folderName());
        assertFalse(paint.singleInstance(), "Paint.NET öffnet Dateien in der laufenden Instanz");
        Program mblock = all.get(2);
        assertTrue(mblock.singleInstance());
        assertEquals("https://ide.mblock.cc", mblock.webUrl());
        assertEquals("mBlock.exe", mblock.processName());
    }

    @Test
    void writesDefaultsOnFirstLoadAndReportsBrokenFile() throws IOException {
        Files.deleteIfExists(ProgramCatalog.file());
        ProgramCatalog c = ProgramCatalog.load();
        assertTrue(Files.exists(ProgramCatalog.file()), "Standardliste wird zum Bearbeiten abgelegt");
        assertNull(c.problem());
        assertEquals(4, c.all().size());

        Files.writeString(ProgramCatalog.file(), "{ kaputt");
        ProgramCatalog broken = ProgramCatalog.load();
        assertNotNull(broken.problem());
        assertEquals(4, broken.all().size(), "bei kaputter Datei: eingebaute Liste");
        Files.delete(ProgramCatalog.file());
    }

    @Test
    void filtersByCampAndSanitisesFolder() {
        List<Program> l = ProgramCatalog.parse("""
                {"programme":[
                  {"id":"a","name":"Nur Mods","camps":["CampMinecraftMods"],"ordner":"..\\\\..\\\\Windows"},
                  {"id":"b","name":"Alle"},
                  {"name":"ohne id"}
                ]}""");
        assertEquals(2, l.size(), "Einträge ohne id werden ignoriert");
        assertTrue(l.get(0).isForCamp("campminecraftmods"));
        assertFalse(l.get(0).isForCamp("CampMinecraftAI"));
        assertTrue(l.get(1).isForCamp("Zukunftslabor"));
        assertFalse(l.get(0).folderName().contains("\\\\"), "kein Ausbruch aus dem Profilordner");
        assertFalse(l.get(0).folderName().startsWith("."));
        assertEquals("Alle", l.get(1).folderName());
    }

    @Test
    void expandsEnvironmentVariables() {
        String home = System.getenv("HOME");
        if (home != null) assertEquals(home + "/x", Program.expand("%HOME%/x"));
        assertEquals("%GIBT_ES_NICHT_123%/x", Program.expand("%GIBT_ES_NICHT_123%/x"));
    }
}
