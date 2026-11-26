package com.forscherfreunde.hub.model;

import com.forscherfreunde.hub.AppPaths;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Liste der Programme. Liegt als {@code shared/programme.json} im Datenordner, damit Betreuer:innen
 * Pfade und Programme ohne neue Hub-Version anpassen können. Fehlt die Datei, wird die Standardliste
 * dort angelegt.
 */
public final class ProgramCatalog {

    private static final Gson GSON = new Gson();

    private static final class FileFormat {
        List<Program> programme;
    }

    private final List<Program> programs;
    private final String problem;

    private ProgramCatalog(List<Program> programs, String problem) {
        this.programs = programs;
        this.problem = problem;
    }

    public static Path file() { return AppPaths.root().resolve("shared").resolve("programme.json"); }

    public static ProgramCatalog load() {
        Path file = file();
        String problem = null;
        String json = null;
        if (Files.exists(file)) {
            try {
                json = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                problem = "programme.json konnte nicht gelesen werden: " + e.getMessage();
            }
        }
        if (json == null) {
            json = defaults();
            if (problem == null) {
                try {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, json, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    // dann eben nur die eingebaute Liste
                }
            }
        }
        try {
            return new ProgramCatalog(parse(json), problem);
        } catch (JsonParseException | IllegalStateException e) {
            return new ProgramCatalog(parse(defaults()),
                    "programme.json enthält einen Fehler und wird ignoriert: " + e.getMessage());
        }
    }

    static List<Program> parse(String json) {
        FileFormat f = GSON.fromJson(json, FileFormat.class);
        List<Program> out = new ArrayList<>();
        if (f != null && f.programme != null) {
            for (Program p : f.programme) if (p != null && p.isValid()) out.add(p);
        }
        return out;
    }

    static String defaults() {
        try (InputStream in = ProgramCatalog.class.getResourceAsStream("/programme-standard.json")) {
            return in == null ? "{\"programme\":[]}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{\"programme\":[]}";
        }
    }

    public List<Program> forProfile(UserProfile p) {
        List<Program> out = new ArrayList<>();
        for (Program prog : programs) if (prog.isForCamp(p.getModuleName())) out.add(prog);
        return out;
    }

    public List<Program> all() { return List.copyOf(programs); }

    /** Fehlermeldung zur Datei oder {@code null}. */
    public String problem() { return problem; }
}
