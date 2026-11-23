package com.forscherfreunde.hub;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.ProfileStore;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.MinecraftWorlds;
import com.forscherfreunde.hub.service.Progress;
import com.forscherfreunde.hub.service.WorkspaceService;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Läuft gegen einen Sandbox-Datenordner (siehe build.gradle → test). */
class WorkspaceTest {

    private final Path root = AppPaths.root();

    @BeforeEach
    void setUp() throws IOException {
        wipe(root);
        wipe(AppPaths.minecraftSaves());
        Path tpl = AppPaths.codeTemplates().resolve(Camp.MODS.id()).resolve("src");
        Files.createDirectories(tpl);
        for (int i = 0; i < 20; i++) Files.writeString(tpl.resolve("A" + i + ".java"), "class A" + i + " {}");
        Path worlds = AppPaths.worldTemplates().resolve(Camp.STADT.id()).resolve("Welten").resolve("Start");
        Files.createDirectories(worlds);
        Files.writeString(worlds.resolve("level.dat"), "start");
    }

    @Test
    void nameValidation() throws IOException {
        ProfileStore store = ProfileStore.load();
        store.add(new UserProfile("Mia", Camp.MODS));
        assertNotNull(store.validateName(".."));
        assertNotNull(store.validateName("a/b"));
        assertNotNull(store.validateName("mia"), "Doppelter Name (Groß-/Kleinschreibung egal)");
        assertNotNull(store.validateName("shared"));
        assertNotNull(store.validateName("   "));
        assertNull(store.validateName("Paul"));
    }

    @Test
    void oldProfilesJsonStillLoads() throws IOException {
        Files.createDirectories(root);
        Files.writeString(AppPaths.profilesFile(), "[{\"name\":\"Alt\",\"workingDirectory\":\"" + esc(root.resolve("Alt"))
                + "\",\"moduleName\":\"CampMinecraftAI\",\"modulePath\":\"" + esc(root.resolve("Alt/CampMinecraftAI"))
                + "\",\"codeProfile\":true}]");
        ProfileStore store = ProfileStore.load();
        assertEquals(1, store.all().size());
        assertEquals(Camp.AI, store.all().get(0).module());
        assertTrue(store.all().get(0).isCodeProfile());
    }

    @Test
    void createBackupRestoreDelete() throws IOException {
        UserProfile p = new UserProfile("Paul", Camp.MODS);
        int[] calls = {0};
        WorkspaceService.create(p, (m, f) -> calls[0]++);
        Path code = p.modulePath();
        assertTrue(Files.exists(code.resolve("src/A0.java")));
        assertTrue(calls[0] > 2);

        Files.writeString(code.resolve("Mein.java"), "v1");
        WorkspaceService.backup(p, Progress.NONE);
        assertNotNull(WorkspaceService.lastBackup(p));
        Files.writeString(code.resolve("Mein.java"), "kaputt");
        Files.writeString(code.resolve("Neu.java"), "x");
        WorkspaceService.restore(p, Progress.NONE);
        assertEquals("v1", Files.readString(code.resolve("Mein.java")));
        assertFalse(Files.exists(code.resolve("Neu.java")));

        Path readOnly = code.resolve(".git/objects/ab/cd");
        Files.createDirectories(readOnly.getParent());
        Files.writeString(readOnly, "obj");
        readOnly.toFile().setWritable(false);
        WorkspaceService.delete(p, Progress.NONE);
        assertFalse(Files.exists(p.workDir()));
        assertFalse(Files.exists(p.backupDir()));
    }

    @Test
    void refusesToDeleteOutsideDataFolder() {
        Gson gson = new Gson();
        UserProfile outside = gson.fromJson("{\"name\":\"X\",\"workingDirectory\":\"" + esc(root.getParent())
                + "\",\"moduleName\":\"CampMinecraftMods\"}", UserProfile.class);
        UserProfile rootItself = gson.fromJson("{\"name\":\"X\",\"workingDirectory\":\"" + esc(root)
                + "\",\"moduleName\":\"CampMinecraftMods\"}", UserProfile.class);
        assertThrows(IOException.class, () -> WorkspaceService.delete(outside, Progress.NONE));
        assertThrows(IOException.class, () -> WorkspaceService.delete(rootItself, Progress.NONE));
        assertTrue(Files.exists(AppPaths.codeTemplates()));
    }

    @Test
    void minecraftWorldsSurviveCrash() throws IOException {
        UserProfile p = new UserProfile("Ida", Camp.STADT);
        WorkspaceService.create(p, Progress.NONE);
        MinecraftWorlds.checkIn(p, Progress.NONE);
        Path saves = AppPaths.minecraftSaves();
        assertTrue(Files.exists(saves.resolve("Start/level.dat")));

        // Kind baut eine neue Welt – dann stürzt der Rechner ab (kein checkOut).
        Files.createDirectories(saves.resolve("NeueWelt"));
        Files.writeString(saves.resolve("NeueWelt/level.dat"), "neu");
        assertTrue(MinecraftWorlds.hasCheckedOutWorlds());

        // Nächster Start räumt auf:
        MinecraftWorlds.checkOut(Progress.NONE);
        assertTrue(Files.exists(p.workDir().resolve("saves/Welten/NeueWelt/level.dat")));
        try (Stream<Path> s = Files.list(saves)) {
            assertTrue(s.findAny().isEmpty());
        }
        assertFalse(MinecraftWorlds.hasCheckedOutWorlds());
    }

    private static String esc(Path p) {
        return p.toString().replace("\\", "\\\\");
    }

    private static void wipe(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                p.toFile().setWritable(true);
                p.toFile().delete();
            });
        }
    }
}
