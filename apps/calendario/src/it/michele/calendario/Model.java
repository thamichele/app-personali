package it.michele.calendario;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dati in memoria: calendari e rubriche sincronizzati, con le loro risorse (.ics/.vcf). */
final class Model {
    private Model() {}

    static final int CAL = 0, CARD = 1;

    /** Una collezione CalDAV (calendario) o CardDAV (rubrica). */
    static final class Coll {
        String url, name, color, ctag;
        int kind;
        boolean readOnly;
        /** href → risorsa */
        final Map<String, Res> items = new LinkedHashMap<>();
    }

    static final class Res {
        final String href;
        String etag, data;
        private Ical.Comp parsed;

        Res(String href, String etag, String data) { this.href = href; this.etag = etag; this.data = data; }

        Ical.Comp root() {
            if (parsed == null) {
                try { parsed = Ical.parse(data); } catch (Exception e) { parsed = new Ical.Comp("VCALENDAR"); }
            }
            return parsed;
        }
    }

    /** Una singola volta di un evento (le ripetizioni sono espanse). */
    static final class Occ {
        Coll coll;
        Res res;
        Ical.Comp comp;      // VEVENT (principale o eccezione)
        ZonedDateTime start, end;
        boolean allDay, recurring;
        ZonedDateTime rid;   // inizio originale della ripetizione, null per gli eventi singoli
        String birthdayName; // compleanno ricavato da un contatto: sola lettura

        String title() {
            if (birthdayName != null) return "Compleanno: " + birthdayName;
            String s = comp.text("SUMMARY");
            return s == null || s.isBlank() ? "(senza titolo)" : s.trim();
        }

        String location() { return comp == null ? null : comp.text("LOCATION"); }

        boolean editable() { return birthdayName == null && coll != null && !coll.readOnly; }

        /** Promemoria come istanti, calcolati da TRIGGER relativo all'inizio/fine o assoluto. */
        List<Instant> alarms() {
            List<Instant> out = new ArrayList<>();
            if (comp == null) return out;
            for (Ical.Comp a : comp.children("VALARM")) {
                Ical.Prop t = a.get("TRIGGER");
                if (t == null) continue;
                try {
                    if ("DATE-TIME".equalsIgnoreCase(t.param("VALUE")) || t.value.trim().endsWith("Z") && !t.value.contains("P"))
                        out.add(Ical.time(t).toInstant());
                    else {
                        ZonedDateTime ref = "END".equalsIgnoreCase(t.param("RELATED")) ? end : start;
                        out.add(ref.plus(Ical.duration(t.value)).toInstant());
                    }
                } catch (Exception ignored) { }
            }
            return out;
        }
    }

