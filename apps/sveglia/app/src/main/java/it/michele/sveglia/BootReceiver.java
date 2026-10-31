package it.michele.sveglia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riprogramma le sveglie dopo riavvio, aggiornamento dell'app o cambio di ora/fuso orario. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // Il receiver è esportato (serve per il boot): accetta solo le azioni di sistema previste.
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        Scheduler.scheduleAll(context);
    }
}
