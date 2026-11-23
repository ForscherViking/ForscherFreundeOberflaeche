package com.forscherfreunde.hub.update;

import java.util.Arrays;

/** Versionsnummer wie {@code 2.0.1} (ein optionales führendes {@code v} wird ignoriert). */
public final class Version implements Comparable<Version> {

    private final int[] parts;
    private final String text;

    private Version(int[] parts) {
        this.parts = parts;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        this.text = sb.toString();
    }

    /** @throws IllegalArgumentException bei ungültigem Format */
    public static Version parse(String s) {
        if (s == null) throw new IllegalArgumentException("Version fehlt");
        String t = s.strip();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        if (!t.matches("\\d{1,6}(\\.\\d{1,6}){0,3}")) throw new IllegalArgumentException("Ungültige Version: " + s);
        String[] p = t.split("\\.");
        int[] parts = new int[p.length];
        for (int i = 0; i < p.length; i++) parts[i] = Integer.parseInt(p[i]);
        return new Version(parts);
    }

    public boolean isNewerThan(Version other) { return compareTo(other) > 0; }

    @Override
    public int compareTo(Version o) {
        int n = Math.max(parts.length, o.parts.length);
        for (int i = 0; i < n; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < o.parts.length ? o.parts[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        int[] p = parts;
        int end = p.length;
        while (end > 1 && p[end - 1] == 0) end--;
        return Arrays.hashCode(Arrays.copyOf(p, end));
    }

    @Override
    public String toString() { return text; }
}
