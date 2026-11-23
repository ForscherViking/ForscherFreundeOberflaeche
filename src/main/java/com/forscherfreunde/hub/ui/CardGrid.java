package com.forscherfreunde.hub.ui;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/** Zentriertes Raster gleich großer Karten (max. 3 Spalten), das in einem ScrollPane mitwächst. */
public final class CardGrid extends JPanel implements Scrollable {

    private final int cardW;
    private final int cardH;
    private final int gap;
    private final int maxCols;

    public CardGrid(int cardW, int cardH, int gap, int maxCols) {
        this.cardW = cardW;
        this.cardH = cardH;
        this.gap = gap;
        this.maxCols = maxCols;
        setOpaque(false);
        setLayout(new GridLogic());
    }

    private int columns(int width) {
        Insets in = getInsets();
        int aw = width - in.left - in.right;
        int c = Math.max(1, (aw + gap) / (cardW + gap));
        return Math.max(1, Math.min(Math.min(maxCols, c), Math.max(1, getComponentCount())));
    }

    private final class GridLogic implements LayoutManager {
        @Override
        public void layoutContainer(Container parent) {
            int n = parent.getComponentCount();
            if (n == 0) return;
            Insets in = getInsets();
            int cols = columns(getWidth());
            int rows = (n + cols - 1) / cols;
            int totalW = cols * cardW + (cols - 1) * gap;
            int totalH = rows * cardH + (rows - 1) * gap;
            int x0 = in.left + Math.max(0, (getWidth() - in.left - in.right - totalW) / 2);
            int y0 = in.top + Math.max(0, (getHeight() - in.top - in.bottom - totalH) / 2);
            for (int i = 0; i < n; i++) {
                Component c = parent.getComponent(i);
                c.setBounds(x0 + (i % cols) * (cardW + gap), y0 + (i / cols) * (cardH + gap), cardW, cardH);
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets in = getInsets();
            int n = Math.max(1, parent.getComponentCount());
            int width = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : 3 * cardW;
            int cols = columns(width);
            int rows = (n + cols - 1) / cols;
            return new Dimension(cols * cardW + (cols - 1) * gap + in.left + in.right,
                    rows * cardH + (rows - 1) * gap + in.top + in.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) { return new Dimension(cardW, cardH); }

        @Override public void addLayoutComponent(String name, Component comp) { }

        @Override public void removeLayoutComponent(Component comp) { }
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }

    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return cardH + gap; }

    @Override public boolean getScrollableTracksViewportWidth() { return true; }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }
}
