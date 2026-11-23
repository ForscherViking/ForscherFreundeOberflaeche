package com.forscherfreunde.hub.model;

import com.forscherfreunde.hub.AppPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Lädt und speichert die Profilliste (profiles.json). */
public final class ProfileStore {

    public static final int MAX_PROFILES = 6;
    public static final int MAX_NAME_LENGTH = 24;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern RESERVED = Pattern.compile(
            "(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9]|shared|hub)$");

    private final List<UserProfile> profiles = new ArrayList<>();

    public static ProfileStore load() throws IOException {
        ProfileStore store = new ProfileStore();
        Path file = AppPaths.profilesFile();
        if (!Files.exists(file)) return store;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<UserProfile> list = GSON.fromJson(r, new TypeToken<List<UserProfile>>() { }.getType());
            if (list != null) {
                for (UserProfile p : list) {
                    if (p != null && p.getName() != null && p.getModuleName() != null) store.profiles.add(p);
                }
            }
        } catch (JsonParseException e) {
            // Beschädigte Datei sichern statt beim nächsten Speichern zu überschreiben.
            Files.copy(file, file.resolveSibling("profiles.json.defekt"), StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("profiles.json ist beschädigt und wurde als profiles.json.defekt gesichert.", e);
        }
        return store;
    }

    public List<UserProfile> all() { return Collections.unmodifiableList(profiles); }

    public boolean isFull() { return profiles.size() >= MAX_PROFILES; }

    public void add(UserProfile p) throws IOException {
        profiles.add(p);
        try {
            save();
        } catch (IOException e) {
            profiles.remove(p);
            throw e;
        }
    }

    public void remove(UserProfile p) throws IOException {
        profiles.remove(p);
        save();
    }

    /** Atomar speichern: erst in eine temporäre Datei schreiben, dann ersetzen. */
    public void save() throws IOException {
        Path file = AppPaths.profilesFile();
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("profiles.json.tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(profiles, w);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** @return Fehlermeldung für die Oberfläche oder {@code null}, wenn der Name gültig ist. */
    public String validateName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) return "Bitte gib einen Namen ein.";
        if (name.length() > MAX_NAME_LENGTH) return "Der Name ist zu lang (max. " + MAX_NAME_LENGTH + " Zeichen).";
        if (INVALID_CHARS.matcher(name).find()) return "Bitte keine Sonderzeichen wie / \\ : * ? \" < > | verwenden.";
        if (name.startsWith(".") || name.endsWith(".")) return "Der Name darf nicht mit einem Punkt beginnen oder enden.";
        if (RESERVED.matcher(name).matches()) return "Dieser Name ist leider reserviert.";
        String lower = name.toLowerCase(Locale.ROOT);
        for (UserProfile p : profiles) {
            if (p.getName().toLowerCase(Locale.ROOT).equals(lower)) return "Es gibt schon ein Profil mit diesem Namen.";
        }
        return null;
    }
}
