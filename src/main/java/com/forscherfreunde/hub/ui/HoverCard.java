package com.forscherfreunde.hub.ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Basis für klickbare Karten mit Hover-, Klick- und Einblend-Animation.
 *
 * <p>Ein Klick zählt, wenn die Maus <em>auf der Karte gedrückt und losgelassen</em> wird – auch wenn
 * sie sich dazwischen leicht bewegt hat. (Das alte {@code mouseClicked} feuerte bei der kleinsten
 * Mausbewegung nicht, deshalb funktionierte die Auswahl „nicht immer sauber“.)
 */
public abstract class HoverCard extends JPanel {

    protected final Anim.Value hover = new Anim.Value(this, 0f);
    protected final Anim.Value press = new Anim.Value(this, 0f);
    private final Anim.Value appear = new Anim.Value(this, 1f);
    private Runnable onClick = () -> { };
    private boolean pressed;

    private final MouseListener hoverTracker = new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { hover.animateTo(1f, 140); }

        @Override public void mouseExited(MouseEvent e) {
            if (getMousePosition(true) == null) hover.animateTo(0f, 200);
        }
    };

    protected HoverCard() {
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(hoverTracker);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || !isEnabled()) return;
                pressed = true;
                press.animateTo(1f, 90);
            }

            @Override public void mouseReleased(MouseEvent e) {
                boolean wasPressed = pressed;
                pressed = false;
                press.animateTo(0f, 160);
                if (wasPressed && contains(e.getPoint()) && isEnabled()) onClick.run();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }
            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "click");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "click");
        getActionMap().put("click", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (isEnabled()) onClick.run(); }
        });
    }

    public void setOnClick(Runnable r) { onClick = r == null ? () -> { } : r; }

    /** Kind-Komponenten (z. B. Buttons) sollen den Hover-Zustand der Karte nicht unterbrechen. */
    protected void trackHover(JComponent child) { child.addMouseListener(hoverTracker); }

    /** Eingangsanimation, leicht zeitversetzt pro Karte. */
    public void playAppear(int delayMillis) {
        if (!Anim.enabled()) return;
        appear.set(0f);
        appear.animateTo(1f, 300, delayMillis, null);
    }

    @Override
    public void paint(Graphics g) {
        float a = appear.get();
        if (a >= 1f) {
            super.paint(g);
            return;
        }
        if (a <= 0f) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(a));
        g2.translate(0, Math.round((1f - a) * 16f));
        super.paint(g2);
        g2.dispose();
    }

    /** Zeichnet Schatten, Fläche, Rahmen und ggf. Fokusring einer Karte. */
    protected void paintChrome(Graphics2D g, int x, int y, int w, int h, int arc, Color accent, float highlight) {
        float hv = hover.get();
        // weicher Schatten aus wenigen halbtransparenten Ebenen – viel günstiger als ein Weichzeichner
        for (int i = 0; i < 4; i++) {
            int spread = 2 + i * 3;
            float alpha = (0.10f + 0.08f * hv) * (1f - i / 4f);
            g.setColor(new Color(0, 0, 0, Math.round(alpha * 255)));
            g.fillRoundRect(x - spread / 2, y + 2 + spread / 2 + Math.round(hv * 3), w + spread, h + spread / 2, arc + spread, arc + spread);
        }
        g.setColor(Theme.mix(Theme.SURFACE, Theme.SURFACE_HI, hv * 0.8f + highlight * 0.4f));
        g.fillRoundRect(x, y, w, h, arc, arc);
        Color border = Theme.mix(Theme.BORDER, accent, Math.max(hv * 0.55f, highlight));
        g.setColor(border);
        g.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
        if (isFocusOwner()) {
            g.setColor(Theme.alpha(Theme.ACCENT, 0.9f));
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(x - 3, y - 3, w + 5, h + 5, arc + 6, arc + 6);
        }
    }

    /** Text mit „…“ kürzen, damit er in {@code maxWidth} passt. */
    protected static String ellipsize(String text, java.awt.FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > maxWidth) end--;
        return text.substring(0, end) + dots;
    }
}
