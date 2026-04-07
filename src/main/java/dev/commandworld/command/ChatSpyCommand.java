package dev.commandworld.command;

import dev.commandworld.CommandWorldPlugin;
import dev.commandworld.config.PluginConfig;
import dev.commandworld.manager.PlayerStateManager;
import dev.commandworld.util.MiniMessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /chatspy — toggle global chat monitoring for staff.
 * Toggle-only. No auto-enable, no bypass lock.
 */
public class ChatSpyCommand implements CommandExecutor, TabCompleter {

    private final CommandWorldPlugin plugin;

    public ChatSpyCommand(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        PluginConfig cfg = plugin.getPluginConfig();

        if (!cfg.isChatSpyEnabled()) {
            MiniMessageUtil.send(player, cfg.getMessage("chatspy-disabled"));
            return true;
        }

        if (!player.hasPermission("commandworld.chatspy")) {
            MiniMessageUtil.send(player, cfg.getMessage("no-permission"));
            return true;
        }

        PlayerStateManager states = plugin.getPlayerStateManager();
        boolean active = states.toggleChatSpy(player);
        MiniMessageUtil.send(player, cfg.getMessage(active ? "chatspy-on" : "chatspy-off"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
