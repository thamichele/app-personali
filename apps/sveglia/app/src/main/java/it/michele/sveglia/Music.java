package it.michele.sveglia;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Avvio delle app musicali usate come suoneria. */
final class Music {
    static final String WEBRADIO = "io.github.thamichele.webradiodroid";
    static final String QOBUZ = "com.qobuz.music";

    private Music() {}

    /** App avviabile con nome visibile. */
    static final class App {
        final String pkg;
        final String label;
        App(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    /**
     * Intent che avvia l'app e, se possibile, anche la riproduzione. WebRadioDroid ha un'azione dedicata
     * che riprende l'ultima stazione; le altre app vengono aperte e poi ricevono il tasto Play.
     */
    static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = null;
        if (pkg.equals(WEBRADIO) || pkg.equals(WEBRADIO + ".debug")) {
            i = new Intent("org.y20k.transistor.action.START")
                    .setClassName(pkg, "org.y20k.transistor.MainActivity")
                    .putExtra("START_LAST_PLAYED_STATION", true);
            if (pm.resolveActivity(i, 0) == null) i = null;
        }
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    static boolean installed(Context c, String pkg) {
        return !pkg.isEmpty() && launchIntent(c, pkg) != null;
    }

    static String label(Context c, String pkg) {
        if (pkg.isEmpty()) return "Suoneria di sistema";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg + " (non installata)";
        }
    }

    /** App con icona nel launcher: prima WebRadioDroid e Qobuz, poi le altre in ordine alfabetico. */
    static List<App> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> apps = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            apps.add(new App(pkg, r.loadLabel(pm).toString()));
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                int pa = priority(a.pkg), pb = priority(b.pkg);
                return pa != pb ? pa - pb : a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    private static int priority(String pkg) {
        if (pkg.startsWith(WEBRADIO)) return 0;
        if (pkg.equals(QOBUZ)) return 1;
        return 2;
    }

    /** Tasto multimediale inviato alla sessione attiva (l'app appena aperta). */
    static void press(Context c, int keyCode) {
        AudioManager am = c.getSystemService(AudioManager.class);
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0));
    }
}
