package com.forscherfreunde.hub.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Kleiner Dialog zum Benennen einer Datei. Prüft beim Tippen, sperrt „OK“ bei ungültigen Namen und
 * bietet einen korrigierten Vorschlag zum Übernehmen an („Mein Schwert“ → „mein_schwert“).
 */
public final class NameDialog {

    private NameDialog() { }

    /**
     * @param validator Fehlermeldung oder {@code null} wenn gültig
     * @param suggester Vorschlag für eine ungültige Eingabe (darf {@code null} liefern)
     * @return der eingegebene Name oder {@code null} bei Abbruch
     */
    public static String ask(Component parent, String title, String text, String initial, String suffix,
                             String rule, String okText, Function<String, String> validator,
                             UnaryOperator<String> suggester) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        String[] result = {null};

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.SURFACE);
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel heading = Ui.label(title, Font.BOLD, 19f, Theme.TEXT);
        JLabel info = Ui.label(text, Font.PLAIN, 14f, Theme.MUTED);

        JTextField field = new JTextField(initial, 22);
        field.setFont(Theme.font(Font.BOLD, 17f));
        field.putClientProperty("JComponent.roundRect", true);
        field.setMargin(new Insets(4, 12, 4, 12));
        JPanel fieldRow = new JPanel(new BorderLayout(8, 0));
        fieldRow.setOpaque(false);
        fieldRow.add(field, BorderLayout.CENTER);
        if (suffix != null && !suffix.isEmpty()) fieldRow.add(Ui.label(suffix, Font.BOLD, 16f, Theme.MUTED), BorderLayout.EAST);
        fieldRow.setMaximumSize(new Dimension(420, 46));

        JLabel hint = Ui.label(rule, Font.PLAIN, 13f, Theme.MUTED);
        JButton suggestion = Ui.ghost("", Icons.Type.CHECK);
        suggestion.setForeground(Theme.ACCENT);
        suggestion.setVisible(false);
        suggestion.setFocusable(false);

        for (JComponent c : new JComponent[] {heading, info, fieldRow, hint, suggestion}) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(heading);
        col.add(Box.createVerticalStrut(6));
        col.add(info);
        col.add(Box.createVerticalStrut(16));
        col.add(fieldRow);
        col.add(Box.createVerticalStrut(8));
        col.add(hint);
        col.add(Box.createVerticalStrut(4));
        col.add(suggestion);
        root.add(col, BorderLayout.CENTER);

        JButton cancel = Ui.secondary("Abbrechen", null);
        JButton ok = Ui.primary(okText, Icons.Type.CHECK);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        buttons.add(cancel);
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

        Runnable check = () -> {
            String name = field.getText().strip();
            String problem = validator.apply(name);
            ok.setEnabled(problem == null);
            if (problem == null) {
                hint.setText(rule);
                hint.setForeground(Theme.MUTED);
                field.putClientProperty("JComponent.outline", null);
                suggestion.setVisible(false);
            } else {
                hint.setText(problem);
                hint.setForeground(name.isEmpty() ? Theme.MUTED : Theme.DANGER);
                field.putClientProperty("JComponent.outline", name.isEmpty() ? null : "error");
                String s = suggester == null || name.isEmpty() ? null : suggester.apply(name);
                boolean useful = s != null && !s.isEmpty() && validator.apply(s) == null;
                suggestion.setText(useful ? "Vorschlag übernehmen: " + s : "");
                suggestion.putClientProperty("value", s);
                suggestion.setVisible(useful);
            }
            d.pack();
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { check.run(); }
            @Override public void removeUpdate(DocumentEvent e) { check.run(); }
            @Override public void changedUpdate(DocumentEvent e) { check.run(); }
        });
        suggestion.addActionListener(e -> {
            field.setText((String) suggestion.getClientProperty("value"));
            field.requestFocusInWindow();
        });
        Runnable submit = () -> {
            String name = field.getText().strip();
            if (validator.apply(name) == null) {
                result[0] = name;
                d.dispose();
            }
        };
        ok.addActionListener(e -> submit.run());
        field.addActionListener(e -> submit.run());
        cancel.addActionListener(e -> d.dispose());
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        root.getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { d.dispose(); }
        });
        cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        d.setContentPane(root);
        d.setResizable(false);
        check.run();
        d.pack();
        d.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> {
            field.requestFocusInWindow();
            field.selectAll();
        });
        d.setVisible(true); // blockiert bis zum Schließen (modal)
        return result[0];
    }
}
