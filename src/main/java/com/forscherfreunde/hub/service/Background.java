package com.forscherfreunde.hub.service;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Ein einziger Arbeits-Thread für alle Dateioperationen. Ein Thread genügt: mehrere parallele
 * Kopier-/Löschvorgänge würden auf langsamen Festplatten nur gegeneinander arbeiten.
 */
public final class Background {

    @FunctionalInterface
    public interface Task<T> {
        T run() throws Exception;
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ffhub-io");
        t.setDaemon(true);
        return t;
    });

    private Background() { }

    /** Führt {@code task} im Hintergrund aus und meldet das Ergebnis auf dem Swing-Thread. */
    public static <T> void run(Task<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, IO).whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                cause.printStackTrace();
                if (onError != null) onError.accept(cause);
            } else if (onSuccess != null) {
                onSuccess.accept(result);
            }
        }));
    }

    /** Wrappt einen Fortschritts-Callback so, dass er auf dem Swing-Thread und höchstens ~20x/s ausgeführt wird. */
    public static Progress onEdt(Progress target) {
        return new Progress() {
            private long last;

            @Override
            public void update(String message, double fraction) {
                long now = System.nanoTime();
                boolean important = fraction < 0 || fraction >= 1;
                if (!important && now - last < 50_000_000L) return;
                last = now;
                SwingUtilities.invokeLater(() -> target.update(message, fraction));
            }
        };
    }
}
