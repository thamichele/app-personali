package it.michele.sveglia;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/** Una sveglia. days: bit 0 = lunedì ... bit 6 = domenica; 0 = una sola volta. */
final class Alarm {
    static final String[] DAY_LETTERS = {"L", "M", "M", "G", "V", "S", "D"};

    int id;
    int hour = 7;
    int minute;
    int days;
    boolean enabled = true;
    String label = "";
    /** Pacchetto dell'app musicale da avviare; vuoto = suoneria di sistema. */
    String app = "";
    /** Cosa riprodurre nell'app: elemento scelto dal catalogo (id e titolo) o testo da cercare; vuoti = ultima riproduzione. */
    String media = "";
    String mediaTitle = "";
    String query = "";
    /** Orario del posticipo in corso (millisecondi), 0 se nessuno. */
    long snoozeAt;

    boolean repeats() { return days != 0; }

    boolean hasDay(int i) { return (days & (1 << i)) != 0; }

    /** Prossimo orario in cui suonare dopo now (posticipo compreso). */
    long next(long now) {
        if (snoozeAt > now) return snoozeAt;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
        // al massimo 7 giorni avanti per trovare un giorno attivo
        for (int i = 0; repeats() && i < 7 && !hasDay(dayIndex(c)); i++) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    /** Indice 0..6 (lunedì..domenica) del giorno del calendario. */
    static int dayIndex(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** "Ultima riproduzione", il titolo scelto o la ricerca. */
    String what() {
        if (!media.isEmpty()) return mediaTitle.isEmpty() ? media : mediaTitle;
        if (!query.isEmpty()) return "Cerca \u201c" + query + "\u201d";
        return "Ultima riproduzione";
    }

    String time() { return String.format(java.util.Locale.ITALY, "%02d:%02d", hour, minute); }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("h", hour).put("m", minute).put("days", days)
                .put("on", enabled).put("label", label).put("app", app)
                .put("media", media).put("mediaTitle", mediaTitle).put("query", query).put("snooze", snoozeAt);
    }

    static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optInt("id");
        a.hour = Math.max(0, Math.min(23, o.optInt("h", 7)));
        a.minute = Math.max(0, Math.min(59, o.optInt("m")));
        a.days = o.optInt("days") & 0x7f;
        a.enabled = o.optBoolean("on", true);
        a.label = o.optString("label");
        a.app = o.optString("app");
        a.media = o.optString("media");
        a.mediaTitle = o.optString("mediaTitle");
        a.query = o.optString("query");
        a.snoozeAt = o.optLong("snooze");
        return a;
    }
}
