package dev.commandworld.manager;

import dev.commandworld.CommandWorldPlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Thin bridge to LuckPerms. Gracefully no-ops when LP is absent.
 */
public class LuckPermsManager {

    private final CommandWorldPlugin plugin;
    private LuckPerms luckPerms;

    public LuckPermsManager(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("LuckPerms not found – permission-group rules will be skipped.");
            return;
        }
        try {
            luckPerms = LuckPermsProvider.get();
            plugin.getLogger().info("LuckPerms hooked successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook into LuckPerms.", e);
        }
    }

    public boolean isAvailable() {
        return luckPerms != null;
    }

    /**
     * Returns the primary group of a player, or {@code null} if LP is unavailable.
     */
    public String getPrimaryGroup(Player player) {
        if (!isAvailable()) return null;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null ? null : user.getPrimaryGroup();
    }
}
