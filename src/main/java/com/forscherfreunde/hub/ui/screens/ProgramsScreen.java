package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.model.Program;
import com.forscherfreunde.hub.model.ProgramCatalog;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.AppWatcher;
import com.forscherfreunde.hub.service.Background;
import com.forscherfreunde.hub.service.FileNames;
import com.forscherfreunde.hub.service.Launcher;
import com.forscherfreunde.hub.service.ProgramLauncher;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.CardGrid;
import com.forscherfreunde.hub.ui.HoverCard;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.Images;
import com.forscherfreunde.hub.ui.NameDialog;
import com.forscherfreunde.hub.ui.Screen;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.ui.Ui;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Programme-Bereich eines Profils: Paint.NET, mBlock, Blockbench … starten. Alles, was die Kinder hier
 * erstellen, landet automatisch in ihrem Ordner „Meine Dateien“ – ohne sich durch Ordnerpfade zu klicken.
 */
public final class ProgramsScreen extends Screen {

    private static final Set<String> THUMB_EXT = Set.of("png", "jpg", "jpeg", "bmp", "gif");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.").withZone(ZoneId.systemDefault());

    private final UserProfile profile;
    private final Runnable back;
    private final List<Program> programs;
    private final Map<Program, ProgramLauncher.How> availability = new HashMap<>();
    private final CardGrid programGrid = new CardGrid(ProgramCard.W, ProgramCard.H, 16, 3);
    private final CardGrid fileGrid = new CardGrid(FileTile.W, FileTile.H, 14, 6);
    private final JLabel filesHeading = Ui.label("Deine Dateien", Font.BOLD, 18f, Theme.TEXT);
    private final JLabel filesEmpty = Ui.label(" ", Font.PLAIN, 14f, Theme.MUTED);
    private boolean launching;

    public ProgramsScreen(AppWindow app, UserProfile profile, Runnable back) {
        super(app);
        this.profile = profile;
        this.back = back;
        ProgramCatalog catalog = ProgramCatalog.load();
        this.programs = catalog.forProfile(profile);
        setBorder(BorderFactory.createEmptyBorder(26, 40, 0, 40));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.label("Programme", Font.BOLD, 30f, Theme.TEXT));
        titles.add(Box.createVerticalStrut(4));
        titles.add(Ui.label("Alles, was du hier erstellst, landet automatisch in deinem Ordner „"
                + ProgramLauncher.FILES_FOLDER + "“.", Font.PLAIN, 15.5f, Theme.MUTED));
        if (catalog.problem() != null) {
            JLabel warn = Ui.label(catalog.problem(), Font.PLAIN, 13f, Theme.WARM);
            warn.setIcon(Icons.of(Icons.Type.ALERT, 15, Theme.WARM));
            titles.add(Box.createVerticalStrut(6));
            titles.add(warn);
        }
        head.add(titles, BorderLayout.WEST);
        JButton openFolder = Ui.ghost("Meinen Ordner öffnen", Icons.Type.FOLDER);
        openFolder.addActionListener(e -> openFolder());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        right.setOpaque(false);
        right.add(openFolder);
        head.add(right, BorderLayout.EAST);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        add(head, BorderLayout.NORTH);

