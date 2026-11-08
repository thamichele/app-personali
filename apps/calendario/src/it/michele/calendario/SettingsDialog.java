package it.michele.calendario;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.List;

/** Account CalDAV/CardDAV, sincronizzazione, promemoria predefinito, colore principale, avvio automatico. */
final class SettingsDialog {
    private SettingsDialog() {}

    static void open(Window owner, Store store, Sync sync, Runnable onSaved) {
        JDialog d = Ui.dialog(owner, "Impostazioni");
        JPanel body = Ui.column();
        body.setBorder(Ui.pad(20, 20, 16, 20));
        body.setBackground(Ui.PANEL);
        body.setOpaque(true);

        JTextField server = Ui.field("https://cloud.esempio.it/remote.php/dav");
        server.setText(store.server());
        JTextField user = Ui.field("utente");
        user.setText(store.user());
        JPasswordField pass = new JPasswordField(store.password());
        Ui.styleText(pass);
        pass.setBorder(new Ui.FieldBorder());
        pass.setEchoChar('•');
        JLabel result = Ui.label(" ", 12, Ui.OFF, false);

        body.add(Ui.section("Account CalDAV e CardDAV"));
        body.add(Ui.labeled("Indirizzo del server", server));
        body.add(Ui.gap(4));
        JLabel hint = Ui.label("<html><div style='width:380px'>Nextcloud, Radicale, Baïkal, iCloud, Fastmail...: basta l'indirizzo "
                + "del server, calendari e rubriche si trovano da soli. Le attività (VTODO) sono escluse.</div></html>", 11, Ui.DIM, false);
        hint.setAlignmentX(0);
        body.add(hint);
        body.add(Ui.gap(10));
        JPanel up = new JPanel(new GridLayout(1, 2, 12, 0));
        up.setOpaque(false);
        up.add(Ui.labeled("Utente", user));
        up.add(Ui.labeled("Password (o password per le app)", pass));
        up.setAlignmentX(0);
        up.setMaximumSize(new Dimension(Integer.MAX_VALUE, up.getPreferredSize().height));
        body.add(up);
        body.add(Ui.gap(10));
        JPanel test = new JPanel(new BorderLayout(10, 0));
        test.setOpaque(false);
        test.setAlignmentX(0);
        Ui.Btn testBtn = Ui.button("Verifica", Ui.SECONDARY, null);
        testBtn.addActionListener(e -> {
            result.setForeground(Ui.OFF);
            result.setText("Verifica in corso...");
            sync.test(server.getText(), user.getText(), new String(pass.getPassword()), msg -> {
                boolean ok = !msg.startsWith("!");
                result.setForeground(ok ? new java.awt.Color(0x6CC4B6) : Ui.DANGER);
                result.setText(ok ? msg : msg.substring(1));
            });
        });
        test.add(testBtn, BorderLayout.WEST);
        test.add(result, BorderLayout.CENTER);
        test.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        body.add(test);
        body.add(Ui.gap(20));

        body.add(Ui.section("Calendario"));
        Ui.Select<Integer> every = new Ui.Select<>(List.of(5, 15, 30, 60), m -> "Ogni " + m + " minuti");
        every.set(store.syncMinutes());
        Ui.Select<Integer> rem = new Ui.Select<>(List.of(-1, 0, 5, 10, 15, 30, 60), m -> m < 0 ? "Nessuno" : m == 0 ? "All'inizio" : m == 60 ? "1 ora prima" : m + " minuti prima");
        rem.set(store.defaultReminder());
        JPanel two = new JPanel(new GridLayout(1, 2, 12, 0));
        two.setOpaque(false);
        two.add(Ui.labeled("Sincronizza", every));
        two.add(Ui.labeled("Promemoria dei nuovi eventi", rem));
        two.setAlignmentX(0);
        two.setMaximumSize(new Dimension(Integer.MAX_VALUE, two.getPreferredSize().height));
        body.add(two);
        body.add(Ui.gap(12));
        Ui.Toggle auto = new Ui.Toggle(Autostart.enabled());
        body.add(switchRow("Avvia con il computer (nell'area di notifica)", auto));
        body.add(Ui.gap(20));

        body.add(Ui.section("Aspetto"));
        final int[] theme = {store.theme()};
        JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        swatches.setOpaque(false);
        swatches.setAlignmentX(0);
        for (int i = 0; i < Ui.THEME_NAMES.length; i++) {
            final int t = i;
            javax.swing.JComponent sw = new javax.swing.JComponent() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    Ui.aa(g);
                    if (theme[0] == t) {
                        g.setColor(Ui.TEXT);
                        g.fill(new Ellipse2D.Double(0, 0, 30, 30));
                        g.setColor(Ui.PANEL);
                        g.fill(new Ellipse2D.Double(2, 2, 26, 26));
                    }
                    g.setColor(Ui.themeColor(t));
                    g.fill(new Ellipse2D.Double(4, 4, 22, 22));
                    g.dispose();
                }
            };
            sw.setPreferredSize(new Dimension(30, 30));
            sw.setToolTipText(Ui.THEME_NAMES[i]);
            sw.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            sw.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    theme[0] = t;
                    Ui.theme(t);
                    for (Window w : Window.getWindows()) w.repaint();
                }
            });
            swatches.add(sw);
        }
        swatches.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        body.add(Ui.labeled("Colore principale", swatches));
        body.add(Ui.gap(12));
        Ui.Select<Integer> scale = new Ui.Select<>(Main.WINDOWS ? List.of(0, 100, 125, 150, 175, 200) : List.of(0, 100, 200, 300),
                s -> s == 0 ? "Automatica" : s + "%"); // su Linux Java scala solo per numeri interi
        scale.set(store.scale());
        JPanel sc = new JPanel(new GridLayout(1, 2, 12, 0));
        sc.setOpaque(false);
        sc.add(Ui.labeled("Dimensione (al prossimo avvio)", scale));
        sc.add(new JPanel() {{ setOpaque(false); }});
        sc.setAlignmentX(0);
        sc.setMaximumSize(new Dimension(Integer.MAX_VALUE, sc.getPreferredSize().height));
        body.add(sc);
        body.add(Ui.gap(20));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(0);
        final int origTheme = store.theme();
        buttons.add(Ui.button("Annulla", Ui.GHOST, () -> {
            Ui.theme(origTheme);
            for (Window w : Window.getWindows()) w.repaint();
            d.dispose();
        }));
        Ui.Btn save = Ui.button("Salva", Ui.PRIMARY, () -> {
            if (server.getText().trim().toLowerCase().startsWith("http://") && Ui.ask(d, "Connessione non cifrata",
                    "Con http:// la password e i calendari viaggiano in chiaro. Va bene solo nella rete di casa: "
                    + "se puoi, usa https://.", -1, "Annulla", "Salva comunque") != 1) return;
            boolean account = !server.getText().trim().equals(store.server()) || !user.getText().equals(store.user());
            store.put("server", server.getText().trim());
            store.put("utente", user.getText().trim());
            store.put("password", new String(pass.getPassword()));
            store.put("sincronizza", every.get());
            store.put("promemoria", rem.get());
            store.put("tema", theme[0]);
            store.put("scala", scale.get());
            store.save();
            if (auto.on != Autostart.enabled()) Autostart.set(auto.on);
            if (account) { sync.colls = new java.util.ArrayList<>(); Store.deleteCache(); }
            d.dispose();
            onSaved.run();
        });
        buttons.add(save);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        d.getRootPane().setDefaultButton(save);
        Ui.place(d, body, buttons, 540);
        d.setVisible(true);
    }

    private static JPanel switchRow(String label, Ui.Toggle t) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(Ui.label(label, 13, Ui.TEXT, false), BorderLayout.CENTER);
        p.add(t, BorderLayout.EAST);
        p.setAlignmentX(0);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return p;
    }
}
