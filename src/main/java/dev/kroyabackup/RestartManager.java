package dev.kroyabackup;

import dev.kroyabackup.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Chat-Ankuendigung "restart.warning-minutes" vor dem taeglichen Neustart
 * (config: restart.time). Loest den Neustart selbst NICHT aus: Papers
 * eingebannter "/restart"-Befehl braucht ein "start.sh"-Wrapper-Skript, das
 * dieser Host nicht verwendet - dort wuerde der Befehl den Server nur
 * abschalten, ohne ihn wieder hochzufahren (getestet, siehe Server-Log
 * "Startup script './start.sh' does not exist! Stopping server."). Der
 * eigentliche Neustart muss daher ueber die native "Geplante Neustart-Zeiten"-
 * Funktion im Host-Panel eingestellt werden (gleiche Uhrzeit wie restart.time
 * hier), die zuverlaessig mit dem Prozess-Supervisor des Hosts zusammenspielt.
 *
 * Statt eines Sekunden-Takts, der die Uhrzeit staendig abfragt, wird die
 * Verzoegerung bis zum naechsten Warn-Zeitpunkt einmalig berechnet und per
 * runTaskLater eingeplant - beim naechsten Plugin-Start (also nach jedem
 * Neustart) wird automatisch der Zeitpunkt fuer den Folgetag geplant.
 */
public class RestartManager {

    private final KroyaBackupPlugin plugin;
    private BukkitTask warningTask;

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
        if (!warningAt.isAfter(now)) {
            warningAt = warningAt.plusDays(1);
        }

        long warningDelayTicks = Duration.between(now, warningAt).toMillis() / 50L;
        warningTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastWarning(warningMinutes);
            scheduleNext();
        }, warningDelayTicks);
    }

    private void broadcastWarning(int minutes) {
        Bukkit.broadcastMessage(MessageUtil.get(plugin.getMessages(), "restart-warning")
                .replace("%minutes%", String.valueOf(minutes)));
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
