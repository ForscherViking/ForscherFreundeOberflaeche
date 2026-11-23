package com.forscherfreunde.hub.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Zeigt immer genau einen Bildschirm an und blendet beim Wechsel weich über.
 *
 * <p>Statt – wie früher – für jede Ansicht ein neues Fenster zu öffnen, gibt es nur noch ein
 * Fenster. Für die Überblendung wird der alte Bildschirm einmalig als Bild festgehalten; pro
 * Animationsschritt fallen dadurch nur zwei günstige Zeichenoperationen an.
 */
public final class ScreenHost extends JPanel {

    private final Anim.Value progress = new Anim.Value(this, 1f);
    private BufferedImage outgoing;
    private Screen current;

    public ScreenHost() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
        setFocusable(true); // hält den Fokus, damit nicht zufällig die erste Karte „ausgewählt“ aussieht
    }

    public Screen current() { return current; }

    public void show(Screen next) {
        if (current != null && isShowing() && Anim.enabled() && getWidth() > 0) {
            outgoing = snapshot(current);
        }
        if (current != null) remove(current);
        current = next;
        add(next, BorderLayout.CENTER);
        revalidate();
        requestFocusInWindow();
        if (outgoing != null) {
            progress.set(0f);
            progress.animateTo(1f, 230, 0, () -> {
                outgoing = null;
                repaint();
            });
        } else {
            repaint();
        }
    }

    private BufferedImage snapshot(JComponent c) {
        AffineTransform tx = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getDefaultTransform() : new AffineTransform();
        int w = (int) Math.ceil(getWidth() * tx.getScaleX());
        int h = (int) Math.ceil(getHeight() * tx.getScaleY());
        BufferedImage img = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().createCompatibleImage(w, h)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(tx.getScaleX(), tx.getScaleY());
        paintBackdrop(g);
        g.translate(c.getX(), c.getY());
        c.paint(g);
        g.dispose();
        return img;
    }

    private void paintBackdrop(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, Math.max(1, getHeight()), Theme.BG));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintBackdrop((Graphics2D) g);
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (outgoing == null) {
            super.paintChildren(g);
            return;
        }
        float t = progress.get();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(t));
        g2.translate(0, Math.round((1f - t) * 18f));
        super.paintChildren(g2);
        g2.dispose();

        Graphics2D g3 = (Graphics2D) g.create();
        g3.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, 1f - t * 1.6f)));
        g3.drawImage(outgoing, 0, 0, getWidth(), getHeight(), null);
        g3.dispose();
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false; // Kinder überlappen während der Überblendung
    }
}
