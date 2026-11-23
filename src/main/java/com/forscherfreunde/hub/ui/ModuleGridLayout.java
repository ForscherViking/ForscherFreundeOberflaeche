package com.forscherfreunde.hub.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Ordnet gleich große Bildkarten so an, dass die Bilder bei jeder Fenstergröße so groß wie möglich
 * werden und ihr Seitenverhältnis exakt behalten. Probiert alle Spaltenanzahlen durch (z. B. 1×4
 * oder 2×2 für vier quadratische Kacheln) und nimmt die mit dem größten Bild.
 */
public final class ModuleGridLayout implements LayoutManager {

    private final double aspect;
    private final int gap;
    private final int maxImageWidth;

    public ModuleGridLayout(double aspect, int gap, int maxImageWidth) {
        this.aspect = aspect;
        this.gap = gap;
        this.maxImageWidth = maxImageWidth;
    }

    @Override
    public void layoutContainer(Container parent) {
        int n = parent.getComponentCount();
        if (n == 0) return;
        Insets in = parent.getInsets();
        int aw = parent.getWidth() - in.left - in.right;
        int ah = parent.getHeight() - in.top - in.bottom;
        int extraW = 2 * ModuleCard.PAD;
        int extraH = 2 * ModuleCard.PAD + ModuleCard.CAPTION;

        int bestCols = 1;
        double bestImgW = -1;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            double cellW = (aw - gap * (cols - 1)) / (double) cols - extraW;
            double cellH = (ah - gap * (rows - 1)) / (double) rows - extraH;
            double imgW = Math.min(Math.min(cellW, cellH * aspect), maxImageWidth);
            if (imgW > bestImgW + 0.5) {
                bestImgW = imgW;
                bestCols = cols;
            }
        }
        int imgW = (int) Math.max(40, Math.floor(bestImgW));
        int imgH = (int) Math.round(imgW / aspect);
        int cardW = imgW + extraW;
        int cardH = imgH + extraH;
        int cols = bestCols;
        int rows = (n + cols - 1) / cols;
        int totalW = cols * cardW + (cols - 1) * gap;
        int totalH = rows * cardH + (rows - 1) * gap;
        int x0 = in.left + Math.max(0, (aw - totalW) / 2);
        int y0 = in.top + Math.max(0, (ah - totalH) / 2);

        for (int i = 0; i < n; i++) {
            int r = i / cols;
            int c = i % cols;
            int itemsInRow = Math.min(cols, n - r * cols);
            // unvollständige letzte Reihe mittig ausrichten
            int rowOffset = (cols - itemsInRow) * (cardW + gap) / 2;
            Component comp = parent.getComponent(i);
            comp.setBounds(x0 + rowOffset + c * (cardW + gap), y0 + r * (cardH + gap), cardW, cardH);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return new Dimension(600, 400);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(200, 200);
    }

    @Override public void addLayoutComponent(String name, Component comp) { }

    @Override public void removeLayoutComponent(Component comp) { }
}
