package dev.commandworld.util;

import dev.commandworld.CommandWorldPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

/**
 * Resolves per-player chat context: world, world-group, LP prefix/suffix.
 *
 * LuckPerms prefixes/suffixes use legacy & colour codes (including hex &#RRGGBB).
 * We convert them to MiniMessage format so they render correctly inside
 * MiniMessage chat format strings.
 */
public final class ChatHelper {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()          // handles &#RRGGBB hex codes
                    .useUnusualXRepeatedCharacterHexFormat() // handles &x&R&R&G&G&B&B too
                    .build();

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChatHelper() {}

    /**
     * Effective context key for a player: returns the world-group name if available,
     * otherwise the raw world name.
     */
    public static String getEffectiveContext(CommandWorldPlugin plugin, Player player) {
        String world = player.getWorld().getName();
        String group = plugin.getPluginConfig().getWorldGroup(world);
        return group != null ? group : world;
    }

    /**
     * Returns the player's LuckPerms prefix as a MiniMessage string,
     * converting legacy & codes so it renders correctly in MiniMessage formats.
     * Returns empty string if LuckPerms is unavailable or prefix is null.
     */
    public static String getPrefix(Player player) {
        String raw = getRawPrefix(player);
        return raw.isEmpty() ? "" : legacyToMiniMessage(raw);
    }

    /**
     * Returns the player's LuckPerms suffix as a MiniMessage string.
     */
    public static String getSuffix(Player player) {
        String raw = getRawSuffix(player);
        return raw.isEmpty() ? "" : legacyToMiniMessage(raw);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String getRawPrefix(Player player) {
        if (!isLpLoaded()) return "";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            CachedMetaData meta = user.getCachedData().getMetaData();
            String prefix = meta.getPrefix();
            return prefix == null ? "" : prefix;
        } catch (Exception e) {
            return "";
        }
    }

    private static String getRawSuffix(Player player) {
        if (!isLpLoaded()) return "";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            CachedMetaData meta = user.getCachedData().getMetaData();
            String suffix = meta.getSuffix();
            return suffix == null ? "" : suffix;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Converts a legacy-formatted string (& codes, &#RRGGBB hex) into a
     * MiniMessage string so it can be safely embedded in MiniMessage formats.
     */
    private static String legacyToMiniMessage(String legacy) {
        try {
            Component component = LEGACY.deserialize(legacy);
            return MM.serialize(component);
        } catch (Exception e) {
            // Fallback: strip all & codes rather than showing raw tags
            return legacy.replaceAll("(?i)&#[0-9a-f]{6}|&[0-9a-fk-orx]", "");
        }
    }

    private static boolean isLpLoaded() {
        try {
            LuckPermsProvider.get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
