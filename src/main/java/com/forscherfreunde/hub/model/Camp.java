package com.forscherfreunde.hub.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Alle verfügbaren Camps. Die {@link #id()} entspricht dem Ordnernamen und dem Wert in profiles.json. */
public enum Camp {
    MODS("CampMinecraftMods", "Camp Minecraft Mods", "Eigene Mods programmieren", Kind.CODE,
            "CampMinecraftMods.jpg", new Color(0x7BD389)),
    AI("CampMinecraftAI", "Camp Minecraft AI", "Künstliche Intelligenz entdecken", Kind.CODE,
            "CampMinecraftAI.jpg", new Color(0x3DD6E0)),
    STADT("Stadt der Zukunft", "Stadt der Zukunft", "Bauen, planen, gestalten", Kind.MINECRAFT,
            "Stadt_der_Zukunft.jpg", new Color(0x5AA9FF)),
    MASCHINEN("Komplexe Maschinen", "Komplexe Maschinen", "Redstone & Mechanik", Kind.MINECRAFT,
            "Komplexe_Maschinen.jpg", new Color(0xFF6B5B)),
    DADDELN("Daddeln und Denken", "Daddeln und Denken", "Spielen und knobeln", Kind.MINECRAFT,
            "Daddeln_und_Denken.jpg", new Color(0xFFC94A)),
    LABOR("Zukunftslabor", "Zukunftslabor", "Experimente der Zukunft", Kind.MINECRAFT,
            "Zukunftslabor.jpg", new Color(0xFF9F43));

    public enum Kind { CODE, MINECRAFT }

    private final String id;
    private final String title;
    private final String tagline;
    private final Kind kind;
    private final String image;
    private final Color accent;

    Camp(String id, String title, String tagline, Kind kind, String image, Color accent) {
        this.id = id;
        this.title = title;
        this.tagline = tagline;
        this.kind = kind;
        this.image = image;
        this.accent = accent;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String tagline() { return tagline; }
    public Kind kind() { return kind; }
    public boolean isCode() { return kind == Kind.CODE; }
    /** Klassenpfad-Name des Bildes unter {@code /img/}. */
    public String image() { return image; }
    public Color accent() { return accent; }
    /** Seitenverhältnis (Breite/Höhe) des Banners: Code-Camps hochkant 2:3, Minecraft-Camp quadratisch. */
    public double imageAspect() { return isCode() ? 2.0 / 3.0 : 1.0; }

    public static Camp byId(String id) {
        if (id == null) return null;
        for (Camp m : values()) {
            if (m.id.equalsIgnoreCase(id.trim())) return m;
        }
        return null;
    }

    public static List<Camp> ofKind(Kind kind) {
        List<Camp> list = new ArrayList<>();
        for (Camp m : values()) if (m.kind == kind) list.add(m);
        return list;
    }
}
