package com.forscherfreunde.hub.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Hält eine vorberechnete, exakt passende Version eines Bildes (in physischen Pixeln, also auch
 * bei 125 %/150 % Windows-Skalierung scharf). Beim Ziehen der Fenstergröße wird übergangsweise
 * die alte Version gestreckt und erst nach einer kurzen Pause neu berechnet.
 */
public final class FittedImage {

    private final JComponent owner;
    private final float arcLogical;
    private final float anchorY;
    private final Timer debounce;
    private BufferedImage source;
    private BufferedImage cached;
    private int pendingW;
    private int pendingH;
    private float pendingArc;

    public FittedImage(JComponent owner, float arc, float anchorY) {
        this.owner = owner;
        this.arcLogical = arc;
        this.anchorY = anchorY;
        this.debounce = new Timer(140, e -> {
            rebuild(pendingW, pendingH, pendingArc);
            owner.repaint();
        });
        this.debounce.setRepeats(false);
    }

    public void setSource(BufferedImage img) {
        this.source = img;
        this.cached = null;
        owner.repaint();
    }

    public boolean hasImage() { return source != null; }

    public double aspect() {
        return source == null ? 1 : source.getWidth() / (double) source.getHeight();
    }

    /** Zeichnet das Bild (gefüllt, abgerundet) in das logische Rechteck. */
    public void paint(Graphics2D g, int x, int y, int w, int h) {
        if (source == null || w <= 0 || h <= 0) return;
        double sx = g.getTransform().getScaleX();
        double sy = g.getTransform().getScaleY();
        int dw = (int) Math.round(w * sx);
        int dh = (int) Math.round(h * sy);
        float arc = (float) (arcLogical * sx);

        if (cached == null) {
            rebuild(dw, dh, arc); // erstes Mal: direkt, damit keine eckige Vorschau aufblitzt
        } else if (cached.getWidth() != dw || cached.getHeight() != dh) {
            pendingW = dw;
            pendingH = dh;
            pendingArc = arc;
            debounce.restart();
        }
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cached, x, y, w, h, null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    private void rebuild(int dw, int dh, float arc) {
        if (source == null || dw <= 0 || dh <= 0) return;
        cached = Images.coverRounded(source, dw, dh, arc, anchorY);
    }
}
