package dev.kroyabackup.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Kleines Hilfswerkzeug für Chat-Nachrichten: wandelt &-Farbcodes um und
 * holt Texte aus der messages.yml, inklusive Ersetzung von %prefix%.
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String get(FileConfiguration messages, String path) {
        String raw = messages.getString(path);
        if (raw == null) {
            return color("&c[Fehlende Nachricht: " + path + "]");
        }

        String prefix = messages.getString("prefix", "");
        raw = raw.replace("%prefix%", prefix);

        return color(raw);
    }
}
