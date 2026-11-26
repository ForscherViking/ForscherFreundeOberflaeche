package com.forscherfreunde.hub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileNamesTest {

    @Test
    void strictRuleMatchesMinecraftTextureNames() {
        assertNull(FileNames.validate("mein_schwert", true));
        assertNull(FileNames.validate("block2", true));
        assertNotNull(FileNames.validate("Mein_Schwert", true), "Großbuchstaben");
        assertNotNull(FileNames.validate("mein schwert", true), "Leerzeichen");
        assertNotNull(FileNames.validate("mein-schwert", true), "Bindestrich");
        assertNotNull(FileNames.validate("schwört", true), "Umlaut");
        assertNotNull(FileNames.validate("2schwert", true), "beginnt mit Ziffer");
        assertNotNull(FileNames.validate("_schwert", true), "beginnt mit _");
        assertNotNull(FileNames.validate("", true));
        assertNotNull(FileNames.validate("a".repeat(41), true));
    }

    @Test
    void suggestionsAreValid() {
        assertEquals("mein_schwert", FileNames.suggestStrict("Mein Schwert!"));
        assertEquals("gruenes_schoene_blume", FileNames.suggestStrict("Grünes  schöne-Blume"));
        assertEquals("strasse", FileNames.suggestStrict("Straße"));
        assertEquals("bild_3d", FileNames.suggestStrict("3D"));
        assertEquals("cafe", FileNames.suggestStrict("Café"));
        for (String s : new String[] {"Mein Schwert!", "Grünes  schöne-Blume", "3D", "__x__", "Bild 1"}) {
            assertNull(FileNames.validate(FileNames.suggestStrict(s), true), s);
        }
    }

    @Test
    void lenientRuleOnlyBlocksWindowsCharacters() {
        assertNull(FileNames.validate("Mein Bild 1", false));
        assertNotNull(FileNames.validate("a/b", false));
        assertNotNull(FileNames.validate("x.", false));
    }
}
