package dev.commandworld.command;

import dev.commandworld.CommandWorldPlugin;
import dev.commandworld.config.PluginConfig;
import dev.commandworld.manager.PlayerStateManager;
import dev.commandworld.util.ChatHelper;
import dev.commandworld.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /staffchat [message]
 * <ul>
 *   <li>No args → toggle staff-chat mode</li>
 *   <li>With args → send a one-off staff message without toggling</li>
 * </ul>
 */
public class StaffChatCommand implements CommandExecutor, TabCompleter {

    private final CommandWorldPlugin plugin;

    public StaffChatCommand(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        if (!player.hasPermission("commandworld.staffchat")) {
            MiniMessageUtil.send(player, plugin.getPluginConfig().getMessage("no-permission"));
            return true;
        }

        PluginConfig cfg          = plugin.getPluginConfig();
        PlayerStateManager states = plugin.getPlayerStateManager();

        if (args.length == 0) {
            // Toggle mode
            boolean active = states.toggleStaffChat(player);
            MiniMessageUtil.send(player, cfg.getMessage(active ? "staffchat-on" : "staffchat-off"));
            return true;
        }

        // One-shot: send message without toggling
        String message   = String.join(" ", args);
        String formatted = MiniMessageUtil.applyPlaceholders(cfg.getStaffChatFormat(),
                player.getName(), message, player.getWorld().getName(),
                ChatHelper.getPrefix(player), ChatHelper.getSuffix(player));
        Component msg = MiniMessageUtil.parse(formatted);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("commandworld.staffchat") || p.hasPermission("commandworld.staff")) {
                p.sendMessage(msg);
            }
        }
        plugin.getLogger().info("[StaffChat] " + player.getName() + ": " + message);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of(); // No completions for chat messages
    }
}
