package com.forscherfreunde.hub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceTest {

    @Test
    void secondInstanceIsRejectedAndActivatesFirst(@TempDir Path dir) throws Exception {
        SingleInstance first = SingleInstance.acquire(dir);
        assertNotNull(first);
        assertTrue(first.isLocked());
        CountDownLatch activated = new CountDownLatch(1);
        first.setOnActivate(activated::countDown);

        // Innerhalb derselben JVM schlägt tryLock mit OverlappingFileLockException fehl;
        // deshalb wird ein echter zweiter Prozess gestartet.
        Process second = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                SecondInstanceMain.class.getName(), dir.toString())
                .inheritIO().start();
        assertTrue(second.waitFor(20, TimeUnit.SECONDS));
        assertTrue(second.exitValue() == 0, "Zweite Instanz sollte die Sperre nicht bekommen");
        assertTrue(activated.await(5, TimeUnit.SECONDS), "Erste Instanz sollte benachrichtigt werden");
    }

    /** Hilfsprogramm für den zweiten Prozess: Exit 0 = korrekt abgewiesen. */
    public static final class SecondInstanceMain {
        public static void main(String[] args) {
            SingleInstance s = SingleInstance.acquire(Path.of(args[0]));
            assertNull(s);
            System.exit(s == null ? 0 : 1);
        }
    }
}
