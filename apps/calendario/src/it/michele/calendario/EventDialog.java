package it.michele.calendario;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Crea, modifica ed elimina un evento. I compleanni e i calendari in sola lettura si possono solo vedere. */
final class EventDialog {
    private static final int[] REMINDERS = {-1, 0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080};
    private static final String[] REPEATS = {"", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"};
    private static final String[] REPEAT_NAMES = {"Mai", "Ogni giorno", "Ogni settimana", "Ogni mese", "Ogni anno"};

    private final Window owner;
    private final Sync sync;
    private final Store store;
    private final Model.Occ occ; // null per un evento nuovo
    private final JDialog d;

    private final JTextField title = Ui.field("Titolo"), location = Ui.field("Luogo"), startTime = Ui.field("hh:mm"), endTime = Ui.field("hh:mm");
    private final JTextArea notes = Ui.area();
    private final Ui.Toggle allDay = new Ui.Toggle(false);
    private final DateButton startDate = new DateButton(), endDate = new DateButton();
    private Ui.Select<Model.Coll> calendar;
    private Ui.Select<String> repeat;
    private Ui.Select<Integer> reminder;
    private final JLabel error = Ui.label(" ", 12, Ui.DANGER, false);
    private String keepRepeat; // regola complessa da non toccare
    private Integer origReminder;

    static void open(Window owner, Sync sync, Store store, Model.Occ occ, LocalDate day, int hour, int minute) {
        new EventDialog(owner, sync, store, occ, day, hour, minute).d.setVisible(true);
    }

    private EventDialog(Window owner, Sync sync, Store store, Model.Occ occ, LocalDate day, int hour, int minute) {
        this.owner = owner;
        this.sync = sync;
        this.store = store;
        this.occ = occ;
        boolean editable = occ == null || occ.editable();
        d = Ui.dialog(owner, occ == null ? "Nuovo evento" : editable ? "Modifica evento" : "Evento");

        List<Model.Coll> writable = new ArrayList<>();
        for (Model.Coll c : sync.colls) if (c.kind == Model.CAL && !c.readOnly) writable.add(c);
        if (occ != null && occ.coll != null && !writable.contains(occ.coll)) writable.add(0, occ.coll);
        calendar = new Ui.Select<>(writable, c -> c.name);
        String last = store.get("ultimo_calendario", "");
        for (Model.Coll c : writable) if (c.url.equals(last)) calendar.set(c);

        List<String> reps = new ArrayList<>(Arrays.asList(REPEATS));
        List<Integer> rems = new ArrayList<>();
        for (int r : REMINDERS) rems.add(r);

        ZonedDateTime s, e;
        if (occ == null) {
            boolean ad = hour < 0;
            s = ZonedDateTime.of(day, ad ? LocalTime.MIDNIGHT : LocalTime.of(hour, minute), ZoneId.systemDefault());
            e = ad ? s.plusDays(1) : s.plusHours(1);
            allDay.set(ad);
            origReminder = ad ? -1 : store.defaultReminder();
        } else {
            s = occ.start;
            e = occ.end;
            allDay.set(occ.allDay);
            if (occ.comp != null) {
                title.setText(nz(occ.comp.text("SUMMARY")));
                location.setText(nz(occ.comp.text("LOCATION")));
                notes.setText(nz(occ.comp.text("DESCRIPTION")));
                origReminder = Edit.reminderOf(occ.comp);
                String rule = occ.comp.value("RRULE");
                if (rule != null && !Rrule.simple(rule)) { keepRepeat = rule; reps.add(0, null); }
            } else {
                title.setText(occ.title());
                origReminder = -1;
            }
            if (occ.coll != null) calendar.set(occ.coll);
        }
        if (origReminder == null) rems.add(0, null);
        else if (!rems.contains(origReminder)) { rems.add(origReminder); rems.sort((a, b) -> Integer.compare(a, b)); }
        reminder = new Ui.Select<>(rems, EventDialog::reminderName);
        reminder.set(origReminder);
        repeat = new Ui.Select<>(reps, r -> r == null ? new Rrule(keepRepeat).describe() : REPEAT_NAMES[Arrays.asList(REPEATS).indexOf(r)]);
        if (keepRepeat != null) repeat.set(null);
        else if (occ != null && occ.comp != null && occ.comp.value("RRULE") != null)
            repeat.set(new Rrule(occ.comp.value("RRULE")).freq);
        else repeat.set("");
        boolean override = occ != null && occ.recurring && occ.comp != null && occ.comp.get("RECURRENCE-ID") != null;
        if (override) repeat.setEnabled(false);

        startDate.set(s.toLocalDate());
        startTime.setText(Ui.time(s));
        endDate.set(occ != null && occ.allDay || occ == null && hour < 0 ? e.toLocalDate().minusDays(1) : e.toLocalDate());
        endTime.setText(Ui.time(e));
        allDay.onChange = v -> times();
        startDate.onPick = nd -> {
            long len = java.time.temporal.ChronoUnit.DAYS.between(startDate.prev, endDate.value);
            endDate.set(nd.plusDays(Math.max(0, len)));
        };

        // ---------- disposizione ----------
        JPanel body = Ui.column();
        body.setBorder(Ui.pad(20, 20, 16, 20));
        body.setBackground(Ui.PANEL);
        body.setOpaque(true);
        title.setFont(Ui.font(16, true));
        body.add(Ui.labeled("Titolo", title));
        body.add(Ui.gap(12));
        if (occ == null || occ.birthdayName == null) {
            body.add(Ui.labeled("Calendario", calendar));
            body.add(Ui.gap(12));
        }
        JPanel adRow = new JPanel(new BorderLayout());
        adRow.setOpaque(false);
        adRow.add(Ui.label("Tutto il giorno", 13, Ui.TEXT, false), BorderLayout.CENTER);
        adRow.add(allDay, BorderLayout.EAST);
        adRow.setAlignmentX(0);
        adRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        body.add(adRow);
        body.add(Ui.gap(12));
        body.add(Ui.labeled("Inizio", dateTime(startDate, startTime)));
        body.add(Ui.gap(12));
        body.add(Ui.labeled("Fine", dateTime(endDate, endTime)));
        body.add(Ui.gap(12));
        JPanel two = new JPanel(new GridLayout(1, 2, 12, 0));
        two.setOpaque(false);
        two.add(Ui.labeled("Ripeti", repeat));
        two.add(Ui.labeled("Promemoria", reminder));
        two.setAlignmentX(0);
        two.setMaximumSize(new Dimension(Integer.MAX_VALUE, two.getPreferredSize().height));
        body.add(two);
        body.add(Ui.gap(12));
        body.add(Ui.labeled("Luogo", location));
        body.add(Ui.gap(12));
        JScrollPane ns = Ui.scroll(notes);
        ns.setBorder(new Ui.FieldBorder());
        ns.setPreferredSize(new Dimension(380, 90));
        ns.getViewport().setBackground(Ui.BG);
        body.add(Ui.labeled("Note", ns));
        body.add(Ui.gap(8));
        error.setAlignmentX(0);
        body.add(error);
        body.add(Ui.gap(8));

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.setAlignmentX(0);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        if (editable) {
            if (occ != null) buttons.add(Ui.button("Elimina", Ui.DANGER_BTN, this::delete), BorderLayout.WEST);
            right.add(Ui.button("Annulla", Ui.GHOST, d::dispose));
            Ui.Btn save = Ui.button("Salva", Ui.PRIMARY, this::save);
            right.add(save);
            d.getRootPane().setDefaultButton(save);
        } else {
            right.add(Ui.button("Chiudi", Ui.PRIMARY, d::dispose));
            for (java.awt.Component c : new java.awt.Component[]{title, location, notes, startTime, endTime, startDate, endDate,
                    allDay, calendar, repeat, reminder}) c.setEnabled(false);
            title.setEditable(false); location.setEditable(false); notes.setEditable(false);
            String why = occ.birthdayName != null ? "Compleanno dai contatti: si modifica nella rubrica."
                    : "Calendario in sola lettura.";
            error.setForeground(Ui.OFF);
            error.setText(why);
        }
        buttons.add(right, BorderLayout.EAST);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        body.add(buttons);
        if (writable.isEmpty() && editable) {
            error.setText("Nessun calendario scrivibile: configura il server nelle impostazioni");
        }

        times();
        d.setContentPane(body);
        d.pack();
        d.setSize(Math.max(460, d.getWidth()), d.getHeight());
        d.setMinimumSize(d.getSize());
        d.setLocationRelativeTo(owner);
        title.requestFocusInWindow();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String reminderName(Integer m) {
        if (m == null) return "Come impostato";
        if (m < 0) return "Nessuno";
        if (m == 0) return "All'inizio";
        if (m % 10080 == 0) return m / 10080 == 1 ? "1 settimana prima" : m / 10080 + " settimane prima";
        if (m % 1440 == 0) return m / 1440 == 1 ? "1 giorno prima" : m / 1440 + " giorni prima";
        if (m % 60 == 0) return m / 60 == 1 ? "1 ora prima" : m / 60 + " ore prima";
        return m + " minuti prima";
    }

    private JPanel dateTime(DateButton date, JTextField time) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.add(date, BorderLayout.CENTER);
        time.setPreferredSize(new Dimension(90, 36));
        p.add(time, BorderLayout.EAST);
        return p;
    }

    private void times() {
        startTime.setVisible(!allDay.on);
        endTime.setVisible(!allDay.on);
    }

    /** "9", "930", "9:30", "09.30" → 09:30 */
    static LocalTime parseTime(String s) {
        String t = s.trim().replace('.', ':');
        try {
            if (t.matches("\\d{1,2}")) return LocalTime.of(Integer.parseInt(t), 0);
            if (t.matches("\\d{3,4}")) return LocalTime.of(Integer.parseInt(t.substring(0, t.length() - 2)), Integer.parseInt(t.substring(t.length() - 2)));
            if (t.matches("\\d{1,2}:\\d{2}")) {
                String[] p = t.split(":");
                return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Edit.Data read() {
        Edit.Data x = new Edit.Data();
        x.title = title.getText();
        x.location = location.getText();
        x.notes = notes.getText();
        x.allDay = allDay.on;
        ZoneId z = ZoneId.systemDefault();
        if (x.allDay) {
            x.start = startDate.value.atStartOfDay(z);
            x.end = endDate.value.plusDays(1).atStartOfDay(z);
            if (!x.end.isAfter(x.start)) x.end = x.start.plusDays(1);
        } else {
            LocalTime st = parseTime(startTime.getText()), et = parseTime(endTime.getText());
            if (st == null || et == null) { error.setText("Orario non valido: usa hh:mm"); return null; }
            x.start = ZonedDateTime.of(startDate.value, st, z);
            x.end = ZonedDateTime.of(endDate.value, et, z);
            if (x.end.isBefore(x.start)) { error.setText("La fine è prima dell'inizio"); return null; }
        }
        String r = repeat.get();
        String orig = occ == null || occ.comp == null ? null : occ.comp.value("RRULE");
        if (r == null) x.repeat = null;                                    // regola complessa: invariata
        else if (orig != null && Rrule.simple(orig) && new Rrule(orig).freq.equals(r)) x.repeat = null;
        else if (orig == null && r.isEmpty()) x.repeat = null;
        else x.repeat = r;
        if (occ == null) x.repeat = r == null || r.isEmpty() ? null : r;
        Integer rem = reminder.get();
        x.reminder = occ == null ? rem : rem == null || rem.equals(origReminder) ? null : rem;
        return x;
    }

    private void busy(boolean b) {
        d.setCursor(java.awt.Cursor.getPredefinedCursor(b ? java.awt.Cursor.WAIT_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
        if (b) error.setForeground(Ui.OFF);
        error.setText(b ? "Salvataggio sul server..." : " ");
    }

    private void done(String err) {
        busy(false);
        if (err == null) d.dispose();
        else { error.setForeground(Ui.DANGER); error.setText(err); }
    }

    private void save() {
        Edit.Data x = read();
        if (x == null) return;
        Model.Coll target = calendar.get();
        if (target == null) { error.setText("Scegli un calendario"); return; }
        store.put("ultimo_calendario", target.url);
        store.save();
        if (occ == null) {
            Ical.Comp cal = Edit.create(x);
            busy(true);
            sync.put(target, Edit.newHref(target, cal), null, cal, this::done);
            return;
        }
        Ical.Comp root;
        if (occ.recurring && occ.rid != null && x.repeat == null) {
            int c = Ui.ask(d, "Evento ripetuto", "Vuoi modificare solo questo evento o tutta la serie?", -1,
                    "Annulla", "Solo questo", "Tutta la serie");
            if (c <= 0) return;
            root = c == 1 ? Edit.editOne(occ, x) : Edit.editSeries(occ, x);
        } else root = Edit.editSeries(occ, x);
        busy(true);
        if (target != occ.coll) {
            // spostamento in un altro calendario: crea là, poi elimina qui
            Model.Coll from = occ.coll;
            Model.Res res = occ.res;
            sync.put(target, target.url + Dav.lastSegment(res.href), null, root, err -> {
                if (err != null) { done(err); return; }
                sync.delete(from, res, this::done);
            });
        } else sync.put(occ.coll, occ.res.href, occ.res.etag, root, this::done);
    }

    private void delete() {
        if (occ.recurring && occ.rid != null) {
            int c = Ui.ask(d, "Elimina evento ripetuto", "Vuoi eliminare solo questo evento o tutta la serie?", 2,
                    "Annulla", "Solo questo", "Tutta la serie");
            if (c <= 0) return;
            busy(true);
            if (c == 1) {
                Ical.Comp root = Edit.deleteOne(occ);
                if (root != null) { sync.put(occ.coll, occ.res.href, occ.res.etag, root, this::done); return; }
            }
            sync.delete(occ.coll, occ.res, this::done);
            return;
        }
        if (Ui.ask(d, "Elimina evento", "Eliminare \"" + occ.title() + "\"? Viene tolto anche dal server.", 1,
                "Annulla", "Elimina") != 1) return;
        busy(true);
        sync.delete(occ.coll, occ.res, this::done);
    }

    /** Pulsante con la data; apre un mini mese. */
    static final class DateButton extends Ui.Btn {
        LocalDate value = LocalDate.now(), prev = value;
        java.util.function.Consumer<LocalDate> onPick = x -> { };

        DateButton() {
            super("", "calendar", Ui.SECONDARY);
            setHorizontalAlignment(LEFT);
            setFont(Ui.font(13, false));
            addActionListener(e -> pick());
        }

        void set(LocalDate v) {
            value = v;
            setText(Ui.day(v) + " " + v.getYear());
        }

        private void pick() {
            JPopupMenu m = Ui.popup();
            Views.Mini mini = new Views.Mini();
            mini.shown = value.withDayOfMonth(1);
            mini.selected = value;
            mini.setBorder(Ui.pad(8, 8, 8, 8));
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBackground(Ui.RAISED);
            wrap.setBorder(Ui.pad(8, 10, 4, 10));
            wrap.add(mini);
            m.add(wrap);
            mini.onPick = dd -> {
                prev = value;
                set(dd);
                m.setVisible(false);
                onPick.accept(dd);
            };
            m.show(this, 0, getHeight() + 2);
        }
    }
}
