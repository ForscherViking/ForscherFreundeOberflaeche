package com.forscherfreunde.hub.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Kurze Hinweis-Blase am unteren Fensterrand, die von selbst wieder verschwindet. */
final class Toast extends JComponent {

    private final Anim.Value fade = new Anim.Value(this, 0f);
    private final Timer hideTimer;
    private String text = "";
    private Icons.Type icon = Icons.Type.CHECK;
    private Color color = Theme.ACCENT;

    Toast() {
        setOpaque(false);
        setVisible(false);
        setFont(Theme.font(Font.BOLD, 14.5f));
        hideTimer = new Timer(2600, e -> fade.animateTo(0f, 220, 0, () -> setVisible(false)));
        hideTimer.setRepeats(false);
    }

    void show(String message, Icons.Type type, Color c) {
        text = message;
        icon = type;
        color = c;
        relayout();
        setVisible(true);
        fade.animateTo(1f, 180);
        hideTimer.restart();
    }

    void relayout() {
        if (getParent() == null) return;
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(text) + 76;
        int h = 48;
        int pw = getParent().getWidth();
        int ph = getParent().getHeight();
        setBounds((pw - w) / 2, ph - h - 28, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        float a = fade.get();
        if (a <= 0f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.quality(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(a));
        g2.translate(0, Math.round((1 - a) * 10));
        int w = getWidth();
        int h = getHeight();
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRoundRect(2, 4, w - 4, h - 4, h, h);
        g2.setColor(Theme.SURFACE_HI);
        g2.fillRoundRect(0, 0, w - 2, h - 4, h - 4, h - 4);
        g2.setColor(Theme.alpha(color, 0.6f));
        g2.drawRoundRect(0, 0, w - 3, h - 5, h - 4, h - 4);
        Icons.paint(g2, icon, 18, (h - 4 - 20) / 2.0, 20, color, 2.4f);
        g2.setColor(Theme.TEXT);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, 50, (h - 4 - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
