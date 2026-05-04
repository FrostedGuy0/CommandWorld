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

public final class ChatHelper {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()          
                    .useUnusualXRepeatedCharacterHexFormat() 
                    .build();

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChatHelper() {}

    public static String getEffectiveContext(CommandWorldPlugin plugin, Player player) {
        String world = player.getWorld().getName();
        String group = plugin.getPluginConfig().getWorldGroup(world);
        return group != null ? group : world;
    }

    public static String getPrefix(Player player) {
        String raw = getRawPrefix(player);
        return raw.isEmpty() ? "" : legacyToMiniMessage(raw);
    }

    public static String getSuffix(Player player) {
        String raw = getRawSuffix(player);
        return raw.isEmpty() ? "" : legacyToMiniMessage(raw);
    }

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

    private static String legacyToMiniMessage(String legacy) {
        try {
            Component component = LEGACY.deserialize(legacy);
            return MM.serialize(component);
        } catch (Exception e) {
            
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
