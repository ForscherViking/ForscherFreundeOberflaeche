package com.forscherfreunde.hub.ui;

import com.forscherfreunde.hub.model.Camp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Auswahlkarte für ein Camp/Modul. Die Karte ist immer exakt so groß wie das Banner (plus Rand und
 * Beschriftung) – siehe {@link ModuleGridLayout}. Dadurch wird nichts verzerrt oder abgeschnitten,
 * und die anklickbare Fläche entspricht genau dem sichtbaren Bild.
 */
public final class ModuleCard extends HoverCard {

    /** Rand um das Bild (Platz für Auswahlring und Schatten). */
    public static final int PAD = 10;
    /** Höhe der Beschriftung unter dem Bild. */
    public static final int CAPTION = 54;
    private static final int ARC = 20;

    private final Camp module;
    private final FittedImage image = new FittedImage(this, ARC, 0.5f);
    private final Anim.Value selected = new Anim.Value(this, 0f);
    private final Anim.Value dim = new Anim.Value(this, 0f);

    public ModuleCard(Camp module) {
        this.module = module;
        setToolTipText(module.title() + " – " + module.tagline());
        getAccessibleContext().setAccessibleName(module.title());
        Images.load(module.image(), image::setSource);
    }

    public Camp module() { return module; }

    public boolean isSelected() { return selected.target() >= 1f; }

    /** @param selected ist diese Karte ausgewählt? @param other ist eine andere Karte ausgewählt? */
    public void setState(boolean selected, boolean other) {
        this.selected.animateTo(selected ? 1f : 0f, 200);
        this.dim.animateTo(other && !selected ? 1f : 0f, 240);
        getAccessibleContext().setAccessibleDescription(selected ? "ausgewählt" : null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.quality(g2);
        float hv = hover.get();
        float sel = selected.get();
        float dm = dim.get() * (1f - hv * 0.7f);
        int lift = Math.round(Math.max(hv, sel) * 4 - press.get() * 3);

        int ix = PAD;
        int iy = PAD - lift;
        int iw = getWidth() - 2 * PAD;
        int ih = getHeight() - 2 * PAD - CAPTION;
        Color accent = module.accent();

        // Schatten + Glühen in Modulfarbe
        for (int i = 0; i < 4; i++) {
            int s = 2 + i * 3;
            float a = (0.16f + 0.10f * hv) * (1f - i / 4f);
            g2.setColor(new Color(0, 0, 0, Math.round(a * 255)));
            g2.fillRoundRect(ix - s / 2, iy + 3 + s / 2 + lift, iw + s, ih + s / 2, ARC + s, ARC + s);
        }
        if (sel > 0f) {
            for (int i = 1; i <= 3; i++) {
                g2.setColor(Theme.alpha(accent, 0.10f * sel * (4 - i) / 3f));
                g2.setStroke(new BasicStroke(i * 3f));
                g2.drawRoundRect(ix - 4, iy - 4, iw + 7, ih + 7, ARC + 8, ARC + 8);
            }
        }

        if (image.hasImage()) {
            image.paint(g2, ix, iy, iw, ih);
        } else {
            g2.setColor(Theme.SURFACE_HI);
            g2.fillRoundRect(ix, iy, iw, ih, ARC, ARC);
        }
        if (dm > 0.01f) {
            g2.setColor(Theme.alpha(Theme.BG, 0.55f * dm));
            g2.fillRoundRect(ix, iy, iw, ih, ARC, ARC);
        }

        // Rahmen / Auswahlring
        float ring = Math.max(sel, hv * 0.6f);
        if (ring > 0.01f) {
            g2.setColor(Theme.alpha(sel > 0.01f ? accent : Theme.TEXT, Math.min(1f, ring)));
            g2.setStroke(new BasicStroke(1.5f + 2f * sel));
            g2.drawRoundRect(ix - 3, iy - 3, iw + 5, ih + 5, ARC + 6, ARC + 6);
        }
        if (isFocusOwner()) {
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(ix - 7, iy - 7, iw + 13, ih + 13, ARC + 12, ARC + 12);
        }

        // Häkchen-Plakette
        if (sel > 0.01f) {
            int d = Math.round(36 * (0.6f + 0.4f * sel));
            int cx = ix + iw - 14 - d / 2;
            int cy = iy + 14 + d / 2;
            g2.setColor(new Color(0, 0, 0, Math.round(90 * sel)));
            g2.fillOval(cx - d / 2, cy - d / 2 + 2, d, d);
            g2.setColor(Theme.alpha(accent, sel));
            g2.fillOval(cx - d / 2, cy - d / 2, d, d);
            Icons.paint(g2, Icons.Type.CHECK, cx - d * 0.32, cy - d * 0.32, d * 0.64, Theme.alpha(Theme.ON_ACCENT, sel), 3f);
        }

        // Beschriftung
        int ty = iy + ih + 14 + lift;
        g2.setFont(Theme.font(Font.BOLD, 16f));
        FontMetrics fm = g2.getFontMetrics();
        Color titleColor = Theme.mix(Theme.TEXT, Theme.MUTED, dm * 0.7f);
        g2.setColor(titleColor);
        String t = ellipsize(module.title(), fm, iw);
        g2.drawString(t, ix + (iw - fm.stringWidth(t)) / 2, ty + fm.getAscent());
        g2.setFont(Theme.font(Font.PLAIN, 13f));
        FontMetrics fm2 = g2.getFontMetrics();
        g2.setColor(Theme.mix(Theme.MUTED, Theme.BORDER, dm * 0.6f));
        String sub = ellipsize(module.tagline(), fm2, iw);
        g2.drawString(sub, ix + (iw - fm2.stringWidth(sub)) / 2, ty + fm.getHeight() + fm2.getAscent());
        g2.dispose();
    }
}
