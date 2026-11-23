package com.forscherfreunde.hub.ui;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lädt Bilder aus dem Klassenpfad (nicht mehr relativ zum Arbeitsverzeichnis) – asynchron,
 * nur einmal und als „kompatible“ Bilder, die Java2D hardwarebeschleunigt zeichnen kann.
 */
public final class Images {

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ffhub-images");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    static {
        ImageIO.setUseCache(false); // kein Umweg über temporäre Dateien
    }

    private Images() { }

    /** Synchron laden (nur für kleine Bilder wie Logo/Icon). */
    public static BufferedImage get(String name) {
        return CACHE.computeIfAbsent(name, Images::read);
    }

    /** Asynchron laden; der Callback läuft auf dem Swing-Thread (sofort, wenn schon im Cache). */
    public static void load(String name, Consumer<BufferedImage> onLoaded) {
        BufferedImage cached = CACHE.get(name);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        LOADER.execute(() -> {
            BufferedImage img = get(name);
            SwingUtilities.invokeLater(() -> onLoaded.accept(img));
        });
    }

    public static void preload(Iterable<String> names) {
        for (String n : names) LOADER.execute(() -> get(n));
    }

    private static BufferedImage read(String name) {
        try (InputStream in = Images.class.getResourceAsStream("/img/" + name)) {
            if (in == null) throw new IOException("Bild fehlt: " + name);
            BufferedImage raw = ImageIO.read(in);
            return toCompatible(raw, raw.getColorModel().hasAlpha() ? Transparency.TRANSLUCENT : Transparency.OPAQUE);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            BufferedImage fallback = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = fallback.createGraphics();
            g.setColor(Theme.SURFACE_HI);
            g.fillRect(0, 0, 4, 4);
            g.dispose();
            return fallback;
        }
    }

    private static BufferedImage create(int w, int h, int transparency) {
        if (!GraphicsEnvironment.isHeadless()) {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
            return gc.createCompatibleImage(w, h, transparency);
        }
        return new BufferedImage(w, h, transparency == Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage toCompatible(BufferedImage src, int transparency) {
        BufferedImage dst = create(src.getWidth(), src.getHeight(), transparency);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    /**
     * Hochwertige, aber schnelle Verkleinerung: schrittweise halbieren (bilinear), statt des sehr
     * langsamen {@code getScaledInstance(SCALE_SMOOTH)}.
     */
    public static BufferedImage scale(BufferedImage src, int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        int transparency = src.getTransparency();
        do {
            cw = cw / 2 >= w ? cw / 2 : w;
            ch = ch / 2 >= h ? ch / 2 : h;
            if (cw < w) cw = w;
            if (ch < h) ch = h;
            BufferedImage next = create(cw, ch, transparency);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, cw, ch, null);
            g.dispose();
            cur = next;
        } while (cw != w || ch != h);
        return cur;
    }

    /**
     * Skaliert {@code src} so, dass es {@code w×h} vollständig füllt (überstehende Ränder werden
     * abgeschnitten, {@code anchorY} 0 = oben, 0.5 = mittig) und rundet die Ecken geglättet ab.
     */
    public static BufferedImage coverRounded(BufferedImage src, int w, int h, float arc, float anchorY) {
        double s = Math.max(w / (double) src.getWidth(), h / (double) src.getHeight());
        int sw = Math.max(w, (int) Math.ceil(src.getWidth() * s));
        int sh = Math.max(h, (int) Math.ceil(src.getHeight() * s));
        BufferedImage scaled = scale(src, sw, sh);
        int ox = (sw - w) / 2;
        int oy = Math.round((sh - h) * anchorY);

        BufferedImage out = create(w, h, Transparency.TRANSLUCENT);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(scaled, -ox, -oy, null);
        g.dispose();
        return out;
    }
}
