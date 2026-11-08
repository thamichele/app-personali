package it.michele.calendario;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lettura e scrittura di iCalendar (RFC 5545) e vCard, che hanno la stessa sintassi a righe.
 * Le proprietà sconosciute restano intatte: modificando un evento si cambiano solo i campi toccati.
 */
final class Ical {
    private Ical() {}

    static final class Prop {
        String name, value;
        final Map<String, String> params = new LinkedHashMap<>();

        Prop(String name, String value) { this.name = name; this.value = value; }

        String param(String p) { return params.get(p); }

        Prop copy() {
            Prop c = new Prop(name, value);
            c.params.putAll(params);
            return c;
        }
    }

    static final class Comp {
        final String name;
        final List<Prop> props = new ArrayList<>();
        final List<Comp> children = new ArrayList<>();

        Comp(String name) { this.name = name; }

        Prop get(String n) {
            for (Prop p : props) if (p.name.equals(n)) return p;
            return null;
        }

        String value(String n) {
            Prop p = get(n);
            return p == null ? null : p.value;
        }

        /** Testo con le sequenze di escape risolte (SUMMARY, DESCRIPTION, ...). */
        String text(String n) {
            Prop p = get(n);
            return p == null ? null : unescape(p.value);
        }

        List<Prop> all(String n) {
            List<Prop> l = new ArrayList<>();
            for (Prop p : props) if (p.name.equals(n)) l.add(p);
            return l;
        }

        void remove(String n) { props.removeIf(p -> p.name.equals(n)); }

        /** Sostituisce tutte le proprietà con quel nome (o le toglie con value null) mantenendo la posizione. */
        Prop set(String n, String value) {
            int at = -1;
            for (int i = 0; i < props.size(); i++) if (props.get(i).name.equals(n)) { at = i; break; }
            remove(n);
            if (value == null) return null;
            Prop p = new Prop(n, value);
            props.add(at < 0 || at > props.size() ? props.size() : at, p);
            return p;
        }

        void setText(String n, String value) {
            set(n, value == null || value.isEmpty() ? null : escape(value));
        }

        List<Comp> children(String n) {
            List<Comp> l = new ArrayList<>();
            for (Comp c : children) if (c.name.equals(n)) l.add(c);
            return l;
        }

        Comp copy() {
            Comp c = new Comp(name);
            for (Prop p : props) c.props.add(p.copy());
            for (Comp ch : children) c.children.add(ch.copy());
            return c;
        }
    }

    // ---------- lettura ----------

    /** Restituisce il primo componente di primo livello (VCALENDAR o VCARD); più vCard finiscono in figli di "ROOT". */
    static Comp parse(String text) {
        Comp root = new Comp("ROOT");
        List<Comp> stack = new ArrayList<>();
        stack.add(root);
        for (String line : unfold(text)) {
            Prop p = parseLine(line);
            if (p == null) continue;
            Comp top = stack.get(stack.size() - 1);
            if (p.name.equals("BEGIN")) {
                Comp c = new Comp(p.value.trim().toUpperCase());
                top.children.add(c);
                stack.add(c);
            } else if (p.name.equals("END")) {
                if (stack.size() > 1) stack.remove(stack.size() - 1);
            } else {
                top.props.add(p);
            }
        }
        return root.children.size() == 1 ? root.children.get(0) : root;
    }

    private static List<String> unfold(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        for (String raw : text.split("\r\n|\n|\r")) {
            if (!raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t') && cur != null) {
                cur.append(raw, 1, raw.length());
            } else {
                if (cur != null) out.add(cur.toString());
                cur = new StringBuilder(raw);
            }
        }
        if (cur != null) out.add(cur.toString());
        return out;
    }

