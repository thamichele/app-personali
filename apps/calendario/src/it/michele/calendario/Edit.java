package it.michele.calendario;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modifiche agli eventi sul testo iCalendar: cambia solo i campi toccati e mantiene il resto. */
final class Edit {
    private Edit() {}

    /** Valori del dialogo. repeat: null = lascia com'è, "" = nessuna, "DAILY"...; reminder: null = lascia, -1 = nessuno. */
    static final class Data {
        String title = "", location = "", notes = "";
        boolean allDay;
        ZonedDateTime start, end; // per tutto il giorno: end è il giorno dopo l'ultimo (esclusivo)
        String repeat;
        Integer reminder;
    }

    static Ical.Comp create(Data d) {
        Ical.Comp cal = new Ical.Comp("VCALENDAR");
        cal.props.add(new Ical.Prop("VERSION", "2.0"));
        cal.props.add(new Ical.Prop("PRODID", "-//Michele//Calendario " + Main.VERSION + "//IT"));
        Ical.Comp ev = new Ical.Comp("VEVENT");
        ev.props.add(new Ical.Prop("UID", UUID.randomUUID().toString()));
        ev.props.add(new Ical.Prop("CREATED", Ical.utc(Instant.now())));
        cal.children.add(ev);
        setTimes(ev, d.start, d.end, d.allDay);
        fields(ev, d);
        if (d.repeat != null && !d.repeat.isEmpty()) ev.set("RRULE", "FREQ=" + d.repeat);
        if (d.reminder != null) alarms(ev, d.reminder, d.title);
        stamp(ev, false);
        timezones(cal);
        return cal;
    }

    static String newHref(Model.Coll c, Ical.Comp cal) {
        return c.url + cal.children("VEVENT").get(0).value("UID") + ".ics";
    }

    private static Ical.Comp master(Ical.Comp root) {
        for (Ical.Comp e : root.children("VEVENT")) if (e.get("RECURRENCE-ID") == null) return e;
        return null;
    }

    private static Ical.Comp override(Ical.Comp root, ZonedDateTime rid) {
        for (Ical.Comp e : root.children("VEVENT")) {
            Ical.Prop r = e.get("RECURRENCE-ID");
            if (r != null && Ical.time(r).toInstant().equals(rid.toInstant())) return e;
        }
        return null;
    }

    /** Modifica dell'evento singolo o dell'intera serie a partire da una sua occorrenza. */
    static Ical.Comp editSeries(Model.Occ occ, Data d) {
        Ical.Comp root = occ.res.root().copy();
        Ical.Comp ev = master(root);
        if (ev == null) ev = root.children("VEVENT").get(0);
        boolean timesChanged = d.allDay != occ.allDay || !d.start.toInstant().equals(occ.start.toInstant())
                || !d.end.toInstant().equals(occ.end.toInstant());
        if (timesChanged) {
            Ical.Prop ds = ev.get("DTSTART");
            ZonedDateTime oldStart = Ical.time(ds);
            long dayShift = ChronoUnit.DAYS.between(occ.start.toLocalDate(), d.start.toLocalDate());
            LocalTime tod = d.start.withZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
            ZonedDateTime newStart = shift(oldStart, dayShift, tod, d.allDay);
            ZonedDateTime newEnd = d.allDay ? newStart.plusDays(Math.max(1, ChronoUnit.DAYS.between(d.start.toLocalDate(), d.end.toLocalDate())))
                    : newStart.plus(Duration.between(d.start, d.end));
            setTimes(ev, newStart, newEnd, d.allDay);
            Ical.Prop nds = ev.get("DTSTART");
            // le eccezioni seguono la serie: stesse date spostate, nel nuovo formato
            for (Ical.Prop ex : ev.all("EXDATE")) {
                StringBuilder b = new StringBuilder();
                for (String v : ex.value.split(",")) {
                    try {
                        ZonedDateTime t = shift(Ical.time(v.trim(), ex.param("TZID")), dayShift, tod, d.allDay);
                        b.append(b.length() > 0 ? "," : "").append(Ical.likeProp("EXDATE", t, nds).value);
                    } catch (Exception ignored) { }
                }
                Ical.Prop np = Ical.likeProp("EXDATE", newStart, nds);
                ex.value = b.toString();
                ex.params.clear();
                ex.params.putAll(np.params);
            }
            ev.props.removeIf(p -> p.name.equals("EXDATE") && p.value.isEmpty());
            for (Ical.Comp o : root.children("VEVENT")) {
                Ical.Prop rid = o.get("RECURRENCE-ID");
                if (rid == null) continue;
                Ical.Prop nr = Ical.likeProp("RECURRENCE-ID", shift(Ical.time(rid), dayShift, tod, d.allDay), nds);
                rid.value = nr.value;
                rid.params.clear();
                rid.params.putAll(nr.params);
            }
        }
        fields(ev, d);
        if (d.repeat != null) {
            if (d.repeat.isEmpty()) {
                ev.remove("RRULE"); ev.remove("RDATE"); ev.remove("EXDATE");
                root.children.removeIf(c -> c.name.equals("VEVENT") && c.get("RECURRENCE-ID") != null);
            } else ev.set("RRULE", "FREQ=" + d.repeat);
        }
        if (d.reminder != null) alarms(ev, d.reminder, d.title);
        stamp(ev, true);
        timezones(root);
        return root;
    }

