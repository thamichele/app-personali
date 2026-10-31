package it.michele.sveglia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riceve l'allarme programmato (receiver non esportato: solo AlarmManager tramite il nostro PendingIntent). */
public class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "it.michele.sveglia.FIRE";
    static final String ACTION_TEST = "it.michele.sveglia.TEST";
    static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context context, Intent intent) {
        Store store = new Store(context);
        Alarm a = store.get(intent.getIntExtra(EXTRA_ID, 0));
        if (a != null && ACTION_TEST.equals(intent.getAction())) {
            RingService.ring(context, a.id);
            return;
        }
        if (!ACTION_FIRE.equals(intent.getAction())) return;
        if (a == null || !a.enabled) return;
        a.snoozeAt = 0;
        if (!a.repeats()) a.enabled = false;
        store.save(a);
        Scheduler.scheduleAll(context);
        RingService.ring(context, a.id);
    }
}
