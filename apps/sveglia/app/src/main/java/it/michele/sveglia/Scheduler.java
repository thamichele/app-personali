package it.michele.sveglia;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Programma le sveglie con AlarmManager.setAlarmClock: è il tipo di allarme più affidabile
 * (esce anche dal risparmio energetico) e mostra l'icona della sveglia nella barra di stato.
 */
final class Scheduler {
    private Scheduler() {}

    static void scheduleAll(Context c) {
        long now = System.currentTimeMillis();
        for (Alarm a : new Store(c).alarms()) {
            if (a.enabled) schedule(c, a, now); else cancel(c, a.id);
        }
    }

    static void schedule(Context c, Alarm a, long now) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(a.next(now), show), fire(c, a.id));
    }

    static void cancel(Context c, int id) {
        c.getSystemService(AlarmManager.class).cancel(fire(c, id));
    }

    /** Prossima sveglia attiva (millisecondi), 0 se nessuna. */
    static long nextAny(Context c) {
        long now = System.currentTimeMillis(), best = 0;
        for (Alarm a : new Store(c).alarms()) {
            if (!a.enabled) continue;
            long t = a.next(now);
            if (best == 0 || t < best) best = t;
        }
        return best;
    }

    /** Suono di prova: non tocca lo stato della sveglia. */
    static void test(Context c, int id, long at) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_TEST)
                .putExtra(AlarmReceiver.EXTRA_ID, id);
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(at, null),
                PendingIntent.getBroadcast(c, -id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static PendingIntent fire(Context c, int id) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(AlarmReceiver.EXTRA_ID, id);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