    private static ZonedDateTime shift(ZonedDateTime t, long days, LocalTime tod, boolean allDay) {
        ZonedDateTime local = t.withZoneSameInstant(ZoneId.systemDefault());
        return ZonedDateTime.of(local.toLocalDate().plusDays(days), allDay ? LocalTime.MIDNIGHT : tod, ZoneId.systemDefault());
    }

    /** Modifica di una sola occorrenza di una serie: eccezione con RECURRENCE-ID. */
    static Ical.Comp editOne(Model.Occ occ, Data d) {
        Ical.Comp root = occ.res.root().copy();
        Ical.Comp ev = override(root, occ.rid);
        if (ev == null) {
            Ical.Comp m = master(root);
            ev = m.copy();
            ev.remove("RRULE"); ev.remove("RDATE"); ev.remove("EXDATE");
            ev.props.add(Ical.likeProp("RECURRENCE-ID", occ.rid, m.get("DTSTART")));
            root.children.add(ev);
        }
        setTimes(ev, d.start, d.end, d.allDay);
        fields(ev, d);
        if (d.reminder != null) alarms(ev, d.reminder, d.title);
        stamp(ev, true);
        timezones(root);
        return root;
    }

    /** Toglie una sola occorrenza (EXDATE); null se non resta niente e la risorsa va eliminata. */
    static Ical.Comp deleteOne(Model.Occ occ) {
        Ical.Comp root = occ.res.root().copy();
        Ical.Comp m = master(root);
        Ical.Comp o = override(root, occ.rid);
        if (o != null) root.children.remove(o);
        if (m == null) return root.children("VEVENT").isEmpty() ? null : root;
        m.props.add(Ical.likeProp("EXDATE", occ.rid, m.get("DTSTART")));
        stamp(m, true);
        timezones(root);
        return root;
    }

    private static void setTimes(Ical.Comp ev, ZonedDateTime start, ZonedDateTime end, boolean allDay) {
        Ical.Prop s = Ical.timeProp("DTSTART", start, allDay), e = Ical.timeProp("DTEND", end, allDay);
        ev.set("DTSTART", s.value).params.putAll(s.params);
        ev.remove("DURATION");
        ev.set("DTEND", e.value).params.putAll(e.params);
    }

    private static void fields(Ical.Comp ev, Data d) {
        ev.setText("SUMMARY", d.title.trim());
        ev.setText("LOCATION", d.location.trim());
        ev.setText("DESCRIPTION", d.notes.trim());
    }

    private static void alarms(Ical.Comp ev, int minutes, String title) {
        ev.children.removeIf(c -> c.name.equals("VALARM"));
        if (minutes < 0) return;
        Ical.Comp a = new Ical.Comp("VALARM");
        a.props.add(new Ical.Prop("ACTION", "DISPLAY"));
        a.props.add(new Ical.Prop("DESCRIPTION", Ical.escape(title.isBlank() ? "Promemoria" : title.trim())));
        a.props.add(new Ical.Prop("TRIGGER", minutes == 0 ? "PT0S" : Ical.duration(Duration.ofMinutes(-minutes))));
        ev.children.add(a);
    }

    private static void stamp(Ical.Comp ev, boolean modified) {
        String now = Ical.utc(Instant.now());
        ev.set("DTSTAMP", now);
        ev.set("LAST-MODIFIED", now);
        if (modified) {
            int seq = 0;
            try { seq = Integer.parseInt(ev.value("SEQUENCE").trim()); } catch (Exception ignored) { }
            ev.set("SEQUENCE", String.valueOf(seq + 1));
        }
    }

    /** Aggiunge il VTIMEZONE di ogni TZID usato che non ne ha già uno. */
    static void timezones(Ical.Comp root) {
        Set<String> have = new HashSet<>();
        for (Ical.Comp tz : root.children("VTIMEZONE")) have.add(tz.value("TZID"));
        Set<String> used = new HashSet<>();
        for (Ical.Comp ev : root.children("VEVENT"))
            for (Ical.Prop p : ev.props) if (p.param("TZID") != null) used.add(p.param("TZID"));
        int at = 0;
        for (int i = 0; i < root.children.size(); i++) if (root.children.get(i).name.equals("VTIMEZONE")) at = i + 1;
        for (String id : used) {
            if (have.contains(id)) continue;
            try {
                root.children.add(at++, Ical.vtimezone(ZoneId.of(id)));
            } catch (Exception ignored) { }
        }
    }

    /** Minuti prima dell'inizio del primo promemoria relativo, -1 se nessuno, null se non rappresentabile. */
    static Integer reminderOf(Ical.Comp ev) {
        List<Ical.Comp> alarms = ev.children("VALARM");
        if (alarms.isEmpty()) return -1;
        Ical.Prop t = alarms.get(0).get("TRIGGER");
        if (t == null || "END".equalsIgnoreCase(t.param("RELATED")) || "DATE-TIME".equalsIgnoreCase(t.param("VALUE"))) return null;
        try {
            long m = -Ical.duration(t.value).toMinutes();
            return m >= 0 && alarms.size() == 1 ? (int) m : null;
        } catch (Exception e) {
            return null;
        }
    }
}
