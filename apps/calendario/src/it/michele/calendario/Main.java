package it.michele.calendario;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Calendario: calendari CalDAV e contatti CardDAV sincronizzati, con promemoria. Per Windows e Linux. */
public final class Main {
    static final String VERSION = version();
    static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    static MainWindow window;
    static Sync sync;

    private Main() {}

    private static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--icone")) { // usato da build.sh
            Ui.exportIcons(Paths.get(args[1]));
            return;
        }
        if (!singleInstance()) return;
        Store store = new Store();
        double scale = store.scale() > 0 ? store.scale() / 100.0 : WINDOWS ? 0 : linuxScale();
        if (scale > 0) System.setProperty("sun.java2d.uiScale", String.valueOf(scale));
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        boolean hidden = Arrays.asList(args).contains("--tray");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(new MetalLookAndFeel()); } catch (Exception ignored) { }
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            Ui.theme(store.theme());
            Ui.install();
            sync = new Sync(store);
            window = new MainWindow(store, sync);
            if (!hidden || !window.hasTray()) window.show();
        });
    }

    /**
     * Scala automatica su Linux: Java segue GDK_SCALE, ma GNOME/KDE con scala frazionaria o testo ingrandito
     * la indicano solo in Xft.dpi (96 = 100%). 0 = lascia decidere a Java.
     */
    private static double linuxScale() {
        if (System.getenv("GDK_SCALE") != null) return 0;
        try {
            Process p = new ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Xft\\.dpi:\\s*(\\d+)").matcher(out);
            if (m.find()) {
                // su Linux Java accetta solo scale intere: 150% diventa 200%, 125% resta 100%
                long s = Math.round(Integer.parseInt(m.group(1)) / 96.0);
                return s >= 2 && s <= 4 ? s : 0;
            }
        } catch (Exception ignored) { }
        return 0;
    }

    /**
     * Una sola copia aperta: la seconda chiede alla prima di mostrarsi (via una porta su 127.0.0.1 che accetta
     * solo il comando "mostra") ed esce. Due copie scriverebbero insieme la stessa copia locale.
     */
    private static boolean singleInstance() {
        Path lockFile = Store.DIR.resolve("calendario.lock"), portFile = Store.DIR.resolve("porta");
        try {
            FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = ch.tryLock();
            if (lock == null) {
                try (Socket s = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(Files.readString(portFile).trim()));
                     OutputStream out = s.getOutputStream()) {
                    out.write("mostra\n".getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
                return false;
            }
            ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            Files.writeString(portFile, String.valueOf(server.getLocalPort()));
            Thread t = new Thread(() -> {
                while (true) {
                    try (Socket s = server.accept()) {
                        s.setSoTimeout(2000);
                        String line = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                        if ("mostra".equals(line)) SwingUtilities.invokeLater(() -> { if (window != null) window.show(); });
                    } catch (Exception ignored) { }
                }
            }, "istanza");
            t.setDaemon(true);
            t.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { lock.release(); ch.close(); } catch (Exception ignored) { }
            }));
        } catch (Exception e) {
            System.err.println("Controllo istanza unica non riuscito: " + e);
        }
        return true;
    }
}
