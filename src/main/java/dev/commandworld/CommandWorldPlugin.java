package dev.commandworld;

import dev.commandworld.command.ChatSpyCommand;
import dev.commandworld.command.CwCommand;
import dev.commandworld.command.StaffChatCommand;
import dev.commandworld.config.PluginConfig;
import dev.commandworld.listener.ChatListener;
import dev.commandworld.listener.CommandListener;
import dev.commandworld.manager.LuckPermsManager;
import dev.commandworld.manager.PlayerStateManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CommandWorldPlugin extends JavaPlugin implements Listener {

    private static CommandWorldPlugin instance;

    private PluginConfig pluginConfig;
    private LuckPermsManager luckPermsManager;
    private PlayerStateManager playerStateManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        luckPermsManager = new LuckPermsManager(this);
        luckPermsManager.init();

        playerStateManager = new PlayerStateManager();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        registerCommand("cw",        new CwCommand(this));
        registerCommand("staffchat", new StaffChatCommand(this));
        registerCommand("chatspy",   new ChatSpyCommand(this));

        getLogger().info("CommandWorld enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CommandWorld disabled.");
    }

    /**
     * On join, delay the command list refresh by 2 ticks.
     *
     * Reason: PlayerCommandSendEvent fires during the join sequence BEFORE
     * LuckPerms has finished loading the player's group data from its async
     * storage backend. This means getPrimaryGroup() returns null on the first
     * fire, so the LP-group rule is skipped and the player might see more
     * commands than they should.
     *
     * Waiting 2 ticks (100ms) gives LuckPerms enough time to cache the user,
     * then we re-fire PlayerCommandSendEvent via updateCommands() with the
     * correct group resolved.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTaskLater(this, player::updateCommands, 2L);
    }

    /**
     * When a player changes worlds, their allowed command list may differ.
     * Re-send the command list packet so the client updates immediately.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, player::updateCommands);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().log(Level.WARNING, "Command '/{0}' not found in plugin.yml!", name);
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    public void reload() {
        reloadConfig();
        pluginConfig.load();
        // Refresh the command list for all online players so changes apply instantly
        for (Player player : getServer().getOnlinePlayers()) {
            player.updateCommands();
        }
        getLogger().info("CommandWorld configuration reloaded.");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static CommandWorldPlugin getInstance() { return instance; }
    public PluginConfig getPluginConfig()          { return pluginConfig; }
    public LuckPermsManager getLuckPermsManager()  { return luckPermsManager; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
}