    /** Durata dell'evento da DTEND o DURATION (predefinita: un giorno per le date pure, zero per gli orari). */
    static Duration length(Ical.Comp ev, ZonedDateTime start, boolean allDay) {
        Ical.Prop end = ev.get("DTEND");
        try {
            if (end != null) {
                ZonedDateTime e = Ical.time(end);
                if (allDay) return Duration.ofDays(Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), e.toLocalDate())));
                return Duration.between(start, e).isNegative() ? Duration.ZERO : Duration.between(start, e);
            }
            String d = ev.value("DURATION");
            if (d != null) return Ical.duration(d);
        } catch (Exception ignored) { }
        return allDay ? Duration.ofDays(1) : Duration.ZERO;
    }

    static ZonedDateTime endOf(ZonedDateTime start, Duration len, boolean allDay) {
        return allDay ? start.plusDays(len.toDays()) : start.plus(len);
    }

    /** Tutte le volte degli eventi nelle collezioni visibili che toccano l'intervallo [from, to). */
    static List<Occ> occurrences(List<Coll> colls, Set<String> hidden, boolean birthdays, ZonedDateTime from, ZonedDateTime to) {
        List<Occ> out = new ArrayList<>();
        for (Coll c : colls) {
            if (c.kind == CARD) {
                if (birthdays) for (Res r : c.items.values()) birthdays(c, r, from, to, out);
                continue;
            }
            if (hidden.contains(c.url)) continue;
            for (Res r : c.items.values()) {
                try { expand(c, r, from, to, out); } catch (Exception ignored) { }
            }
        }
        out.sort((a, b) -> a.allDay != b.allDay ? (a.allDay ? -1 : 1) : a.start.compareTo(b.start));
        return out;
    }

    private static void expand(Coll c, Res r, ZonedDateTime from, ZonedDateTime to, List<Occ> out) {
        List<Ical.Comp> events = r.root().children("VEVENT");
        if (events.isEmpty()) return;
        Ical.Comp master = null;
        Map<Instant, Ical.Comp> overrides = new HashMap<>();
        for (Ical.Comp e : events) {
            Ical.Prop rid = e.get("RECURRENCE-ID");
            if (rid == null) master = e;
            else overrides.put(Ical.time(rid).toInstant(), e);
        }
        boolean recurring = master != null && (master.get("RRULE") != null || master.get("RDATE") != null);
        if (master != null) {
            Ical.Prop ds = master.get("DTSTART");
            if (ds == null) return;
            boolean allDay = Ical.isDate(ds);
            ZonedDateTime start = Ical.time(ds);
            Duration len = length(master, start, allDay);
            List<ZonedDateTime> starts;
            if (master.get("RRULE") != null) starts = new Rrule(master.value("RRULE")).expand(start, to);
            else { starts = new ArrayList<>(); starts.add(start); }
            for (Ical.Prop rd : master.all("RDATE"))
                for (String v : rd.value.split(",")) {
                    try { if (!v.contains("/")) starts.add(Ical.time(v.trim(), rd.param("TZID"))); } catch (Exception ignored) { }
                }
            Set<Instant> ex = new HashSet<>();
            for (Ical.Prop exd : master.all("EXDATE"))
                for (String v : exd.value.split(",")) {
                    try {
                        ZonedDateTime t = Ical.time(v.trim(), exd.param("TZID"));
                        // un'eccezione a data pura cancella l'occorrenza di quel giorno
                        ex.add(v.trim().length() == 8 ? ZonedDateTime.of(t.toLocalDate(), start.toLocalTime(), start.getZone()).toInstant() : t.toInstant());
                    } catch (Exception ignored) { }
                }
            for (ZonedDateTime s : starts) {
                if (ex.contains(s.toInstant()) || overrides.containsKey(s.toInstant())) continue;
                ZonedDateTime e = endOf(s, len, allDay);
                if (!overlaps(s, e, allDay, from, to)) continue;
                Occ o = new Occ();
                o.coll = c; o.res = r; o.comp = master; o.start = s; o.end = e; o.allDay = allDay;
                o.recurring = recurring;
                o.rid = recurring ? s : null;
                out.add(o);
            }
        }
        for (Map.Entry<Instant, Ical.Comp> en : overrides.entrySet()) {
            Ical.Comp e = en.getValue();
            Ical.Prop ds = e.get("DTSTART");
            if (ds == null) continue;
            if ("CANCELLED".equalsIgnoreCase(e.value("STATUS"))) continue;
            boolean allDay = Ical.isDate(ds);
            ZonedDateTime s = Ical.time(ds);
            ZonedDateTime end = endOf(s, length(e, s, allDay), allDay);
            if (!overlaps(s, end, allDay, from, to)) continue;
            Occ o = new Occ();
            o.coll = c; o.res = r; o.comp = e; o.start = s; o.end = end; o.allDay = allDay;
            o.recurring = master != null;
            o.rid = Ical.time(e.get("RECURRENCE-ID")).withZoneSameInstant(ZoneId.systemDefault());
            if (master == null) o.rid = null;
            out.add(o);
        }
    }

    private static boolean overlaps(ZonedDateTime s, ZonedDateTime e, boolean allDay, ZonedDateTime from, ZonedDateTime to) {
        if (!e.isAfter(s)) e = s.plusMinutes(allDay ? 1440 : 1);
        return s.isBefore(to) && e.isAfter(from);
    }

    // ---------- contatti ----------

    static final class Contact {
        String name, org;
        LocalDate birthday;  // anno 0 se sconosciuto
        final List<String> phones = new ArrayList<>(), emails = new ArrayList<>();
    }

    static List<Contact> contacts(Ical.Comp root) {
        List<Contact> out = new ArrayList<>();
        List<Ical.Comp> cards = root.name.equals("VCARD") ? List.of(root) : root.children("VCARD");
        for (Ical.Comp v : cards) {
            Contact c = new Contact();
            c.name = v.text("FN");
            if (c.name == null || c.name.isBlank()) {
                String n = v.text("N");
                c.name = n == null ? "" : n.replace(";", " ").trim().replaceAll(" +", " ");
            }
            String org = v.text("ORG");
            c.org = org == null ? null : org.replace(";", " ").trim();
            for (Ical.Prop p : v.all("TEL")) c.phones.add(Ical.unescape(p.value).replaceFirst("^tel:", ""));
            for (Ical.Prop p : v.all("EMAIL")) c.emails.add(Ical.unescape(p.value).replaceFirst("^mailto:", ""));
            c.birthday = birthday(v.value("BDAY"));
            if (!c.name.isEmpty() || c.org != null) out.add(c);
        }
        return out;
    }

    /** BDAY: 19800315, 1980-03-15, --0315, --03-15, anche con l'ora. */
    static LocalDate birthday(String v) {
        if (v == null) return null;
        String s = v.trim();
        int t = s.indexOf('T');
        if (t > 0) s = s.substring(0, t);
        s = s.replace("-", "");
        try {
            if (s.length() == 8) return LocalDate.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)), Integer.parseInt(s.substring(6, 8)));
            if (s.length() == 4) return LocalDate.of(0, Integer.parseInt(s.substring(0, 2)), Integer.parseInt(s.substring(2, 4)));
        } catch (Exception ignored) { }
        return null;
    }

    private static void birthdays(Coll c, Res r, ZonedDateTime from, ZonedDateTime to, List<Occ> out) {
        for (Contact ct : contacts(r.root())) {
            if (ct.birthday == null) continue;
            for (int y = from.getYear(); y <= to.getYear(); y++) {
                LocalDate d;
                try { d = ct.birthday.withYear(y); } catch (Exception e) { continue; }
                if (ct.birthday.getMonthValue() == 2 && ct.birthday.getDayOfMonth() == 29 && !d.isLeapYear()) d = LocalDate.of(y, 2, 28);
                ZonedDateTime s = d.atStartOfDay(ZoneId.systemDefault());
                if (!overlaps(s, s.plusDays(1), true, from, to)) continue;
                Occ o = new Occ();
                o.coll = c; o.res = r; o.start = s; o.end = s.plusDays(1); o.allDay = true;
                int age = ct.birthday.getYear() > 0 ? y - ct.birthday.getYear() : -1;
                o.birthdayName = ct.name + (age > 0 ? " (" + age + ")" : "");
                out.add(o);
            }
        }
    }
}
