package it.michele.calendario;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ripetizioni RRULE (RFC 5545): FREQ giornaliera, settimanale, mensile e annuale con INTERVAL, COUNT, UNTIL,
 * BYDAY (anche "2MO", "-1FR"), BYMONTHDAY, BYMONTH e BYSETPOS. Bastano per gli eventi creati dalle app comuni.
 */
final class Rrule {
    final String freq;
    final int interval;
    final int count;
    final String until;
    final List<int[]> byDay = new ArrayList<>(); // {ordinale (0 = tutti), giorno 1-7}
    final List<Integer> byMonthDay = new ArrayList<>(), byMonth = new ArrayList<>(), bySetPos = new ArrayList<>();

    Rrule(String rule) {
        Map<String, String> m = new HashMap<>();
        for (String part : rule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) m.put(part.substring(0, eq).trim().toUpperCase(), part.substring(eq + 1).trim().toUpperCase());
        }
        freq = m.getOrDefault("FREQ", "");
        interval = Math.max(1, parseInt(m.get("INTERVAL"), 1));
        count = parseInt(m.get("COUNT"), 0);
        until = m.get("UNTIL");
        if (m.containsKey("BYDAY")) for (String d : m.get("BYDAY").split(",")) {
            d = d.trim();
            if (d.length() < 2) continue;
            int day = "MOTUWETHFRSASU".indexOf(d.substring(d.length() - 2)) / 2 + 1;
            if (day <= 0) continue;
            byDay.add(new int[]{d.length() > 2 ? parseInt(d.substring(0, d.length() - 2).replace("+", ""), 0) : 0, day});
        }
        ints(m.get("BYMONTHDAY"), byMonthDay);
        ints(m.get("BYMONTH"), byMonth);
        ints(m.get("BYSETPOS"), bySetPos);
    }

    private static int parseInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static void ints(String s, List<Integer> out) {
        if (s != null) for (String p : s.split(",")) { int v = parseInt(p, 0); if (v != 0) out.add(v); }
    }

    /** Regola semplice ("FREQ=WEEKLY" ecc.) che il dialogo sa mostrare e modificare. */
    static boolean simple(String rule) {
        Rrule r = new Rrule(rule);
        return r.interval == 1 && r.count == 0 && r.until == null && r.byDay.isEmpty() && r.byMonthDay.isEmpty()
                && r.byMonth.isEmpty() && r.bySetPos.isEmpty() && !r.freq.isEmpty();
    }

    /** Descrizione breve in italiano. */
    String describe() {
        String[] unit = {"giorno", "settimana", "mese", "anno"};
        String[] units = {"giorni", "settimane", "mesi", "anni"};
        int i = List.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY").indexOf(freq);
        if (i < 0) return "Ripetizione personalizzata";
        String s = interval == 1 ? "Ogni " + unit[i] : "Ogni " + interval + " " + units[i];
        if (!byDay.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (int[] d : byDay) b.append(b.length() > 0 ? ", " : "").append(d[0] != 0 ? d[0] + "° " : "")
                    .append(Ui.DAYS_SHORT[d[1] - 1].toLowerCase());
            s += " (" + b + ")";
        }
        if (count > 0) s += ", " + count + " volte";
        else if (until != null) s += ", fino al " + until.substring(6, 8) + "/" + until.substring(4, 6) + "/" + until.substring(0, 4);
        return s;
    }

    /** Inizi delle ripetizioni da start fino a "to" (compreso), escluso nulla: le eccezioni le toglie il chiamante. */
    List<ZonedDateTime> expand(ZonedDateTime start, ZonedDateTime to) {
        List<ZonedDateTime> out = new ArrayList<>();
        out.add(start);
        int unit = List.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY").indexOf(freq);
        if (unit < 0) return out;
        ZoneId zone = start.getZone();
        LocalTime time = start.toLocalTime();
        LocalDate first = start.toLocalDate();
        ZonedDateTime limit = to;
        if (until != null) {
            ZonedDateTime u = until.length() == 8 ? Ical.time(until, null).plusDays(1).minusNanos(1) : Ical.time(until, zone.getId());
            if (u.isBefore(limit)) limit = u;
        }
        int n = 1;
        for (int k = 0; k < 50000; k++) {
            LocalDate base = unit == 0 ? first.plusDays((long) k * interval)
                    : unit == 1 ? first.with(DayOfWeek.MONDAY).plusWeeks((long) k * interval)
                    : unit == 2 ? first.withDayOfMonth(1).plusMonths((long) k * interval)
                    : first.withDayOfYear(1).plusYears((long) k * interval);
            if (base.atStartOfDay(zone).isAfter(limit)) break;
            List<LocalDate> days = candidates(unit, base, first);
            Collections.sort(days);
            if (!bySetPos.isEmpty()) {
                List<LocalDate> sel = new ArrayList<>();
                for (int p : bySetPos) {
                    int idx = p > 0 ? p - 1 : days.size() + p;
                    if (idx >= 0 && idx < days.size()) sel.add(days.get(idx));
                }
                Collections.sort(sel);
                days = sel;
            }
            for (LocalDate d : days) {
                if (!d.isAfter(first)) continue;
                ZonedDateTime t = ZonedDateTime.of(d, time, zone);
                if (t.isAfter(limit)) return out;
                if (count > 0 && n >= count) return out;
                out.add(t);
                n++;
            }
        }
        return out;
    }

    private List<LocalDate> candidates(int unit, LocalDate base, LocalDate first) {
        List<LocalDate> days = new ArrayList<>();
        switch (unit) {
            case 0:
                if (matches(base, true)) days.add(base);
                break;
            case 1:
                if (byDay.isEmpty()) addIf(days, base.plusDays(first.getDayOfWeek().getValue() - 1));
                else for (int[] d : byDay) addIf(days, base.plusDays(d[1] - 1));
                break;
            case 2:
                month(days, YearMonth.from(base), first);
                break;
            default:
                if (!byMonth.isEmpty()) {
                    for (int m : byMonth) month(days, YearMonth.of(base.getYear(), m), first);
                } else if (!byMonthDay.isEmpty() || byDay.isEmpty()) {
                    month(days, YearMonth.of(base.getYear(), first.getMonthValue()), first);
                } else {
                    // BYDAY sull'intero anno (es. "20MO", "-1SU")
                    for (int[] d : byDay) {
                        List<LocalDate> all = new ArrayList<>();
                        for (LocalDate x = base.with(TemporalAdjusters.firstInMonth(DayOfWeek.of(d[1])));
                             x.getYear() == base.getYear(); x = x.plusWeeks(1)) all.add(x);
                        pick(days, all, d[0]);
                    }
                }
        }
        return days;
    }

    private void month(List<LocalDate> days, YearMonth ym, LocalDate first) {
        if (!byMonth.isEmpty() && !byMonth.contains(ym.getMonthValue())) return;
        if (!byMonthDay.isEmpty()) {
            for (int md : byMonthDay) {
                LocalDate d = safeDay(ym, md > 0 ? md : ym.lengthOfMonth() + md + 1);
                if (d != null && matchesDay(d, ym)) days.add(d);
            }
        } else if (!byDay.isEmpty()) {
            for (int[] d : byDay) {
                List<LocalDate> all = new ArrayList<>();
                for (LocalDate x = ym.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.of(d[1])));
                     x.getMonthValue() == ym.getMonthValue(); x = x.plusWeeks(1)) all.add(x);
                pick(days, all, d[0]);
            }
        } else {
            LocalDate d = safeDay(ym, first.getDayOfMonth());
            if (d != null) days.add(d);
        }
    }

    private static void pick(List<LocalDate> days, List<LocalDate> all, int ord) {
        if (ord == 0) days.addAll(all);
        else {
            int idx = ord > 0 ? ord - 1 : all.size() + ord;
            if (idx >= 0 && idx < all.size()) days.add(all.get(idx));
        }
    }

    private static LocalDate safeDay(YearMonth ym, int day) {
        return day >= 1 && day <= ym.lengthOfMonth() ? ym.atDay(day) : null;
    }

    private boolean matchesDay(LocalDate d, YearMonth ym) {
        if (byDay.isEmpty()) return true;
        for (int[] b : byDay) if (b[0] == 0 && d.getDayOfWeek().getValue() == b[1]) return true;
        return false;
    }

    private void addIf(List<LocalDate> days, LocalDate d) {
        if (byMonth.isEmpty() || byMonth.contains(d.getMonthValue())) days.add(d);
    }

    private boolean matches(LocalDate d, boolean daily) {
        if (!byMonth.isEmpty() && !byMonth.contains(d.getMonthValue())) return false;
        if (!byMonthDay.isEmpty()) {
            boolean ok = false;
            for (int md : byMonthDay) {
                int day = md > 0 ? md : d.lengthOfMonth() + md + 1;
                if (d.getDayOfMonth() == day) ok = true;
            }
            if (!ok) return false;
        }
        if (!byDay.isEmpty() && daily) {
            boolean ok = false;
            for (int[] b : byDay) if (d.getDayOfWeek().getValue() == b[1]) ok = true;
            return ok;
        }
        return true;
    }
}
