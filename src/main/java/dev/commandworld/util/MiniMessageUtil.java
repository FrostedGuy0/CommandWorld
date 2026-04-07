package dev.commandworld.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Convenience helpers for MiniMessage formatting.
 */
public final class MiniMessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MiniMessageUtil() {}

    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static void send(Player player, String miniMessage) {
        player.sendMessage(parse(miniMessage));
    }

    /**
     * Applies common chat placeholders.
     */
    public static String applyPlaceholders(String format, String player, String message,
                                           String world, String prefix, String suffix) {
        return format
                .replace("%player%",  player)
                .replace("%message%", message)
                .replace("%world%",   world)
                .replace("%prefix%",  prefix  == null ? "" : prefix)
                .replace("%suffix%",  suffix  == null ? "" : suffix);
    }

    /**
     * Strips MiniMessage tags from a string (for plain-text comparison etc.).
     */
    public static String stripTags(String miniMessage) {
        return MM.stripTags(miniMessage);
    }
}
