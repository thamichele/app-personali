package it.michele.calendario;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/** Finestra principale: barra laterale (mini mese, calendari), intestazione e vista scelta. */
final class MainWindow implements Views.Host {
    private static final String MONTH = "Mese", WEEK = "Settimana", DAY = "Giorno", AGENDA = "Agenda";

    final JFrame frame = new JFrame("Calendario");
    private final Store store;
    private final Sync sync;
    private Reminders reminders;
    private String view;
    private LocalDate anchor = LocalDate.now();

    private final Views.Month month = new Views.Month(this);
    private final Views.WeekHead weekHead = new Views.WeekHead(this);
    private final Views.WeekBody weekBody = new Views.WeekBody(this);
    private final Views.Agenda agenda = new Views.Agenda(this);
    private final Views.Mini mini = new Views.Mini();
    private final JPanel cards = new JPanel(new CardLayout());
    private final JScrollPane weekScroll = Ui.scroll(weekBody);
    private final JLabel title = Ui.label("", 22, Ui.TEXT, true);
    private final JLabel status = Ui.label(" ", 11, Ui.DIM, false);
    private final JPanel calList = Ui.column();
    private final java.util.Map<String, Ui.Btn> viewButtons = new java.util.LinkedHashMap<>();
    private Ui.Btn refreshBtn;
    private Timer syncTimer;
    private boolean tray, weekScrolled;
    private LocalDate today = LocalDate.now();

