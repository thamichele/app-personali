package it.michele.calendario;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Contatti delle rubriche CardDAV, in sola lettura, con ricerca. */
final class ContactsDialog {
    private ContactsDialog() {}

    static void open(Window owner, Sync sync) {
        List<Model.Contact> all = new ArrayList<>();
        for (Model.Coll c : sync.colls)
            if (c.kind == Model.CARD) for (Model.Res r : c.items.values()) all.addAll(Model.contacts(r.root()));
        Collator col = Collator.getInstance(Locale.ITALIAN);
        all.sort((a, b) -> col.compare(a.name, b.name));

        JDialog d = Ui.dialog(owner, "Contatti");
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Ui.PANEL);
        root.setBorder(Ui.pad(16, 16, 16, 16));
        JTextField search = Ui.field("Cerca per nome, telefono o email");
        root.add(search, BorderLayout.NORTH);
        JPanel list = Ui.column();
        list.setBorder(Ui.pad(0, 0, 0, 6));
        javax.swing.JScrollPane sp = Ui.scroll(list);
        sp.getViewport().setBackground(Ui.PANEL);
        root.add(sp, BorderLayout.CENTER);
        JLabel count = Ui.label("", 11, Ui.DIM, false);
        root.add(count, BorderLayout.SOUTH);

        Runnable fill = () -> {
            list.removeAll();
            String q = search.getText().trim().toLowerCase(Locale.ITALIAN);
            int n = 0;
            for (Model.Contact c : all) {
                if (!q.isEmpty() && !matches(c, q)) continue;
                if (++n > 500) break;
                list.add(row(c));
                list.add(Ui.gap(6));
            }
            if (all.isEmpty()) list.add(Ui.label(sync.configured() ? "Nessun contatto: sincronizza o controlla le rubriche sul server"
                    : "Configura il server nelle impostazioni", 13, Ui.OFF, false));
            count.setText(all.isEmpty() ? " " : Math.min(n, 500) + " di " + all.size() + " contatti · sola lettura");
            list.revalidate();
            list.repaint();
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { fill.run(); }
            @Override public void removeUpdate(DocumentEvent e) { fill.run(); }
            @Override public void changedUpdate(DocumentEvent e) { fill.run(); }
        });
        fill.run();
        d.setContentPane(root);
        d.setSize(520, 620);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static boolean matches(Model.Contact c, String q) {
        if (c.name.toLowerCase(Locale.ITALIAN).contains(q)) return true;
        if (c.org != null && c.org.toLowerCase(Locale.ITALIAN).contains(q)) return true;
        String digits = q.replaceAll("[^0-9]", "");
        for (String p : c.phones) if (!digits.isEmpty() && p.replaceAll("[^0-9]", "").contains(digits)) return true;
        for (String e : c.emails) if (e.toLowerCase(Locale.ITALIAN).contains(q)) return true;
        return false;
    }

    private static JPanel row(Model.Contact c) {
        JPanel card = Ui.card(Ui.BG, Ui.BORDER, 12);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(Ui.pad(10, 14, 10, 14));
        JPanel col = Ui.column();
        col.add(Ui.label(c.name.isEmpty() ? c.org : c.name, 13, Ui.TEXT, true));
        if (c.org != null && !c.org.isEmpty() && !c.name.isEmpty()) col.add(Ui.label(c.org, 12, Ui.OFF, false));
        for (String p : c.phones) col.add(selectable(p));
        for (String e : c.emails) col.add(selectable(e));
        card.add(col, BorderLayout.CENTER);
        if (c.birthday != null) {
            String b = c.birthday.getDayOfMonth() + " " + Ui.MONTHS[c.birthday.getMonthValue() - 1].toLowerCase()
                    + (c.birthday.getYear() > 0 ? " " + c.birthday.getYear() : "");
            JLabel l = Ui.label(b, 11, Ui.ACCENT_TEXT, false);
            l.setIcon(new Ui.Glyph("calendar", 14, Ui.ACCENT_TEXT));
            l.setVerticalAlignment(JLabel.TOP);
            card.add(l, BorderLayout.EAST);
        }
        card.setAlignmentX(0);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    /** Testo copiabile (telefono, email) con l'aspetto di un'etichetta. */
    private static JTextField selectable(String s) {
        JTextField t = new JTextField(s);
        t.setEditable(false);
        t.setBorder(null);
        t.setOpaque(false);
        t.setFont(Ui.font(12, false));
        t.setForeground(Ui.OFF);
        t.setSelectionColor(Ui.ACCENT_BORDER);
        t.setAlignmentX(0);
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        return t;
    }
}
