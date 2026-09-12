package dev.kroyabackup.commands;

import dev.kroyabackup.KroyaBackupPlugin;
import dev.kroyabackup.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /backup now - sichert sofort alle Welten (läuft im Hintergrund).
 * /backup list - zeigt vorhandene Backup-Zeitpunkte, neueste zuerst.
 * Auch von der Server-Konsole aus nutzbar, keine Spieler-Position nötig.
 */
public class BackupCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("now", "list");

    private final KroyaBackupPlugin plugin;

    public BackupCommand(KroyaBackupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kroyabackup.admin")) {
            sender.sendMessage(MessageUtil.get(plugin.getMessages(), "no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "now" -> {
                sender.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-started"));
                plugin.getBackupManager().performBackup(sender);
            }
            case "list" -> handleList(sender);
            default -> sender.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-usage"));
        }
        return true;
    }

    private void handleList(CommandSender sender) {
        List<String> backups = plugin.getBackupManager().listBackups();
        if (backups.isEmpty()) {
            sender.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-list-empty"));
            return;
        }
        sender.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-list-header"));
        for (String name : backups) {
            sender.sendMessage(MessageUtil.get(plugin.getMessages(), "backup-list-entry").replace("%name%", name));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("kroyabackup.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(args[0].toLowerCase())) {
                options.add(sub);
            }
        }
        return options;
    }
}