    private static Prop parseLine(String line) {
        int colon = -1;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == ':' && !quoted) { colon = i; break; }
        }
        if (colon <= 0) return null;
        String head = line.substring(0, colon);
        List<String> parts = splitOutsideQuotes(head, ';');
        String name = parts.get(0).toUpperCase();
        int dot = name.lastIndexOf('.'); // gruppi vCard: "item1.TEL"
        if (dot >= 0) name = name.substring(dot + 1);
        Prop p = new Prop(name, line.substring(colon + 1));
        for (int i = 1; i < parts.size(); i++) {
            String s = parts.get(i);
            int eq = s.indexOf('=');
            if (eq < 0) { p.params.put(s.toUpperCase(), ""); continue; }
            String v = s.substring(eq + 1);
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            p.params.put(s.substring(0, eq).toUpperCase(), v);
        }
        return p;
    }

    private static List<String> splitOutsideQuotes(String s, char sep) {
        List<String> out = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == sep && !quoted) { out.add(s.substring(start, i)); start = i + 1; }
        }
        out.add(s.substring(start));
        return out;
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' || n == 'N' ? '\n' : n);
            } else b.append(ch);
        }
        return b.toString();
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\n").replace("\n", "\\n");
    }

    // ---------- scrittura ----------

    static String write(Comp c) {
        StringBuilder b = new StringBuilder();
        write(c, b);
        return b.toString();
    }

    private static void write(Comp c, StringBuilder b) {
        b.append("BEGIN:").append(c.name).append("\r\n");
        for (Prop p : c.props) {
            StringBuilder l = new StringBuilder(p.name);
            for (Map.Entry<String, String> e : p.params.entrySet()) {
                String v = e.getValue();
                l.append(';').append(e.getKey()).append('=');
                if (v.matches(".*[:;,].*")) l.append('"').append(v.replace("\"", "")).append('"');
                else l.append(v);
            }
            l.append(':').append(p.value);
            fold(l.toString(), b);
        }
        for (Comp ch : c.children) write(ch, b);
        b.append("END:").append(c.name).append("\r\n");
    }

    /** Righe al massimo di 75 byte UTF-8, senza spezzare i caratteri. */
    private static void fold(String line, StringBuilder b) {
        int bytes = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int n = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + n > 75) { b.append("\r\n "); bytes = 1; }
            b.appendCodePoint(cp);
            bytes += n;
            i += Character.charCount(cp);
        }
        b.append("\r\n");
    }

    // ---------- date ----------

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    static boolean isDate(Prop p) {
        return "DATE".equalsIgnoreCase(p.param("VALUE")) || p.value.trim().length() == 8;
    }

    /** Valore data/ora di una proprietà (DTSTART, EXDATE, ...); le date pure diventano la mezzanotte locale. */
    static ZonedDateTime time(Prop p) {
        return time(p.value.trim(), p.param("TZID"));
    }

    static ZonedDateTime time(String v, String tzid) {
        if (v.length() == 8) return LocalDate.parse(v, DATE).atStartOfDay(ZoneId.systemDefault());
        if (v.endsWith("Z")) return LocalDateTime.parse(v.substring(0, v.length() - 1), LOCAL).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault());
        LocalDateTime l = LocalDateTime.parse(v.length() > 15 ? v.substring(0, 15) : v, LOCAL);
        return l.atZone(zone(tzid));
    }

    private static final Map<String, String> WINDOWS_ZONES = Map.of(
            "W. Europe Standard Time", "Europe/Berlin", "Central Europe Standard Time", "Europe/Budapest",
            "Romance Standard Time", "Europe/Paris", "GMT Standard Time", "Europe/London",
            "UTC", "UTC", "Eastern Standard Time", "America/New_York", "Pacific Standard Time", "America/Los_Angeles");

    /** Fuso dal TZID: nomi IANA, prefissi tipo "/mozilla.org/.../Europe/Rome", alcuni nomi Windows; altrimenti locale. */
    static ZoneId zone(String tzid) {
        if (tzid == null || tzid.isEmpty()) return ZoneId.systemDefault();
        String w = WINDOWS_ZONES.get(tzid);
        if (w != null) return ZoneId.of(w);
        String t = tzid;
        for (int i = 0; i < 3; i++) {
            try {
                return ZoneId.of(t);
            } catch (Exception e) {
                int slash = t.indexOf('/');
                if (slash < 0) break;
                t = t.substring(slash + 1);
            }
        }
        return ZoneId.systemDefault();
    }

    /** Scrive un orario nel fuso locale (TZID) o come data pura. */
    static Prop timeProp(String name, ZonedDateTime t, boolean allDay) {
        if (allDay) {
            Prop p = new Prop(name, t.toLocalDate().format(DATE));
            p.params.put("VALUE", "DATE");
            return p;
        }
        ZoneId z = ZoneId.systemDefault();
        Prop p = new Prop(name, t.withZoneSameInstant(z).toLocalDateTime().format(LOCAL));
        p.params.put("TZID", z.getId());
        return p;
    }

    /** Stesso formato (data, UTC o TZID) di una proprietà di riferimento: serve per EXDATE e RECURRENCE-ID. */
    static Prop likeProp(String name, ZonedDateTime t, Prop ref) {
        if (isDate(ref)) {
            Prop p = new Prop(name, t.toLocalDate().format(DATE));
            p.params.put("VALUE", "DATE");
            return p;
        }
        if (ref.value.trim().endsWith("Z"))
            return new Prop(name, t.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().format(LOCAL) + "Z");
        Prop p = new Prop(name, t.withZoneSameInstant(zone(ref.param("TZID"))).toLocalDateTime().format(LOCAL));
        if (ref.param("TZID") != null) p.params.put("TZID", ref.param("TZID"));
        return p;
    }

    static String utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC).format(LOCAL) + "Z";
    }

    /** Durata ISO di iCalendar (-PT15M, P1D, P1W, ...). */
    static Duration duration(String v) {
        v = v.trim();
        boolean neg = v.startsWith("-");
        if (v.startsWith("-") || v.startsWith("+")) v = v.substring(1);
        Duration d;
        if (v.endsWith("W")) d = Duration.ofDays(7L * Long.parseLong(v.substring(1, v.length() - 1)));
        else d = Duration.parse(v.startsWith("P") && !v.contains("T") ? v + "T0S" : v);
        return neg ? d.negated() : d;
    }

    static String duration(Duration d) {
        boolean neg = d.isNegative();
        long m = Math.abs(d.toMinutes());
        String s = m % 1440 == 0 && m > 0 ? "P" + m / 1440 + "D" : m % 60 == 0 && m > 0 ? "PT" + m / 60 + "H" : "PT" + m + "M";
        return (neg ? "-" : "") + s;
    }

    /**
     * VTIMEZONE del fuso locale, ricavato dalle regole di java.time: i server e le altre app sanno così
     * quando scatta l'ora legale per gli eventi che si ripetono.
     */
    static Comp vtimezone(ZoneId z) {
        Comp tz = new Comp("VTIMEZONE");
        tz.props.add(new Prop("TZID", z.getId()));
        ZoneRules r = z.getRules();
        List<ZoneOffsetTransitionRule> rules = r.getTransitionRules();
        if (rules.size() == 2) {
            for (ZoneOffsetTransitionRule t : rules) {
                boolean dst = t.getOffsetAfter().getTotalSeconds() > t.getStandardOffset().getTotalSeconds();
                Comp c = new Comp(dst ? "DAYLIGHT" : "STANDARD");
                LocalDateTime local = t.createTransition(1970).getDateTimeBefore();
                c.props.add(new Prop("DTSTART", local.format(LOCAL)));
                c.props.add(new Prop("TZOFFSETFROM", offset(t.getOffsetBefore())));
                c.props.add(new Prop("TZOFFSETTO", offset(t.getOffsetAfter())));
                String byday = byday(t);
                if (byday != null)
                    c.props.add(new Prop("RRULE", "FREQ=YEARLY;BYMONTH=" + t.getMonth().getValue() + ";BYDAY=" + byday));
                tz.children.add(c);
            }
        } else {
            ZoneOffset o = r.getOffset(Instant.now());
            Comp c = new Comp("STANDARD");
            c.props.add(new Prop("DTSTART", "19700101T000000"));
            c.props.add(new Prop("TZOFFSETFROM", offset(o)));
            c.props.add(new Prop("TZOFFSETTO", offset(o)));
            tz.children.add(c);
        }
        return tz;
    }

    private static String byday(ZoneOffsetTransitionRule t) {
        DayOfWeek dow = t.getDayOfWeek();
        if (dow == null) return null;
        int ind = t.getDayOfMonthIndicator();
        String d = dow.name().substring(0, 2);
        int len = t.getMonth().minLength();
        if (ind < 0 || ind == len - 6) return "-1" + d;       // ultima domenica del mese
        if ((ind - 1) % 7 == 0) return ((ind - 1) / 7 + 1) + d; // prima, seconda, ... domenica
        return null;
    }

    private static String offset(ZoneOffset o) {
        int s = o.getTotalSeconds();
        int a = Math.abs(s);
        return String.format("%s%02d%02d", s < 0 ? "-" : "+", a / 3600, a / 60 % 60);
    }
}
