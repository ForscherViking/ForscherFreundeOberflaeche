package com.forscherfreunde.hub.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;

/**
 * Vektor-Icons (Linienstil, 24er-Raster) – gestochen scharf in jeder Größe und Skalierung,
 * ohne zusätzliche Bilddateien.
 */
public final class Icons {

    public enum Type {
        PLAY, BOOK, BACK, POWER, TRASH, BACKUP, RESTORE, PLUS, CHECK, CODE, CUBE, FILE, VIDEO, FOLDER, ALERT, USER
    }

    private record Glyph(Shape[] stroke, Shape[] fill) { }

    private static final Map<Type, Glyph> GLYPHS = new EnumMap<>(Type.class);

    static {
        GLYPHS.put(Type.PLAY, new Glyph(new Shape[0], new Shape[] {poly(true, 7, 4.5, 19.5, 12, 7, 19.5)}));
        GLYPHS.put(Type.BOOK, new Glyph(new Shape[] {book(false), book(true)}, new Shape[0]));
        GLYPHS.put(Type.BACK, new Glyph(new Shape[] {line(19, 12, 5, 12), poly(false, 12, 19, 5, 12, 12, 5)}, new Shape[0]));
        GLYPHS.put(Type.POWER, new Glyph(new Shape[] {
                new Arc2D.Double(4, 4.5, 16, 16, 125, 290, Arc2D.OPEN), line(12, 2.5, 12, 11)}, new Shape[0]));
        GLYPHS.put(Type.TRASH, new Glyph(new Shape[] {line(3.5, 6, 20.5, 6), trashBody(),
                poly(false, 9, 6, 9, 3.5, 15, 3.5, 15, 6), line(10, 10.5, 10, 16.5), line(14, 10.5, 14, 16.5)}, new Shape[0]));
        GLYPHS.put(Type.BACKUP, new Glyph(new Shape[] {
                new RoundRectangle2D.Double(3, 3.5, 18, 5, 2, 2),
                poly(false, 5, 8.5, 5, 19.5, 19, 19.5, 19, 8.5), line(10, 12.5, 14, 12.5)}, new Shape[0]));
        GLYPHS.put(Type.RESTORE, new Glyph(new Shape[] {
                new Arc2D.Double(3.5, 3.5, 17, 17, 160, -290, Arc2D.OPEN), poly(false, 3, 4, 3.6, 9.5, 9, 8.6)}, new Shape[0]));
        GLYPHS.put(Type.PLUS, new Glyph(new Shape[] {line(12, 5, 12, 19), line(5, 12, 19, 12)}, new Shape[0]));
        GLYPHS.put(Type.CHECK, new Glyph(new Shape[] {poly(false, 5, 12.5, 10, 17.5, 19, 7)}, new Shape[0]));
        GLYPHS.put(Type.CODE, new Glyph(new Shape[] {poly(false, 8, 7, 3, 12, 8, 17), poly(false, 16, 7, 21, 12, 16, 17),
                line(14, 4.5, 10, 19.5)}, new Shape[0]));
        GLYPHS.put(Type.CUBE, new Glyph(new Shape[] {poly(true, 12, 2.5, 20.5, 7.25, 20.5, 16.75, 12, 21.5, 3.5, 16.75, 3.5, 7.25),
                poly(false, 3.5, 7.25, 12, 12, 20.5, 7.25), line(12, 12, 12, 21.5)}, new Shape[0]));
        GLYPHS.put(Type.FILE, new Glyph(new Shape[] {poly(true, 6, 2.5, 14, 2.5, 19, 7.5, 19, 21.5, 6, 21.5),
                poly(false, 14, 2.5, 14, 7.5, 19, 7.5), line(9, 13, 16, 13), line(9, 17, 14, 17)}, new Shape[0]));
        GLYPHS.put(Type.VIDEO, new Glyph(new Shape[] {new RoundRectangle2D.Double(2.5, 6, 13.5, 12, 4, 4),
                poly(true, 16, 10.5, 21.5, 7, 21.5, 17, 16, 13.5)}, new Shape[0]));
        GLYPHS.put(Type.FOLDER, new Glyph(new Shape[] {poly(true, 3, 5, 9.5, 5, 11.5, 7.5, 21, 7.5, 21, 19, 3, 19)}, new Shape[0]));
        GLYPHS.put(Type.ALERT, new Glyph(new Shape[] {poly(true, 12, 3, 22, 20, 2, 20), line(12, 9.5, 12, 13.5)},
                new Shape[] {new Ellipse2D.Double(10.9, 15.9, 2.2, 2.2)}));
        GLYPHS.put(Type.USER, new Glyph(new Shape[] {new Ellipse2D.Double(8, 3.5, 8, 8),
                new Arc2D.Double(4, 13.5, 16, 14, 0, 180, Arc2D.OPEN)}, new Shape[0]));
    }

    private Icons() { }

    public static Icon of(Type type, int size, Color color) {
        return new VectorIcon(type, size, color, 2f);
    }

    public static Icon of(Type type, int size, Color color, float strokeWidth) {
        return new VectorIcon(type, size, color, strokeWidth);
    }

    /** Zeichnet ein Icon direkt (für selbst gezeichnete Komponenten). */
    public static void paint(Graphics2D g, Type type, double x, double y, double size, Color color, float strokeWidth) {
        Glyph glyph = GLYPHS.get(type);
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.quality(g2);
        g2.translate(x, y);
        g2.scale(size / 24.0, size / 24.0);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Shape s : glyph.fill()) g2.fill(s);
        for (Shape s : glyph.stroke()) g2.draw(s);
        if (type == Type.PLAY) g2.draw(glyph.fill()[0]); // abgerundete Ecken
        g2.dispose();
    }

    private record VectorIcon(Type type, int size, Color color, float stroke) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color col = c != null && !c.isEnabled() ? Theme.alpha(color, 0.4f) : color;
            paint((Graphics2D) g, type, x, y, size, col, stroke);
        }

        @Override public int getIconWidth() { return size; }

        @Override public int getIconHeight() { return size; }
    }

    private static Shape line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Shape poly(boolean closed, double... xy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        if (closed) p.closePath();
        return p;
    }

    private static Shape book(boolean right) {
        Path2D.Double p = new Path2D.Double();
        if (!right) {
            p.moveTo(2.5, 4.5);
            p.lineTo(8, 4.5);
            p.quadTo(12, 4.5, 12, 8.5);
            p.lineTo(12, 20.5);
            p.quadTo(12, 17.5, 9, 17.5);
            p.lineTo(2.5, 17.5);
        } else {
            p.moveTo(21.5, 4.5);
            p.lineTo(16, 4.5);
            p.quadTo(12, 4.5, 12, 8.5);
            p.lineTo(12, 20.5);
            p.quadTo(12, 17.5, 15, 17.5);
            p.lineTo(21.5, 17.5);
        }
        p.closePath();
        return p;
    }

    private static Shape trashBody() {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(5.5, 6);
        p.lineTo(6.5, 19);
        p.quadTo(6.6, 20.5, 8, 20.5);
        p.lineTo(16, 20.5);
        p.quadTo(17.4, 20.5, 17.5, 19);
        p.lineTo(18.5, 6);
        return p;
    }
}
