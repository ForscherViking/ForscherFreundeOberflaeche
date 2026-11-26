package com.forscherfreunde.hub.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dateinamen-Regeln für Dateien der Kinder.
 *
 * <p><b>Streng</b> (z. B. Paint.NET-Bilder, die als Minecraft-Texturen in IntelliJ landen):
 * nur Kleinbuchstaben, Ziffern und {@code _}, beginnend mit einem Buchstaben – so wie Minecraft
 * Textur- und Item-Namen verlangt ({@code mein_schwert}).
 */
public final class FileNames {

    public static final int MAX_LENGTH = 40;
    private static final Pattern STRICT = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern INVALID_WINDOWS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private FileNames() { }

    /** @return Fehlermeldung für die Oberfläche oder {@code null}, wenn der Name passt. Ohne Dateiendung. */
    public static String validate(String name, boolean strict) {
        if (name == null || name.isEmpty()) return "Bitte gib einen Namen ein.";
        if (name.length() > MAX_LENGTH) return "Der Name ist zu lang (höchstens " + MAX_LENGTH + " Zeichen).";
        if (strict) {
            if (name.contains(" ")) return "Bitte keine Leerzeichen – nimm stattdessen _ (Unterstrich).";
            if (!name.equals(name.toLowerCase(Locale.ROOT))) return "Bitte nur Kleinbuchstaben verwenden.";
            if (!Character.isLetter(name.charAt(0)) || name.charAt(0) > 'z') return "Der Name muss mit einem Buchstaben (a–z) beginnen.";
            if (!STRICT.matcher(name).matches()) return "Erlaubt sind nur a–z, Ziffern und _ (keine Umlaute, Bindestriche oder Punkte).";
            return null;
        }
        if (INVALID_WINDOWS.matcher(name).find()) return "Bitte keine Sonderzeichen wie / \\ : * ? \" < > | verwenden.";
        if (name.startsWith(".") || name.endsWith(".") || name.endsWith(" ")) return "Der Name darf nicht mit Punkt oder Leerzeichen enden.";
        return null;
    }

    /** Macht aus einer Eingabe einen gültigen strengen Namen: „Mein Schwert!“ → „mein_schwert“. */
    public static String suggestStrict(String raw) {
        if (raw == null) return "";
        String s = raw.strip().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[\\s\\-.]+", "_").replaceAll("[^a-z0-9_]", "").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (!s.isEmpty() && !Character.isLetter(s.charAt(0))) s = "bild_" + s;
        if (s.length() > MAX_LENGTH) s = s.substring(0, MAX_LENGTH).replaceAll("_+$", "");
        return s;
    }
}
