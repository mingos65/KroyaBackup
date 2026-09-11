package dev.kroyabackup;

import dev.kroyabackup.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Taeglicher automatischer Neustart zu fester Uhrzeit (config: restart.time),
 * mit einer Chat-Ankuendigung "restart.warning-minutes" vorher. Nutzt Papers
 * eingebauten "/restart"-Befehl statt selbst herunterzufahren, damit der
 * Host-Wrapper den Prozess wie gewohnt sauber neu startet.
 *
 * Statt eines Sekunden-Takts, der die Uhrzeit staendig abfragt, wird die
 * Verzoegerung bis zum naechsten Warn-/Neustart-Zeitpunkt einmalig berechnet
 * und per runTaskLater eingeplant - beim naechsten Plugin-Start (also nach
 * jedem Neustart) wird automatisch der Zeitpunkt fuer den Folgetag geplant.
 */
public class RestartManager {

    private final KroyaBackupPlugin plugin;
    private BukkitTask warningTask;
    private BukkitTask restartTask;

    public RestartManager(KroyaBackupPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("restart.enabled", true)) {
            return;
        }
        scheduleNext();
    }

    public void stop() {
        if (warningTask != null) {
            warningTask.cancel();
        }
        if (restartTask != null) {
            restartTask.cancel();
        }
    }

    private void scheduleNext() {
        LocalTime restartTime = parseTime(plugin.getConfig().getString("restart.time", "04:00"));
        int warningMinutes = plugin.getConfig().getInt("restart.warning-minutes", 5);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRestart = now.toLocalDate().atTime(restartTime);
        if (!nextRestart.isAfter(now)) {
            nextRestart = nextRestart.plusDays(1);
        }
        LocalDateTime warningAt = nextRestart.minusMinutes(warningMinutes);

        long restartDelayTicks = Duration.between(now, nextRestart).toMillis() / 50L;
        restartTask = Bukkit.getScheduler().runTaskLater(plugin, this::executeRestart, restartDelayTicks);

        if (warningAt.isAfter(now)) {
            long warningDelayTicks = Duration.between(now, warningAt).toMillis() / 50L;
            warningTask = Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastWarning(warningMinutes), warningDelayTicks);
        }
    }

    private void broadcastWarning(int minutes) {
        Bukkit.broadcastMessage(MessageUtil.get(plugin.getMessages(), "restart-warning")
                .replace("%minutes%", String.valueOf(minutes)));
    }

    private void executeRestart() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
    }

    private LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            plugin.getLogger().warning("Ungueltige restart.time in config.yml ('" + raw + "'), nutze 04:00 stattdessen.");
            return LocalTime.of(4, 0);
        }
    }
}
