package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.AppPaths;
import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.Background;
import com.forscherfreunde.hub.service.Launcher;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.HoverCard;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.Screen;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Dokumentation eines Moduls: PDFs und Videos, gruppiert nach Ordnern. Öffnet mit dem Standardprogramm. */
public final class DocsScreen extends Screen {

    private static final int MAX_WIDTH = 880;

    private record Doc(String group, Path file, String name, Kind kind) { }

    private enum Kind { PDF, VIDEO }

    private final UserProfile profile;
    private final Runnable back;
    private final Path root;
    private final JPanel list = new ListPanel();
    private final List<HoverCard> rows = new ArrayList<>();

    public DocsScreen(AppWindow app, UserProfile profile, Runnable back) {
        super(app);
        this.profile = profile;
        this.back = back;
        this.root = AppPaths.documentation().resolve(profile.getModuleName());
        setBorder(BorderFactory.createEmptyBorder(28, 40, 0, 40));

        Camp m = profile.module();
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = Ui.label("Dokumentation", Font.BOLD, 30f, Theme.TEXT);
        JLabel sub = Ui.label(m != null ? m.title() : profile.getModuleName(), Font.PLAIN, 16f, Theme.MUTED);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(title);
        head.add(Box.createVerticalStrut(4));
        head.add(sub);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        add(head, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(6, 0, 30, 0));
        list.add(centered(Ui.label("Wird geladen …", Font.PLAIN, 15f, Theme.MUTED)));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    @Override public String title() { return "Dokumentation"; }

    @Override public Runnable backAction() { return back; }

    @Override
    public void onShown() {
        Background.run(this::scan, this::showDocs, e -> showMessage("Die Dokumentation konnte nicht gelesen werden."));
    }

    private List<Doc> scan() throws IOException {
        List<Doc> docs = new ArrayList<>();
        if (!Files.isDirectory(root)) return docs;
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                String fn = p.getFileName().toString();
                String lower = fn.toLowerCase(Locale.ROOT);
                Kind kind = lower.endsWith(".pdf") ? Kind.PDF
                        : lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v") ? Kind.VIDEO : null;
                if (kind == null) return;
                Path parent = root.relativize(p.getParent());
                String group = parent.toString().isEmpty() ? "" : parent.toString().replace('\\', ' ').replace('/', ' ');
                String name = fn.substring(0, fn.lastIndexOf('.')).replace('_', ' ');
                docs.add(new Doc(group, p, name, kind));
            });
        }
        Collator c = Collator.getInstance(Locale.GERMAN);
        docs.sort((a, b) -> {
            int g = c.compare(a.group(), b.group());
            return g != 0 ? g : c.compare(a.name(), b.name());
        });
        return docs;
    }

    private void showDocs(List<Doc> docs) {
        list.removeAll();
        if (docs.isEmpty()) {
            showMessage("Für dieses Modul gibt es noch keine Dokumentation.");
            return;
        }
        Map<String, List<Doc>> groups = new LinkedHashMap<>();
        for (Doc d : docs) groups.computeIfAbsent(d.group(), k -> new ArrayList<>()).add(d);
        for (Map.Entry<String, List<Doc>> e : groups.entrySet()) {
            if (!e.getKey().isEmpty() || groups.size() > 1) {
                JLabel h = Ui.label(e.getKey().isEmpty() ? "Allgemein" : e.getKey(), Font.BOLD, 13.5f, Theme.MUTED);
                h.setIcon(Icons.of(Icons.Type.FOLDER, 16, Theme.MUTED));
                h.setIconTextGap(8);
                h.setBorder(BorderFactory.createEmptyBorder(14, 6, 8, 0));
                list.add(limit(h, 34));
            }
            for (Doc d : e.getValue()) {
                DocRow row = new DocRow(d);
                rows.add(row);
                list.add(limit(row, 64));
            }
        }
        list.revalidate();
        list.repaint();
        for (int i = 0; i < rows.size() && i < 14; i++) rows.get(i).playAppear(i * 25);
    }

    private void showMessage(String text) {
        list.removeAll();
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = Ui.label(text, Font.PLAIN, 16f, Theme.MUTED);
        l.setIcon(Icons.of(Icons.Type.BOOK, 22, Theme.MUTED));
        l.setIconTextGap(10);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel path = Ui.label(root.toString(), Font.PLAIN, 12.5f, Theme.BORDER.brighter());
        path.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(Box.createVerticalStrut(40));
        p.add(l);
        p.add(Box.createVerticalStrut(8));
        p.add(path);
        list.add(centered(p));
        list.revalidate();
        list.repaint();
    }

    private void open(Doc d) {
        app.toast("„" + d.name() + "“ wird geöffnet …", d.kind() == Kind.PDF ? Icons.Type.FILE : Icons.Type.VIDEO, Theme.ACCENT);
        Background.run(() -> {
            Launcher.openFile(d.file());
            return null;
        }, null, e -> app.error("Datei konnte nicht geöffnet werden", e));
    }

    private static JComponent centered(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        return p;
    }

    private static JComponent limit(JComponent c, int height) {
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        c.setMaximumSize(new Dimension(MAX_WIDTH, height));
        c.setPreferredSize(new Dimension(MAX_WIDTH, height));
        return c;
    }

    /** Liste, die die Breite des Sichtbereichs übernimmt (kein horizontales Scrollen). */
    private static final class ListPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /** Eine Zeile für ein Dokument. */
    private final class DocRow extends HoverCard {
        private final Doc doc;
        private final Color color;

        DocRow(Doc doc) {
            this.doc = doc;
            this.color = doc.kind() == Kind.PDF ? new Color(0xFF7A6B) : new Color(0x6BB8FF);
            setToolTipText(doc.file().getFileName().toString());
            getAccessibleContext().setAccessibleName(doc.name());
            setOnClick(() -> open(doc));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            int x = 3;
            int y = 3;
            int w = getWidth() - 6;
            int h = getHeight() - 10;
            float hv = hover.get();
            g2.translate(Math.round(hv * 3), 0);
            paintChrome(g2, x, y, w - 4, h, 16, color, 0f);

            int s = h - 16;
            g2.setColor(Theme.alpha(color, 0.15f + 0.1f * hv));
            g2.fillRoundRect(x + 8, y + 8, s, s, 12, 12);
            Icons.paint(g2, doc.kind() == Kind.PDF ? Icons.Type.FILE : Icons.Type.VIDEO,
                    x + 8 + s * 0.2, y + 8 + s * 0.2, s * 0.6, color, 2.2f);

            String tag = doc.kind() == Kind.PDF ? "PDF" : "VIDEO";
            g2.setFont(Theme.font(Font.BOLD, 11.5f));
            FontMetrics fmTag = g2.getFontMetrics();
            int tw = fmTag.stringWidth(tag) + 18;
            int tx = x + w - 4 - 16 - tw;
            g2.setColor(Theme.alpha(color, 0.14f));
            g2.fillRoundRect(tx, y + (h - 22) / 2, tw, 22, 22, 22);
            g2.setColor(color);
            g2.drawString(tag, tx + 9, y + (h - fmTag.getHeight()) / 2 + fmTag.getAscent());

            g2.setFont(Theme.font(Font.BOLD, 15.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            int nx = x + 8 + s + 16;
            g2.drawString(ellipsize(doc.name(), fm, tx - nx - 12), nx, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
