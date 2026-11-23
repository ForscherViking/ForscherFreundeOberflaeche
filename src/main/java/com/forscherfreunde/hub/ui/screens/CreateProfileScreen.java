package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.ProfileStore;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.WorkspaceService;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.ModuleCard;
import com.forscherfreunde.hub.ui.ModuleGridLayout;
import com.forscherfreunde.hub.ui.Screen;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Neues Profil anlegen – alles auf einem Bildschirm statt in drei Einzelfenstern:
 * Name eingeben, Art wählen, Camp anklicken, „Profil erstellen“.
 */
public final class CreateProfileScreen extends Screen {

    private final JTextField name = new JTextField();
    private final JLabel nameHint = Ui.label(" ", Font.PLAIN, 13f, Theme.MUTED);
    private final JButton create = Ui.primary("Profil erstellen", Icons.Type.CHECK);
    private final JPanel pickerHost = new JPanel(new CardLayout());
    private final Map<Camp.Kind, List<ModuleCard>> cards = new EnumMap<>(Camp.Kind.class);
    private Camp.Kind kind;
    private Camp selected;
    private boolean nameTouched;

    public CreateProfileScreen(AppWindow app) {
        super(app);
        setBorder(BorderFactory.createEmptyBorder(26, 40, 22, 40));
        add(buildForm(), BorderLayout.NORTH);
        add(buildPickers(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        updateState();
    }

    @Override public String title() { return "Neues Profil"; }

    @Override public Runnable backAction() { return app::showProfiles; }

    @Override
    public void onShown() {
        name.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ Aufbau

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);

        JPanel nameCol = column("Wie heißt du?");
        name.setFont(Theme.font(Font.BOLD, 18f));
        name.putClientProperty("JTextField.placeholderText", "Dein Name");
        name.putClientProperty("JComponent.roundRect", true);
        name.setPreferredSize(new Dimension(320, 48));
        name.setMaximumSize(new Dimension(320, 48));
        name.setMargin(new Insets(4, 16, 4, 16));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { nameTouched = true; updateState(); }
            @Override public void removeUpdate(DocumentEvent e) { nameTouched = true; updateState(); }
            @Override public void changedUpdate(DocumentEvent e) { updateState(); }
        });
        name.addActionListener(e -> { if (create.isEnabled()) create.doClick(); });
        nameCol.add(name);
        nameCol.add(Box.createVerticalStrut(6));
        nameHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameCol.add(nameHint);

        JPanel kindCol = column("Was möchtest du machen?");
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        JToggleButton code = chip("Programmieren", Icons.Type.CODE, Camp.Kind.CODE, group);
        JToggleButton mc = chip("Minecraft-Welten", Icons.Type.CUBE, Camp.Kind.MINECRAFT, group);
        chips.add(code);
        chips.add(Box.createHorizontalStrut(10));
        chips.add(mc);
        kindCol.add(chips);
        kindCol.add(Box.createVerticalStrut(6));
        kindCol.add(Box.createVerticalStrut(nameHint.getPreferredSize().height));

