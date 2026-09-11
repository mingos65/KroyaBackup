package dev.kroyabackup;

import dev.kroyabackup.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Sichert regelmaessig ALLE Bukkit-Welten als Zip auf die Festplatte -
 * unabhaengig vom Host-Panel (das nur manuelle Snapshots kennt) und
 * unabhaengig von einzelnen Plugins wie PlotSquared (deren eigenes
 * Backup-System einen geloeschten Plot nur sichert, wenn "delete-on-unclaim"
 * nicht sofort wieder alles entfernt).
 *
 * world.save() laeuft synchron auf dem Hauptthread (noetig, um wirklich
 * alle offenen Chunks vorher auf die Platte zu schreiben), das eigentliche
 * Zippen danach asynchron, damit ein grosser Weltordner den Server nicht
 * fuer Sekunden einfrieren laesst.
 */
public class BackupManager {

    private final KroyaBackupPlugin plugin;
    private BukkitTask task;

    public BackupManager(KroyaBackupPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("backup.enabled", true)) {
            return;
        }
        long intervalTicks = plugin.getConfig().getLong("backup.interval-hours", 6) * 60 * 60 * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> performBackup(null), intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /** Sichert alle Welten. feedbackTo darf null sein (automatischer, geplanter Lauf ohne Chat-Rueckmeldung). */
    public void performBackup(CommandSender feedbackTo) {
        for (World world : Bukkit.getWorlds()) {
            world.save();
        }

        File backupRoot = new File(plugin.getDataFolder(), "backups");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File targetDir = new File(backupRoot, timestamp);
        List<World> worlds = List.copyOf(Bukkit.getWorlds());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            targetDir.mkdirs();
            int successCount = 0;
            for (World world : worlds) {
                try {
                    zipDirectory(world.getWorldFolder(), new File(targetDir, world.getName() + ".zip"));
                    successCount++;
                } catch (IOException ex) {
                    plugin.getLogger().warning("Backup fehlgeschlagen fuer Welt " + world.getName() + ": " + ex.getMessage());
                }
            }
            pruneOldBackups(backupRoot);

            int finalCount = successCount;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Backup abgeschlossen: " + finalCount + " Welt(en) gesichert (" + timestamp + ").");
                if (feedbackTo != null) {
                    feedbackTo.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-done")
                            .replace("%amount%", String.valueOf(finalCount)));
                }
            });
        });
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        Path sourcePath = sourceDir.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
             Stream<Path> walk = Files.walk(sourcePath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("session.lock"))
                    .forEach(p -> {
                        String entryName = sourcePath.relativize(p).toString().replace('\\', '/');
                        try {
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException ex) {
                            plugin.getLogger().warning("Konnte Datei nicht sichern: " + p + " (" + ex.getMessage() + ")");
                        }
                    });
        }
    }

    /** Loescht die aeltesten Backup-Ordner, bis nur noch "backup.keep-count" uebrig sind. */
    private void pruneOldBackups(File backupRoot) {
        File[] dirs = backupRoot.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        int keep = plugin.getConfig().getInt("backup.keep-count", 10);
        int toDelete = dirs.length - keep;
        for (int i = 0; i < toDelete; i++) {
            deleteRecursively(dirs[i]);
        }
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** Namen der vorhandenen Backup-Zeitpunkte, neueste zuerst. */
    public List<String> listBackups() {
        File backupRoot = new File(plugin.getDataFolder(), "backups");
        File[] dirs = backupRoot.listFiles(File::isDirectory);
        if (dirs == null) {
            return List.of();
        }
        Arrays.sort(dirs, Comparator.comparing(File::getName).reversed());
        return Arrays.stream(dirs).map(File::getName).toList();
    }
}
