package com.forscherfreunde.hub.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Kachel „Neues Profil“ mit gestricheltem Rahmen. */
public final class AddProfileCard extends HoverCard {

    public AddProfileCard(Runnable onClick) {
        setPreferredSize(new Dimension(ProfileCard.WIDTH, ProfileCard.HEIGHT));
        setOnClick(onClick);
        setToolTipText("Ein neues Profil anlegen");
        getAccessibleContext().setAccessibleName("Neues Profil anlegen");
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.quality(g2);
        float hv = hover.get();
        int lift = Math.round(hv * 3 - press.get() * 2);
        g2.translate(0, -lift);
        int x = 4;
        int y = 4;
        int w = getWidth() - 8;
        int h = getHeight() - 10;
        g2.setColor(Theme.alpha(Theme.ACCENT, 0.03f + 0.07f * hv));
        g2.fillRoundRect(x, y, w, h, 22, 22);
        g2.setColor(Theme.mix(Theme.BORDER, Theme.ACCENT, 0.25f + 0.6f * hv));
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {7f, 6f}, 0f));
        g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 22, 22);
        if (isFocusOwner()) {
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(Theme.ACCENT);
            g2.drawRoundRect(x - 3, y - 3, w + 5, h + 5, 28, 28);
        }

        int d = 54;
        int cx = x + w / 2;
        int cy = y + h / 2 - 16;
        g2.setColor(Theme.mix(Theme.SURFACE_HI, Theme.ACCENT, 0.15f + 0.85f * hv));
        g2.fillOval(cx - d / 2, cy - d / 2, d, d);
        Icons.paint(g2, Icons.Type.PLUS, cx - 13, cy - 13, 26, hv > 0.5f ? Theme.ON_ACCENT : Theme.ACCENT, 2.6f);

        g2.setFont(Theme.font(Font.BOLD, 16f));
        FontMetrics fm = g2.getFontMetrics();
        String t = "Neues Profil";
        g2.setColor(Theme.mix(Theme.MUTED, Theme.TEXT, hv));
        g2.drawString(t, cx - fm.stringWidth(t) / 2, cy + d / 2 + 18 + fm.getAscent());
        g2.dispose();
    }
}
