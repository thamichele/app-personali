package it.michele.calendario;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Avvio con il computer, nascosto nell'area di notifica (--tray):
 * Linux con un file .desktop in ~/.config/autostart, Windows con la chiave Run dell'utente.
 */
final class Autostart {
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private Autostart() {}

    /** Comando che avvia questa installazione: l'eseguibile del pacchetto, altrimenti java -jar. */
    static List<String> command() {
        List<String> cmd = new ArrayList<>();
        String app = System.getProperty("jpackage.app-path");
        if (app != null) cmd.add(app);
        else {
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", Main.WINDOWS ? "javaw.exe" : "java").toString());
            try {
                cmd.add("-jar");
                cmd.add(new File(Autostart.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath());
            } catch (Exception e) {
                return null;
            }
        }
        cmd.add("--tray");
        return cmd;
    }

    private static Path desktopFile() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isEmpty() ? Paths.get(xdg) : Paths.get(System.getProperty("user.home"), ".config");
        return base.resolve("autostart").resolve("calendario.desktop");
    }

    static boolean enabled() {
        if (Main.WINDOWS) return run("reg", "query", RUN_KEY, "/v", "Calendario") == 0;
        return Files.exists(desktopFile());
    }

    static void set(boolean on) {
        List<String> cmd = command();
        if (on && cmd == null) return;
        if (Main.WINDOWS) {
            if (on) {
                StringBuilder line = new StringBuilder();
                for (String c : cmd) line.append(line.length() > 0 ? " " : "").append('"').append(c).append('"');
                run("reg", "add", RUN_KEY, "/v", "Calendario", "/t", "REG_SZ", "/d", line.toString(), "/f");
            } else run("reg", "delete", RUN_KEY, "/v", "Calendario", "/f");
            return;
        }
        try {
            Path f = desktopFile();
            if (!on) { Files.deleteIfExists(f); return; }
            Files.createDirectories(f.getParent());
            StringBuilder exec = new StringBuilder();
            for (String c : cmd) exec.append(exec.length() > 0 ? " " : "").append('"')
                    .append(c.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$")).append('"');
            Files.write(f, ("[Desktop Entry]\nType=Application\nName=Calendario\nExec=" + exec
                    + "\nX-GNOME-Autostart-enabled=true\nNoDisplay=true\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Avvio automatico non impostato: " + e);
        }
    }

    private static int run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, TimeUnit.SECONDS) ? p.exitValue() : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