    MainWindow(Store store, Sync sync) {
        this.store = store;
        this.sync = sync;
        view = store.get("vista", MONTH);
        frame.setIconImages(Ui.appIcons());
        frame.getContentPane().setBackground(Ui.BG);
        frame.setLayout(new BorderLayout());
        frame.add(sidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Ui.BG);
        main.add(header(), BorderLayout.NORTH);
        cards.setBackground(Ui.BG);
        cards.add(month, MONTH);
        weekScroll.setColumnHeaderView(weekHead);
        weekScroll.getColumnHeader().setBackground(Ui.BG);
        cards.add(weekScroll, WEEK);
        cards.add(Ui.scroll(agenda), AGENDA);
        main.add(cards, BorderLayout.CENTER);
        frame.add(main, BorderLayout.CENTER);
        keys(main);

        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(store.getInt("larghezza", 1240), store.getInt("altezza", 820));
        frame.setLocationRelativeTo(null);
        if (store.getBool("massimizzata", false)) frame.setExtendedState(Frame.MAXIMIZED_BOTH);

        sync.onChange = this::refresh;
        mini.onPick = d -> { anchor = d; refresh(); };
        setView(view);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { close(); }
        });
        setupTray();
        reminders = new Reminders(sync, store, this::show);
        reminders.tray = trayIcon;
        restartTimer();
        new Timer(60_000, e -> { // linea dell'ora attuale e cambio di giorno
            if (frame.isVisible()) { if (!LocalDate.now().equals(today)) { today = LocalDate.now(); refresh(); } else cards.repaint(); }
        }).start();
        if (sync.configured()) syncNow(); else status.setText("Server non configurato");
    }

    void show() {
        frame.setVisible(true);
        if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
        frame.toFront();
        frame.requestFocus();
    }

    private void close() {
        boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        store.put("massimizzata", max);
        if (!max) { store.put("larghezza", frame.getWidth()); store.put("altezza", frame.getHeight()); }
        store.put("vista", view);
        store.save();
        if (tray) frame.setVisible(false); // resta nell'area di notifica per i promemoria
        else System.exit(0);
    }

    // ---------- area di notifica ----------

    private TrayIcon trayIcon;

    private void setupTray() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray st = SystemTray.getSystemTray();
            Dimension size = st.getTrayIconSize();
            trayIcon = new TrayIcon(Ui.appIcon(Math.max(16, size.height), Ui.ACCENT), "Calendario");
            trayIcon.setImageAutoSize(true);
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Apri Calendario");
            open.addActionListener(e -> SwingUtilities.invokeLater(this::show));
            MenuItem add = new MenuItem("Nuovo evento");
            add.addActionListener(e -> SwingUtilities.invokeLater(() -> { show(); create(LocalDate.now(), nextHour(), 0); }));
            MenuItem s = new MenuItem("Sincronizza");
            s.addActionListener(e -> SwingUtilities.invokeLater(this::syncNow));
            MenuItem quit = new MenuItem("Esci");
            quit.addActionListener(e -> { close(); System.exit(0); });
            menu.add(open); menu.add(add); menu.add(s); menu.addSeparator(); menu.add(quit);
            trayIcon.setPopupMenu(menu);
            trayIcon.addActionListener(e -> SwingUtilities.invokeLater(this::show));
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) SwingUtilities.invokeLater(MainWindow.this::show);
                }
            });
            st.add(trayIcon);
            tray = true;
        } catch (AWTException | UnsupportedOperationException e) {
            tray = false;
        }
    }

    boolean hasTray() { return tray; }

    // ---------- sincronizzazione ----------

    private void syncNow() {
        if (sync.busy) return;
        status.setText("Sincronizzazione...");
        sync.syncNow(err -> {
            if (reminders != null) reminders.check();
        });
    }

    private void restartTimer() {
        if (syncTimer != null) syncTimer.stop();
        int ms = Math.max(1, sync.configured() ? store.syncMinutes() : 60) * 60_000;
        syncTimer = new Timer(ms, e -> { if (sync.configured()) syncNow(); });
        syncTimer.start();
    }

    // ---------- intestazione ----------

    private JPanel header() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(Ui.BG);
        h.setBorder(Ui.pad(14, 20, 12, 16));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(Ui.button("Oggi", Ui.SECONDARY, () -> { anchor = LocalDate.now(); refresh(); }));
        left.add(Ui.iconButton("left", "Precedente (←)", () -> move(-1)));
        left.add(Ui.iconButton("right", "Successivo (→)", () -> move(1)));
        title.setBorder(Ui.pad(0, 10, 0, 0));
        left.add(title);
        h.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JPanel seg = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Ui.aa(g);
                g.setColor(Ui.PANEL);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
                g.setColor(Ui.BORDER);
                g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 10, 10));
                g.dispose();
            }
        };
        seg.setOpaque(false);
        seg.setBorder(Ui.pad(2, 2, 2, 2));
        String[][] vs = {{MONTH, "M"}, {WEEK, "S"}, {DAY, "G"}, {AGENDA, "A"}};
        for (String[] v : vs) {
            Ui.Btn b = Ui.button(v[0], Ui.GHOST, () -> setView(v[0]));
            b.setPreferredSize(new Dimension(b.getPreferredSize().width, 32));
            b.setToolTipText(v[0] + " (" + v[1] + ")");
            viewButtons.put(v[0], b);
            seg.add(b);
        }
        right.add(seg);
        right.add(javax.swing.Box.createHorizontalStrut(8));
        refreshBtn = Ui.iconButton("refresh", "Sincronizza (F5)", this::syncNow);
        right.add(refreshBtn);
        right.add(Ui.iconButton("user", "Contatti", () -> ContactsDialog.open(frame, sync)));
        right.add(Ui.iconButton("settings", "Impostazioni", this::settings));
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private void settings() {
        SettingsDialog.open(frame, store, sync, () -> {
            restartTimer();
            refresh();
            if (sync.configured()) syncNow();
        });
    }

    // ---------- barra laterale ----------

    private JPanel sidebar() {
        JPanel s = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Ui.PANEL);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Ui.BORDER);
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
        s.setPreferredSize(new Dimension(256, 100));
        s.setBorder(Ui.pad(16, 16, 12, 16));
        JPanel top = Ui.column();
        JLabel app = Ui.label("Calendario", 15, Ui.TEXT, true);
        app.setIcon(new Ui.Glyph("calendar", 20, Ui.ACCENT_TEXT) {
            @Override public void paintIcon(java.awt.Component c, Graphics g, int x, int y) { color = Ui.ACCENT_TEXT; super.paintIcon(c, g, x, y); }
        });
        app.setIconTextGap(10);
        app.setAlignmentX(0);
        top.add(app);
        top.add(Ui.gap(16));
        Ui.Btn add = new Ui.Btn("Nuovo evento", "plus", Ui.PRIMARY);
        add.addActionListener(e -> create(anchor, nextHour(), 0));
        add.setToolTipText("Nuovo evento (Ctrl+N)");
        add.setAlignmentX(0);
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        top.add(add);
        top.add(Ui.gap(16));
        mini.setAlignmentX(0);
        top.add(mini);
        top.add(Ui.gap(16));
        JLabel sec = Ui.section("Calendari");
        sec.setAlignmentX(0);
        top.add(sec);
        calList.setAlignmentX(0);
        top.add(calList);
        JScrollPane sp = Ui.scroll(top);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        s.add(sp, BorderLayout.CENTER);
        status.setBorder(Ui.pad(8, 0, 0, 0));
        s.add(status, BorderLayout.SOUTH);
        return s;
    }

    private void buildCalendars() {
        calList.removeAll();
        Set<String> hidden = store.hidden();
        boolean anyCard = false;
        for (Model.Coll c : sync.colls) {
            if (c.kind == Model.CARD) { anyCard = true; continue; }
            calList.add(calRow(c.name, c.readOnly ? "sola lettura" : null, Ui.color(c.color), !hidden.contains(c.url), v -> {
                Set<String> h = store.hidden();
                if (v) h.remove(c.url); else h.add(c.url);
                store.hidden(h);
                store.save();
                refresh();
            }));
        }
        if (anyCard) calList.add(calRow("Compleanni", "dai contatti", Ui.ACCENT_SOFT, store.getBool("compleanni", true), v -> {
            store.put("compleanni", v);
            store.save();
            refresh();
        }));
        if (sync.colls.isEmpty()) {
            JPanel w = Ui.card(Ui.ACCENT_DEEP, Ui.ACCENT_BORDER, 10);
            w.setLayout(new BorderLayout(10, 0));
            w.setBorder(Ui.pad(12, 12, 12, 12));
            JLabel i = new JLabel(new Ui.Glyph("alert", 18, Ui.ACCENT_TEXT));
            i.setVerticalAlignment(JLabel.TOP);
            w.add(i, BorderLayout.WEST);
            w.add(Ui.label("<html><div style='width:150px'><b>" + (sync.configured() ? "Nessun calendario" : "Collega il server")
                    + "</b><br><span style='color:#A39E95'>" + (sync.configured() ? "Sincronizza o controlla l'account."
                    : "Apri le impostazioni e inserisci indirizzo, utente e password.") + "</span></div></html>", 12, Ui.TEXT, false), BorderLayout.CENTER);
            w.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            w.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (sync.configured()) syncNow(); else settings(); }
            });
            w.setAlignmentX(0);
            calList.add(w);
        }
        calList.revalidate();
        calList.repaint();
    }

    /** Riga di un calendario: casella del suo colore, nome, nota. */
    private JComponent calRow(String name, String note, java.awt.Color color, boolean on, java.util.function.Consumer<Boolean> change) {
        final boolean[] state = {on};
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setBorder(Ui.pad(5, 0, 5, 0));
        JComponent box = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Ui.aa(g);
                int y = (getHeight() - 18) / 2;
                if (state[0]) {
                    g.setColor(color);
                    g.fill(new RoundRectangle2D.Double(0, y, 18, 18, 8, 8));
                    new Ui.Glyph("check", 14, Ui.BG).paintIcon(this, g, 2, y + 2);
                } else {
                    g.setColor(color);
                    g.setStroke(new java.awt.BasicStroke(1.5f));
                    g.draw(new RoundRectangle2D.Double(0.75, y + 0.75, 16.5, 16.5, 8, 8));
                }
                g.dispose();
            }
        };
        box.setPreferredSize(new Dimension(18, 20));
        r.add(box, BorderLayout.WEST);
        JPanel col = Ui.column();
        col.add(Ui.label(name, 13, Ui.TEXT, false));
        if (note != null) col.add(Ui.label(note, 10, Ui.DIM, false));
        r.add(col, BorderLayout.CENTER);
        r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        r.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { state[0] = !state[0]; box.repaint(); change.accept(state[0]); }
        });
        r.setAlignmentX(0);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
        return r;
    }

    // ---------- navigazione ----------

    private void keys(JComponent c) {
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), () -> move(-1));
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), () -> move(1));
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), () -> { anchor = LocalDate.now(); refresh(); });
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_M, 0), () -> setView(MONTH));
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), () -> setView(WEEK));
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), () -> setView(DAY));
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), () -> setView(AGENDA));
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::syncNow);
        bind(c, KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK), () -> create(anchor, nextHour(), 0));
    }

    private static void bind(JComponent c, KeyStroke k, Runnable r) {
        String id = k.toString();
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, id);
        c.getActionMap().put(id, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { r.run(); }
        });
    }

    private static int nextHour() { return Math.min(23, java.time.LocalTime.now().getHour() + 1); }

    private void move(int dir) {
        switch (view) {
            case MONTH: anchor = anchor.plusMonths(dir); break;
            case WEEK: anchor = anchor.plusWeeks(dir); break;
            case DAY: anchor = anchor.plusDays(dir); break;
            default: anchor = anchor.plusDays(dir * 30L); break;
        }
        refresh();
    }

    private void setView(String v) {
        view = v;
        ((CardLayout) cards.getLayout()).show(cards, v.equals(DAY) ? WEEK : v);
        for (java.util.Map.Entry<String, Ui.Btn> e : viewButtons.entrySet()) { e.getValue().selected = e.getKey().equals(v); e.getValue().repaint(); }
        weekHead.days = weekBody.days = v.equals(DAY) ? 1 : 7;
        refresh();
        if (!weekScrolled && (v.equals(WEEK) || v.equals(DAY))) {
            weekScrolled = true; // la prima volta parte dalle 7
            SwingUtilities.invokeLater(() -> weekScroll.getVerticalScrollBar().setValue(Views.HOUR * 7));
        }
    }

    /** Ricalcola le occorrenze dell'intervallo mostrato e ridisegna. */
    void refresh() {
        ZoneId z = ZoneId.systemDefault();
        LocalDate from, to;
        switch (view) {
            case MONTH: from = Views.monthStart(anchor); to = from.plusDays(42); break;
            case WEEK: from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); to = from.plusDays(7); break;
            case DAY: from = anchor; to = anchor.plusDays(1); break;
            default: from = anchor; to = anchor.plusDays(Views.AGENDA_DAYS); break;
        }
        java.util.List<Model.Occ> occs = Model.occurrences(sync.colls, store.hidden(), store.getBool("compleanni", true),
                from.atStartOfDay(z), to.atStartOfDay(z));
        for (Views.View v : new Views.View[]{month, weekHead, weekBody, agenda}) { v.anchor = anchor; v.occs = occs; }
        if (view.equals(MONTH)) title.setText(Ui.MONTHS[anchor.getMonthValue() - 1] + " " + anchor.getYear());
        else if (view.equals(WEEK)) {
            LocalDate a = from, b = to.minusDays(1);
            title.setText(a.getMonth() == b.getMonth() ? a.getDayOfMonth() + "–" + b.getDayOfMonth() + " " + Ui.MONTHS[a.getMonthValue() - 1].toLowerCase() + " " + a.getYear()
                    : a.getDayOfMonth() + " " + Ui.MONTHS[a.getMonthValue() - 1].substring(0, 3).toLowerCase() + " – " + b.getDayOfMonth() + " "
                    + Ui.MONTHS[b.getMonthValue() - 1].substring(0, 3).toLowerCase() + " " + b.getYear());
        } else if (view.equals(DAY)) title.setText(Ui.DAYS[anchor.getDayOfWeek().getValue() - 1] + " " + anchor.getDayOfMonth() + " "
                + Ui.MONTHS[anchor.getMonthValue() - 1].toLowerCase() + " " + anchor.getYear());
        else title.setText("Agenda");
        mini.selected = anchor;
        if (!anchor.withDayOfMonth(1).equals(mini.shown) && !view.equals(AGENDA)) mini.shown = anchor.withDayOfMonth(1);
        mini.repaint();
        buildCalendars();
        status.setText(sync.busy ? "Sincronizzazione..." : sync.status.isEmpty() ? " " : sync.status);
        status.setForeground(sync.status.startsWith("Sincronizzato") || sync.busy ? Ui.DIM : Ui.DANGER);
        weekHead.revalidate();
        frame.getContentPane().repaint();
        if (trayIcon != null) trayIcon.setToolTip("Calendario" + (sync.status.isEmpty() ? "" : " · " + sync.status));
    }

    // ---------- Views.Host ----------

    @Override public void open(Model.Occ o) {
        EventDialog.open(frame, sync, store, o, null, 0, 0);
        afterEdit();
    }

    @Override public void create(LocalDate day, int hour, int minute) {
        if (!sync.configured()) { settings(); return; }
        EventDialog.open(frame, sync, store, null, day, hour, minute);
        afterEdit();
    }

    private void afterEdit() {
        refresh();
        if (reminders != null) reminders.check();
    }

    @Override public void goDay(LocalDate day) {
        anchor = day;
        setView(DAY);
    }

}
