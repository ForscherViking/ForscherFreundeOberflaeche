package com.forscherfreunde.hub.service;

import com.forscherfreunde.hub.AppPaths;
import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.UserProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/** Anlegen, Löschen, Sichern und Wiederherstellen von Profil-Arbeitsordnern. */
public final class WorkspaceService {

    private WorkspaceService() { }

    public static void create(UserProfile profile, Progress progress) throws IOException {
        Camp module = profile.module();
        if (module == null) throw new IOException("Unbekanntes Modul: " + profile.getModuleName());
        Path work = profile.workDir();
        requireInsideRoot(work);

        Path source;
        Path target;
        if (module.isCode()) {
            source = AppPaths.codeTemplates().resolve(module.id());
            target = work.resolve(module.id());
        } else {
            source = AppPaths.worldTemplates().resolve(module.id());
            target = work.resolve("saves");
        }
        if (!Files.isDirectory(source)) {
            throw new IOException("Die Vorlage für „" + module.title() + "“ wurde nicht gefunden:\n" + source);
        }

        boolean existedBefore = Files.exists(work);
        try {
            Files.createDirectories(work);
            FileOps.copyTree(source, target, progress, "Vorlage wird kopiert …");
        } catch (IOException e) {
            if (!existedBefore) {
                try { FileOps.deleteTree(work, Progress.NONE, ""); } catch (IOException ignored) { }
            }
            throw e;
        }
    }

    public static void delete(UserProfile profile, Progress progress) throws IOException {
        Path work = profile.workDir();
        requireInsideRoot(work);
        FileOps.deleteTree(work, progress, "Profil wird gelöscht …");
        Path backup = profile.backupDir();
        if (Files.exists(backup)) {
            requireInsideRoot(backup);
            FileOps.deleteTree(backup, progress, "Backup wird gelöscht …");
        }
    }

    /** Erstellt ein neues Backup. Das alte Backup wird erst ersetzt, wenn das neue vollständig ist. */
    public static void backup(UserProfile profile, Progress progress) throws IOException {
        Path work = profile.workDir();
        Path backup = profile.backupDir();
        requireInsideRoot(backup);
        if (!Files.isDirectory(work)) throw new IOException("Arbeitsordner nicht gefunden:\n" + work);

        Path tmp = backup.resolveSibling(backup.getFileName() + ".neu");
        FileOps.deleteTree(tmp, Progress.NONE, "");
        Files.createDirectories(tmp.getParent());
        FileOps.copyTree(work, tmp, progress, "Backup wird erstellt …");
        FileOps.deleteTree(backup, Progress.NONE, "");
        Files.move(tmp, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.from(Instant.now()));
    }

    public static void restore(UserProfile profile, Progress progress) throws IOException {
        Path backup = profile.backupDir();
        if (!Files.isDirectory(backup)) throw new IOException("Für dieses Profil gibt es noch kein Backup.");
        Path modulePath = profile.modulePath();
        requireInsideRoot(modulePath);
        FileOps.deleteTree(modulePath, progress, "Aktueller Stand wird entfernt …");
        FileOps.copyTree(backup, profile.workDir(), progress, "Backup wird wiederhergestellt …");
    }

    /** Zeitpunkt des letzten Backups oder {@code null}. */
    public static Instant lastBackup(UserProfile profile) {
        try {
            Path b = profile.backupDir();
            return Files.isDirectory(b) ? Files.getLastModifiedTime(b).toInstant() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void requireInsideRoot(Path p) throws IOException {
        if (!AppPaths.isInsideRoot(p)) {
            throw new IOException("Aus Sicherheitsgründen abgebrochen – Pfad liegt außerhalb des Datenordners:\n" + p);
        }
    }
}
