package it.michele.sveglia;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Sveglie e impostazioni. Salvate nello spazio "device protected", leggibile anche prima del primo sblocco
 * dopo un riavvio: così le sveglie suonano comunque.
 */
final class Store {
    private static final String KEY_ALARMS = "alarms";
    private static final String KEY_SNOOZE = "snooze_min";
    private static final String KEY_VOLUME = "music_volume";
    private static final String KEY_LOG = "log";
    private static final int MAX_LOG_LINES = 40;

    private final SharedPreferences sp;

    Store(Context c) {
        sp = c.getApplicationContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("sveglia", Context.MODE_PRIVATE);
    }

    /** Sveglie ordinate per orario. */
    List<Alarm> alarms() {
        List<Alarm> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_ALARMS, "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(Alarm.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        Collections.sort(list, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a, Alarm b) { return (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute); }
        });
        return list;
    }

    Alarm get(int id) {
        for (Alarm a : alarms()) if (a.id == id) return a;
        return null;
    }

    /** Inserisce o aggiorna; una sveglia nuova (id 0) riceve un id libero. */
    synchronized void save(Alarm alarm) {
        List<Alarm> list = alarms();
        if (alarm.id == 0) {
            int max = 0;
            for (Alarm a : list) max = Math.max(max, a.id);
            alarm.id = max + 1;
        }
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == alarm.id) list.remove(i--);
        list.add(alarm);
        write(list);
    }

    synchronized void delete(int id) {
        List<Alarm> list = alarms();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) list.remove(i--);
        write(list);
    }

    private void write(List<Alarm> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : list) arr.put(a.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        // commit: la sveglia deve essere su disco prima di programmare l'allarme
        sp.edit().putString(KEY_ALARMS, arr.toString()).commit();
    }

    int snoozeMinutes() { return sp.getInt(KEY_SNOOZE, 10); }
    void setSnoozeMinutes(int v) { sp.edit().putInt(KEY_SNOOZE, Math.max(1, Math.min(v, 60))).apply(); }

    /** Volume musica in percentuale impostato quando suona la sveglia; 0 = lascia quello attuale. */
    int musicVolume() { return sp.getInt(KEY_VOLUME, 50); }
    void setMusicVolume(int v) { sp.edit().putInt(KEY_VOLUME, Math.max(0, Math.min(v, 100))).apply(); }

    /** Registro di cosa è successo quando la sveglia ha suonato, per capire perché una ricerca non parte. */
    String log() { return sp.getString(KEY_LOG, ""); }

    synchronized void log(String msg) {
        String line = new java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.ITALY).format(new java.util.Date()) + "  " + msg;
        String[] old = log().isEmpty() ? new String[0] : log().split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < old.length && i < MAX_LOG_LINES - 1; i++) sb.append('\n').append(old[i]);
        sp.edit().putString(KEY_LOG, sb.toString()).apply();
    }

    void clearLog() { sp.edit().putString(KEY_LOG, "").apply(); }
}
