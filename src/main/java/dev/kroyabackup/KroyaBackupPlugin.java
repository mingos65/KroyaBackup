package dev.kroyabackup;

import dev.kroyabackup.commands.BackupCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class KroyaBackupPlugin extends JavaPlugin {

    private BackupManager backupManager;
    private RestartManager restartManager;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadMessages();

        this.backupManager = new BackupManager(this);
        this.restartManager = new RestartManager(this);

        BackupCommand backupCommand = new BackupCommand(this);
        getCommand("backup").setExecutor(backupCommand);
        getCommand("backup").setTabCompleter(backupCommand);

        backupManager.start();
        restartManager.start();

        getLogger().info("KroyaBackup wurde aktiviert.");
    }

    @Override
    public void onDisable() {
        if (backupManager != null) {
            backupManager.stop();
        }
        if (restartManager != null) {
            restartManager.stop();
        }
        getLogger().info("KroyaBackup wurde deaktiviert.");
    }

    private void loadMessages() {
        saveResource("messages.yml", true);
        File file = new File(getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public FileConfiguration getMessages() {
        return messages;
    }
}