        row.add(nameCol);
        row.add(Box.createHorizontalStrut(44));
        row.add(kindCol);
        form.add(row);
        return form;
    }

    private JPanel column(String heading) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel l = Ui.label(heading, Font.BOLD, 14f, Theme.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(l);
        col.add(Box.createVerticalStrut(10));
        return col;
    }

    private JToggleButton chip(String text, Icons.Type icon, Camp.Kind k, ButtonGroup group) {
        JToggleButton b = new JToggleButton(text, Icons.of(icon, 18, Theme.TEXT));
        b.setSelectedIcon(Icons.of(icon, 18, Theme.ON_ACCENT));
        b.setFont(Theme.font(Font.BOLD, 15f));
        b.setIconTextGap(10);
        b.setMargin(new Insets(12, 20, 12, 22));
        b.setFocusable(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Ui.style(b, "arc: 999; borderWidth: 1; focusWidth: 0; innerFocusWidth: 0;"
                + "background: " + Theme.hex(Theme.SURFACE_HI) + "; hoverBackground: #283748;"
                + "borderColor: " + Theme.hex(Theme.BORDER) + ";"
                + "selectedBackground: " + Theme.hex(Theme.ACCENT) + ";"
                + "selectedForeground: " + Theme.hex(Theme.ON_ACCENT));
        b.addActionListener(e -> selectKind(k));
        group.add(b);
        return b;
    }

    private JPanel buildPickers() {
        pickerHost.setOpaque(false);
        pickerHost.setBorder(BorderFactory.createEmptyBorder(18, 0, 8, 0));

        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        JLabel hint = Ui.label("Wähle oben aus, was du machen möchtest – dann erscheinen hier die Camps.",
                Font.PLAIN, 16f, Theme.MUTED);
        empty.add(hint);
        pickerHost.add(empty, "none");

        for (Camp.Kind k : Camp.Kind.values()) {
            List<Camp> modules = Camp.ofKind(k);
            double aspect = modules.get(0).imageAspect();
            JPanel grid = new JPanel(new ModuleGridLayout(aspect, 26, k == Camp.Kind.CODE ? 360 : 300));
            grid.setOpaque(false);
            List<ModuleCard> list = new ArrayList<>();
            for (Camp m : modules) {
                ModuleCard card = new ModuleCard(m);
                card.setOnClick(() -> selectModule(m));
                list.add(card);
                grid.add(card);
            }
            cards.put(k, list);
            pickerHost.add(grid, k.name());
        }
        return pickerHost;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(16, 0, 0, 0)));
        JButton cancel = Ui.secondary("Abbrechen", null);
        cancel.addActionListener(e -> app.showProfiles());
        create.addActionListener(e -> createProfile());
        footer.add(cancel);
        footer.add(create);
        return footer;
    }

    // ------------------------------------------------------------------ Logik

    private void selectKind(Camp.Kind k) {
        if (k == kind) return;
        kind = k;
        selected = null;
        for (ModuleCard c : cards.get(k)) c.setState(false, false);
        ((CardLayout) pickerHost.getLayout()).show(pickerHost, k.name());
        List<ModuleCard> list = cards.get(k);
        for (int i = 0; i < list.size(); i++) list.get(i).playAppear(i * 50);
        updateState();
    }

    private void selectModule(Camp m) {
        selected = m;
        for (ModuleCard c : cards.get(kind)) c.setState(c.module() == m, true);
        updateState();
    }

    private void updateState() {
        String error = app.store().validateName(name.getText());
        if (error != null && nameTouched) {
            nameHint.setText(error);
            nameHint.setForeground(Theme.DANGER);
            name.putClientProperty("JComponent.outline", "error");
        } else {
            nameHint.setText(error == null ? "Super, " + name.getText().strip() + "!" : " ");
            nameHint.setForeground(error == null ? Theme.ACCENT : Theme.MUTED);
            name.putClientProperty("JComponent.outline", null);
        }
        create.setEnabled(error == null && selected != null);
        create.setToolTipText(error != null ? error
                : selected == null ? "Bitte wähle ein Camp aus." : null);
    }

    private void createProfile() {
        if (app.store().isFull()) {
            app.error("Zu viele Profile", "Es sind bereits " + ProfileStore.MAX_PROFILES + " Profile vorhanden.");
            return;
        }
        String n = name.getText().strip();
        if (app.store().validateName(n) != null || selected == null) return;
        UserProfile profile = new UserProfile(n, selected);
        app.runBusy("Profil für " + n + " wird angelegt …", pr -> {
            WorkspaceService.create(profile, pr);
            return null;
        }, r -> {
            try {
                app.store().add(profile);
            } catch (java.io.IOException ex) {
                app.error("Profil konnte nicht gespeichert werden", ex);
                return;
            }
            app.toast("Willkommen, " + n + "!");
            app.showProfile(profile);
        }, e -> app.error("Profil konnte nicht angelegt werden", e));
    }
}