        JPanel content = new ScrollContent();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        programGrid.setBorder(BorderFactory.createEmptyBorder(6, 0, 18, 0));
        for (Program p : programs) {
            ProgramCard card = new ProgramCard(p);
            programGrid.add(card);
        }
        if (programs.isEmpty()) {
            content.add(left(Ui.label("Für dieses Profil sind keine Programme eingetragen ("
                    + ProgramCatalog.file() + ").", Font.PLAIN, 15f, Theme.MUTED)));
        }
        content.add(left(programGrid));
        filesHeading.setIcon(Icons.of(Icons.Type.FOLDER, 18, Theme.MUTED));
        filesHeading.setIconTextGap(10);
        filesHeading.setBorder(BorderFactory.createEmptyBorder(6, 4, 10, 0));
        content.add(left(filesHeading));
        filesEmpty.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        content.add(left(filesEmpty));
        fileGrid.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));
        content.add(left(fileGrid));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    @Override public String title() { return "Programme"; }

    @Override public Runnable backAction() { return back; }

    @Override
    public void onShown() {
        Component[] cards = programGrid.getComponents();
        for (int i = 0; i < cards.length; i++) ((HoverCard) cards[i]).playAppear(40 + i * 45);
        Background.run(() -> {
            Map<Program, ProgramLauncher.How> m = new HashMap<>();
            for (Program p : programs) m.put(p, ProgramLauncher.availability(p));
            return m;
        }, m -> {
            availability.putAll(m);
            programGrid.repaint();
        }, null);
        refreshFiles();
    }

    private void refreshFiles() {
        Background.run(() -> ProgramLauncher.recentFiles(profile, programs, 12), files -> {
            fileGrid.removeAll();
            for (ProgramLauncher.KidFile f : files) fileGrid.add(new FileTile(f));
            filesEmpty.setText(files.isEmpty() ? "Hier erscheinen deine Bilder und Projekte, sobald du etwas gespeichert hast." : " ");
            filesEmpty.setVisible(files.isEmpty());
            fileGrid.setVisible(!files.isEmpty());
            revalidate();
            repaint();
        }, null);
    }

    private void openFolder() {
        Background.run(() -> {
            Path dir = ProgramLauncher.filesDir(profile);
            Files.createDirectories(dir);
            for (Program p : programs) Files.createDirectories(ProgramLauncher.folder(p, profile));
            Launcher.openFile(dir);
            return null;
        }, null, e -> app.error("Ordner konnte nicht geöffnet werden", e));
    }

    /** Ergebnis eines Starts. */
    private record Started(ProgramLauncher.How how, Path file, boolean alreadyRunning) { }

    private void launch(Program prog, Path existingFile) {
        if (launching) return;
        String chosenName = null;
        if (existingFile == null && prog.newFile() != null && availability.get(prog) != ProgramLauncher.How.BROWSER) {
            boolean image = Set.of("png", "jpg", "jpeg", "bmp").contains(prog.newFile().endung());
            chosenName = askName(prog, image ? "Neues Bild" : "Neue Datei",
                    image ? "Wie soll dein Bild heißen?" : "Wie soll deine Datei heißen?",
                    ProgramLauncher.suggestNewName(prog, profile), "Erstellen", null,
                    "." + prog.newFile().endung());
            if (chosenName == null) return; // abgebrochen
        }
        final String name = chosenName;
        launching = true;
        Background.run(() -> {
            ProgramLauncher.How how = ProgramLauncher.availability(prog);
            if (how == null) throw new IOException("„" + prog.title() + "“ ist auf diesem Laptop nicht installiert.");
            boolean opensFile = existingFile != null || (prog.newFile() != null && how != ProgramLauncher.How.BROWSER);
            if (how != ProgramLauncher.How.BROWSER && !opensFile && prog.singleInstance() && prog.isRunning()) {
                return new Started(how, null, true); // Einzelinstanz-Sperre
            }
            Path file = existingFile;
            if (file == null && prog.newFile() != null && how != ProgramLauncher.How.BROWSER) {
                file = name != null ? ProgramLauncher.createBlankFile(prog, profile, name)
                        : ProgramLauncher.createBlankFile(prog, profile);
            }
            ProgramLauncher.open(prog, profile, file);
            return new Started(how, file, false);
        }, s -> {
            launching = false;
            if (s.alreadyRunning()) {
                app.toast(prog.title() + " ist schon geöffnet.", Icons.Type.ALERT, Theme.WARM);
            } else if (s.how() == ProgramLauncher.How.BROWSER) {
                app.toast(prog.title() + " wird im Browser geöffnet.");
            } else if (s.file() != null && existingFile == null) {
                app.toast("„" + s.file().getFileName() + "“ ist angelegt – Strg+S speichert in deinem Ordner.");
            } else {
                app.toast(prog.title() + " wird geöffnet …");
            }
            if (s.how() == ProgramLauncher.How.BROWSER) return;
            app.minimizeWhileRunning(prog, 90_000, how -> {
                if (how == AppWatcher.End.CLOSED) {
                    Background.run(ProgramLauncher::cleanupUntouchedBlanks, n -> refreshFiles(), null);
                } else {
                    refreshFiles();
                }
            });
        }, e -> {
            launching = false;
            app.error("Start nicht möglich", e);
        });
    }

    /** Namensdialog mit der Regel des Programms (streng: nur a–z, 0–9, _). */
    private String askName(Program prog, String title, String text, String initial, String okText,
                           Path currentFile, String suffix) {
        boolean strict = prog.strictNames();
        Path dir = currentFile != null ? currentFile.getParent() : ProgramLauncher.folder(prog, profile);
        String rule = strict ? "Nur Kleinbuchstaben, Ziffern und _ – ohne Leerzeichen, z. B. mein_schwert"
                : "Ohne Sonderzeichen wie / \\ : * ? \" < > |";
        return NameDialog.ask(this, title, text, initial, suffix, rule, okText, n -> {
            String problem = FileNames.validate(n, strict);
            if (problem != null) return problem;
            Path target = dir.resolve(n + suffix);
            boolean sameFile = currentFile != null && target.toString().equalsIgnoreCase(currentFile.toString());
            if (Files.exists(target) && !sameFile) return "Es gibt schon eine Datei mit diesem Namen.";
            return null;
        }, strict ? FileNames::suggestStrict : null);
    }

    private void rename(ProgramLauncher.KidFile f) {
        Program prog = f.program();
        String fileName = f.path().getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String suffix = dot > 0 ? fileName.substring(dot) : "";
        String initial = prog.strictNames() && FileNames.validate(base, true) != null ? FileNames.suggestStrict(base) : base;
        String name = askName(prog, "Umbenennen", "Neuer Name für „" + fileName + "“:", initial, "Umbenennen",
                f.path(), suffix);
        if (name == null || name.equals(base)) return;
        Background.run(() -> ProgramLauncher.rename(prog, f.path(), name), renamed -> {
            app.toast("Umbenannt in „" + renamed.getFileName() + "“.");
            refreshFiles();
        }, e -> app.error("Umbenennen nicht möglich", e));
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static Color colorOf(Program p) {
        try {
            return p.color() != null ? Color.decode(p.color()) : Theme.ACCENT;
        } catch (NumberFormatException e) {
            return Theme.ACCENT;
        }
    }

    private static Icons.Type iconOf(Program p) {
        return switch (p.symbol().toLowerCase(Locale.ROOT)) {
            case "bild" -> Icons.Type.IMAGE;
            case "roboter" -> Icons.Type.ROBOT;
            case "wuerfel", "würfel" -> Icons.Type.CUBE;
            case "code" -> Icons.Type.CODE;
            default -> Icons.Type.GRID;
        };
    }

    /** Inhalt, der die Breite des Sichtbereichs übernimmt. */
    private static final class ScrollContent extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /** Kachel für ein Programm. */
    private final class ProgramCard extends HoverCard {
        static final int W = 340;
        static final int H = 112;
        private final Program program;
        private final Color color;

        ProgramCard(Program program) {
            this.program = program;
            this.color = colorOf(program);
            setPreferredSize(new Dimension(W, H));
            getAccessibleContext().setAccessibleName(program.title());
            setToolTipText(program.newFile() != null ? "Neue Datei in deinem Ordner anlegen und öffnen"
                    : program.title() + " starten");
            setOnClick(() -> launch(program, null));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            ProgramLauncher.How how = availability.get(program);
            boolean known = availability.containsKey(program);
            boolean missing = known && how == null;
            int lift = Math.round(hover.get() * 3 - press.get() * 2);
            g2.translate(0, -lift);
            int w = getWidth() - 8;
            int h = getHeight() - 10;
            paintChrome(g2, 4, 4, w, h, 20, color, 0f);
            float alpha = missing ? 0.45f : 1f;

            int s = h - 28;
            g2.setColor(Theme.alpha(color, 0.18f * alpha + 0.04f));
            g2.fillRoundRect(18, 18, s, s, 18, 18);
            Icons.paint(g2, iconOf(program), 18 + s * 0.2, 18 + s * 0.2, s * 0.6, Theme.alpha(color, alpha), 2.2f);

            int x = 18 + s + 18;
            int maxW = 4 + w - 16 - x;
            g2.setFont(Theme.font(Font.BOLD, 18f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.alpha(Theme.TEXT, missing ? 0.5f : 1f));
            g2.drawString(ellipsize(program.title(), fm, maxW), x, 22 + fm.getAscent());
            g2.setFont(Theme.font(Font.PLAIN, 13.5f));
            FontMetrics fm2 = g2.getFontMetrics();
            g2.setColor(Theme.MUTED);
            g2.drawString(ellipsize(program.description(), fm2, maxW), x, 22 + fm.getHeight() + fm2.getAscent());

            String status;
            Color sc;
            if (!known) {
                status = " ";
                sc = Theme.MUTED;
            } else if (missing) {
                status = "Nicht installiert";
                sc = Theme.alpha(Theme.MUTED, 0.8f);
            } else if (how == ProgramLauncher.How.BROWSER) {
                status = "Startet im Browser";
                sc = Theme.alpha(color, 0.9f);
            } else if (program.newFile() != null) {
                status = "+  Neues " + program.newFile().name();
                sc = color;
            } else {
                status = "Starten";
                sc = color;
            }
            g2.setFont(Theme.font(Font.BOLD, 12.5f));
            g2.setColor(sc);
            g2.drawString(status, x, 4 + h - 16);
            g2.dispose();
        }
    }

    /** Kachel für eine Datei des Kindes – mit Vorschaubild, wenn es ein Bild ist. */
    private final class FileTile extends HoverCard {
        static final int W = 176;
        static final int H = 150;
        private final ProgramLauncher.KidFile file;
        private final Color color;
        private final JButton renameButton;
        private final boolean nameProblem;
        private BufferedImage thumb;

        @Override
        public void doLayout() {
            int h = getHeight() - 10;
            renameButton.setBounds(getWidth() - 8 - 34, h - 38, 32, 32);
        }

        FileTile(ProgramLauncher.KidFile file) {
            this.file = file;
            this.color = colorOf(file.program());
            setPreferredSize(new Dimension(W, H));
            String name = file.path().getFileName().toString();
            setToolTipText(name + " – mit " + file.program().title() + " öffnen");
            getAccessibleContext().setAccessibleName(name);
            setOnClick(() -> launch(file.program(), file.path()));
            setLayout(null);
            renameButton = Ui.iconButton(Icons.Type.EDIT, "Umbenennen", Theme.MUTED);
            renameButton.addActionListener(e -> rename(file));
            trackHover(renameButton);
            add(renameButton);
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            nameProblem = file.program().strictNames() && FileNames.validate(base, true) != null;
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
            if (THUMB_EXT.contains(ext)) {
                Background.run(() -> {
                    BufferedImage img = ImageIO.read(file.path().toFile());
                    return img == null ? null : Images.scale(img, Math.min(img.getWidth(), 320),
                            Math.max(1, (int) Math.round(img.getHeight() * Math.min(1.0, 320.0 / img.getWidth()))));
                }, img -> {
                    thumb = img;
                    repaint();
                }, e -> { });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            int lift = Math.round(hover.get() * 3 - press.get() * 2);
            g2.translate(0, -lift);
            int w = getWidth() - 8;
            int h = getHeight() - 10;
            paintChrome(g2, 4, 4, w, h, 16, color, 0f);

            int px = 12;
            int py = 12;
            int pw = w - 16;
            int ph = h - 52;
            g2.setColor(Theme.alpha(color, 0.10f));
            g2.fillRoundRect(px, py, pw, ph, 10, 10);
            if (thumb != null) {
                double sc = Math.min(pw / (double) thumb.getWidth(), ph / (double) thumb.getHeight());
                int tw = (int) Math.round(thumb.getWidth() * sc);
                int th = (int) Math.round(thumb.getHeight() * sc);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(thumb, px + (pw - tw) / 2, py + (ph - th) / 2, tw, th, null);
            } else {
                int s = Math.min(pw, ph) - 30;
                Icons.paint(g2, iconOf(file.program()), px + (pw - s) / 2.0, py + (ph - s) / 2.0, s, color, 2f);
            }

            String name = file.path().getFileName().toString();
            g2.setFont(Theme.font(Font.BOLD, 13f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(ellipsize(name, fm, w - 50), 12, py + ph + 8 + fm.getAscent());
            g2.setFont(Theme.font(Font.PLAIN, 11.5f));
            FontMetrics fm2 = g2.getFontMetrics();
            g2.setColor(Theme.MUTED);
            LocalDate d = file.modified().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            String when = d.equals(LocalDate.now()) ? "heute, " + TIME.format(file.modified().toInstant())
                    : DAY.format(file.modified().toInstant());
            if (nameProblem) {
                g2.setColor(Theme.WARM);
                g2.drawString(ellipsize("Name umbenennen!", fm2, w - 50), 12, py + ph + 8 + fm.getHeight() + fm2.getAscent());
            } else {
                g2.drawString(ellipsize(file.program().title() + " · " + when, fm2, w - 50), 12,
                        py + ph + 8 + fm.getHeight() + fm2.getAscent());
            }
            g2.dispose();
        }
    }
}
