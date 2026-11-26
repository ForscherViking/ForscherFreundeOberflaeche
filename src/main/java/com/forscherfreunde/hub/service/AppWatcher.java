package com.forscherfreunde.hub.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Beobachtet ein externes Programm: meldet, sobald es läuft, und sobald es wieder geschlossen wurde.
 * Es gibt immer höchstens eine Beobachtung; gepollt wird nur, solange sie aktiv ist.
 * Die Callbacks laufen auf einem Hintergrund-Thread.
 */
public final class AppWatcher {

    /** Ergebnis, wenn die Beobachtung endet. */
    public enum End {
        /** Das Programm lief und wurde geschlossen. */
        CLOSED,
        /** Das Programm ist innerhalb der Wartezeit nie aufgetaucht. */
        NEVER_STARTED
    }

    public interface Listener {
        void started();
        void ended(End how);
    }

    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ffhub-app-watch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** So oft hintereinander „nicht mehr gefunden“, bevor es als geschlossen gilt (überbrückt z. B. Launcher → Spiel). */
    private static final int REQUIRED_MISSES = 2;

    private static ScheduledFuture<?> current;

    private AppWatcher() { }

    public static void watch(WatchedProgram app, long startTimeoutMillis, Listener listener) {
        watch(app::isRunning, startTimeoutMillis, 2000, listener);
    }

    static synchronized void watch(BooleanSupplier isRunning, long startTimeoutMillis, long pollMillis, Listener listener) {
        cancel();
        long deadline = System.currentTimeMillis() + startTimeoutMillis;
        boolean[] seen = {false};
        int[] misses = {0};
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        Runnable check = () -> {
            boolean running;
            try {
                running = isRunning.getAsBoolean();
            } catch (RuntimeException e) {
                return; // nächster Versuch
            }
            if (!seen[0]) {
                if (running) {
                    seen[0] = true;
                    listener.started();
                } else if (System.currentTimeMillis() > deadline) {
                    stop(self[0]);
                    listener.ended(End.NEVER_STARTED);
                }
                return;
            }
            if (running) {
                misses[0] = 0;
            } else if (++misses[0] >= REQUIRED_MISSES) {
                stop(self[0]);
                listener.ended(End.CLOSED);
            }
        };
        self[0] = EXEC.scheduleWithFixedDelay(check, 0, pollMillis, TimeUnit.MILLISECONDS);
        current = self[0];
    }

    /** Beendet eine laufende Beobachtung ohne Callback. */
    public static synchronized void cancel() {
        if (current != null) current.cancel(false);
        current = null;
    }

    private static synchronized void stop(ScheduledFuture<?> f) {
        if (f != null) f.cancel(false);
        if (current == f) current = null;
    }
}
