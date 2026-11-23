package com.forscherfreunde.hub.service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Schnelle, robuste Datei-Hilfen (Kopieren/Löschen ganzer Ordner mit Fortschritt). */
public final class FileOps {

    private FileOps() { }

    public static long countFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    public static void copyTree(Path src, Path dst, Progress progress, String label) throws IOException {
        copyTree(src, dst, p -> true, progress, label);
    }

    /**
     * Kopiert {@code src} rekursiv nach {@code dst} (bestehende Dateien werden überschrieben).
     * Einzelne gesperrte Dateien brechen den Vorgang nicht ab; der erste Fehler wird am Ende geworfen.
     */
    public static void copyTree(Path src, Path dst, Predicate<Path> include, Progress progress, String label)
            throws IOException {
        long total = Math.max(1, countFiles(src));
        long[] done = {0};
        IOException[] firstError = {null};
        progress.update(label, 0);
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(src) && !include.test(dir)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!include.test(file)) return FileVisitResult.CONTINUE;
                Path target = dst.resolve(src.relativize(file).toString());
                try {
                    makeWritable(target);
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    if (firstError[0] == null) firstError[0] = e;
                }
                progress.update(label, ++done[0] / (double) total);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                if (firstError[0] == null) firstError[0] = e;
                return FileVisitResult.CONTINUE;
            }
        });
        progress.update(label, 1);
        if (firstError[0] != null) throw firstError[0];
    }

    /** Löscht einen Ordner rekursiv. Schreibgeschützte Dateien (z. B. aus .git) werden mitgelöscht. */
    public static void deleteTree(Path dir, Progress progress, String label) throws IOException {
        if (!Files.exists(dir)) return;
        long total = Math.max(1, countFiles(dir));
        long[] done = {0};
        IOException[] firstError = {null};
        progress.update(label, 0);
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    deleteWithRetry(file);
                } catch (IOException e) {
                    if (firstError[0] == null) firstError[0] = e;
                }
                progress.update(label, ++done[0] / (double) total);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                if (firstError[0] == null && !(e instanceof NoSuchFileException)) firstError[0] = e;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) {
                try {
                    deleteWithRetry(d);
                } catch (IOException ex) {
                    if (firstError[0] == null) firstError[0] = ex;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        progress.update(label, 1);
        if (firstError[0] != null) throw firstError[0];
    }

    /** Leert einen Ordner, lässt ihn selbst aber bestehen. */
    public static void clearDirectory(Path dir, Predicate<Path> keep) throws IOException {
        if (!Files.isDirectory(dir)) return;
        IOException first = null;
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (keep.test(child)) continue;
                try {
                    if (Files.isDirectory(child)) deleteTree(child, Progress.NONE, "");
                    else deleteWithRetry(child);
                } catch (IOException e) {
                    if (first == null) first = e;
                }
            }
        }
        if (first != null) throw first;
    }

    private static void deleteWithRetry(Path p) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                Files.deleteIfExists(p);
                return;
            } catch (AccessDeniedException e) {
                last = e;
                makeWritable(p);
            } catch (DirectoryNotEmptyException e) {
                last = e;
            }
            try {
                Thread.sleep(80L * (attempt + 1)); // Windows gibt Dateien manchmal verzögert frei
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new IOException("Konnte nicht löschen: " + p);
    }

    private static void makeWritable(Path p) {
        if (!Files.exists(p)) return;
        try {
            Files.setAttribute(p, "dos:readonly", false);
        } catch (UnsupportedOperationException | IOException | IllegalArgumentException ignored) {
            p.toFile().setWritable(true);
        }
    }
}
