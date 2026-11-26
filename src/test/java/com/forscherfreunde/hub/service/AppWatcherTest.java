package com.forscherfreunde.hub.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AppWatcherTest {

    @Test
    void recognisesProgramPaths() {
        assertTrue(ExternalApp.INTELLIJ.matchesCommand("C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2024.3\\bin\\idea64.exe"));
        assertTrue(ExternalApp.INTELLIJ.matchesCommand("C:\\Users\\kid\\AppData\\Local\\Programs\\IntelliJ IDEA\\bin\\IDEA64.EXE"));
        assertFalse(ExternalApp.INTELLIJ.matchesCommand("C:\\Program Files\\Java\\bin\\java.exe"));
        assertFalse(ExternalApp.INTELLIJ.matchesCommand("C:\\tools\\ideas.exe"));

        assertTrue(ExternalApp.MINECRAFT.matchesCommand("C:\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe"));
        assertTrue(ExternalApp.MINECRAFT.matchesCommand(
                "C:\\Users\\kid\\AppData\\Roaming\\.minecraft\\runtime\\java-runtime-gamma\\windows-x64\\java-runtime-gamma\\bin\\javaw.exe"));
        assertTrue(ExternalApp.MINECRAFT.matchesCommand("C:\\XboxGames\\Minecraft Launcher\\Content\\Minecraft.exe"));
        assertFalse(ExternalApp.MINECRAFT.matchesCommand("C:\\Users\\kid\\AppData\\Local\\ForscherFreundeHub\\jre\\bin\\javaw.exe"));
        assertFalse(ExternalApp.MINECRAFT.matchesCommand("C:\\Program Files\\Java\\jdk-17\\bin\\java.exe"));
    }

    @Test
    void reportsStartAndCloseWithGapTolerance() throws Exception {
        // startet, kurze Lücke (Launcher → Spiel), läuft weiter, wird geschlossen
        boolean[] pattern = {false, true, true, false, true, true, false, false, false};
        AtomicInteger i = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<AppWatcher.End> how = new AtomicReference<>();
        AtomicInteger checksAtEnd = new AtomicInteger();
        AppWatcher.watch(() -> pattern[Math.min(i.getAndIncrement(), pattern.length - 1)], 5000, 20, new AppWatcher.Listener() {
            @Override public void started() { started.countDown(); }
            @Override public void ended(AppWatcher.End h) { how.set(h); checksAtEnd.set(i.get()); ended.countDown(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertEquals(AppWatcher.End.CLOSED, how.get());
        assertEquals(8, checksAtEnd.get(), "eine einzelne Lücke beendet die Beobachtung nicht");
    }

    @Test
    void givesUpWhenProgramNeverStarts() throws Exception {
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<AppWatcher.End> how = new AtomicReference<>();
        AtomicBoolean started = new AtomicBoolean();
        AppWatcher.watch(() -> false, 100, 20, new AppWatcher.Listener() {
            @Override public void started() { started.set(true); }
            @Override public void ended(AppWatcher.End h) { how.set(h); ended.countDown(); }
        });
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertEquals(AppWatcher.End.NEVER_STARTED, how.get());
        assertFalse(started.get());
    }

    /**
     * Prüft die echte Prozesserkennung mit einem gewöhnlichen {@code sleep}-Prozess. (Kein umbenanntes
     * Programm: Auf neueren Linux-Systemen ist {@code sleep} ein Sammelprogramm, das unter fremdem
     * Namen sofort endet.) Die Namensregeln für IntelliJ/Minecraft prüft {@link #recognisesProgramPaths()}.
     */
    @Test
    void detectsRunningProcess() throws Exception {
        Process p;
        try {
            p = new ProcessBuilder("sleep", "30").start();
        } catch (java.io.IOException e) {
            assumeTrue(false, "kein sleep-Programm vorhanden (z. B. Windows)");
            return;
        }
        try {
            String command = waitForCommand(p);
            assumeTrue(command != null, "Programmpfad fremder Prozesse auf diesem System nicht lesbar");
            assertTrue(ExternalApp.anyProcessMatches(command::equals), "laufender Prozess wird erkannt");
            assertFalse(ExternalApp.anyProcessMatches(c -> c.endsWith("gibt-es-nicht-xyz")));
        } finally {
            p.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static String waitForCommand(Process p) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            String c = p.toHandle().info().command().orElse(null);
            if (c != null) return c;
            Thread.sleep(100);
        }
        return null;
    }
}
