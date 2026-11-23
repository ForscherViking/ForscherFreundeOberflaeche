package com.forscherfreunde.hub.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.Map;

/** Farben, Schriften und globale Look-and-Feel-Einstellungen. */
public final class Theme {

    // Nachthimmel-Palette passend zu den Camp-Bannern, Akzent = Minecraft-Gras, Warm = Lagerfeuer.
    public static final Color BG = new Color(0x0E1319);
    public static final Color BG_TOP = new Color(0x131B24);
    public static final Color SURFACE = new Color(0x18212C);
    public static final Color SURFACE_HI = new Color(0x202C3A);
    public static final Color BORDER = new Color(0x2A3747);
    public static final Color TEXT = new Color(0xEEF2F6);
    public static final Color MUTED = new Color(0x92A1B3);
    public static final Color ACCENT = new Color(0x5BC94E);
    public static final Color ACCENT_HI = new Color(0x72D966);
    public static final Color ACCENT_LO = new Color(0x48AE3C);
    public static final Color ON_ACCENT = new Color(0x0A1A07);
    public static final Color WARM = new Color(0xF5A524);
    public static final Color DANGER = new Color(0xEF5A50);

    private static Font base;
    private static Object desktopTextHints;

    private Theme() { }

    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@background", hex(BG),
                "@foreground", hex(TEXT),
                "@accentColor", hex(ACCENT)));
        FlatDarkLaf.setup();

        Font f = UIManager.getFont("defaultFont");
        if (f == null) f = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        base = f.deriveFont(Math.max(14f, f.getSize2D() + 1f));
        UIManager.put("defaultFont", base);

        UIManager.put("Button.arc", 14);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ProgressBar.horizontalSize", new java.awt.Dimension(240, 8));
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollPane.smoothScrolling", true);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("ToolTip.background", SURFACE_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
        UIManager.put("ToolTipManager.enableToolTipMode", "activeApplication");
        UIManager.put("TitlePane.unifiedBackground", true);
        FlatLaf.updateUI();

        desktopTextHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
    }

    public static Font font(int style, float size) {
        Font b = base != null ? base : new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        return b.deriveFont(style, size);
    }

    /** Kantenglättung für Formen und systemkonforme Text-Glättung. */
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (desktopTextHints instanceof Map<?, ?> hints) {
            g.addRenderingHints(hints);
        } else {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    public static Color alpha(Color c, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), al);
    }

    public static Color mix(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
