package dev.commandworld.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import dev.commandworld.CommandWorldPlugin;
import dev.commandworld.config.CommandRule;
import dev.commandworld.config.PluginConfig;
import dev.commandworld.util.MiniMessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CommandListener implements Listener {

    private final CommandWorldPlugin plugin;

    public CommandListener(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    // ── 1. Command-list packet filtering (PRIMARY tab-complete fix) ────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("commandworld.bypass")) return;

        CommandRule rule = resolveRule(player);
        Iterator<String> it = event.getCommands().iterator();

        while (it.hasNext()) {
            String entry = it.next();
            String label = stripNamespace(entry);

            // Remove if:
            // 1. The bare label is not allowed by the rule, OR
            // 2. The entry is a namespaced duplicate (contains ":") — we never
            //    want "crowneconomy:ah" showing even if "ah" is whitelisted.
            //    Players should only see the clean command name.
            if (!rule.isVisible(label) || entry.contains(":")) {
                it.remove();
            }
        }
    }

    // ── 2. Async tab-complete (server-computed completions) ────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) return;
        if (!event.getBuffer().startsWith("/"))              return;
        if (player.hasPermission("commandworld.bypass"))     return;

        CommandRule rule = resolveRule(player);
        List<AsyncTabCompleteEvent.Completion> filtered = new ArrayList<>();

        for (AsyncTabCompleteEvent.Completion c : event.completions()) {
            String s     = c.suggestion();
            String label = stripNamespace(s.startsWith("/") ? s.substring(1) : s);
            // Same rule: allow only non-namespaced entries that pass the rule
            if (rule.isVisible(label) && !s.contains(":")) filtered.add(c);
        }

        event.completions(filtered);
        event.setHandled(true);
    }

    // ── 3. Execution blocking ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("commandworld.bypass")) return;

        String label = extractLabel(event.getMessage());
        if (!resolveRule(player).canExecute(label)) {
            event.setCancelled(true);
            MiniMessageUtil.send(player, plugin.getPluginConfig().getMessage("command-blocked"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String stripNamespace(String input) {
        String s = input.toLowerCase();
        int colon = s.indexOf(':');
        return colon == -1 ? s : s.substring(colon + 1);
    }

    private String extractLabel(String fullCommand) {
        String stripped = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
        int space = stripped.indexOf(' ');
        String label = space == -1 ? stripped : stripped.substring(0, space);
        return stripNamespace(label);
    }

    private CommandRule resolveRule(Player player) {
        PluginConfig cfg = plugin.getPluginConfig();
        String world   = player.getWorld().getName();
        String group   = cfg.getWorldGroup(world);
        String lpGroup = plugin.getLuckPermsManager().getPrimaryGroup(player);
        return cfg.resolveCommandRule(lpGroup, world, group);
    }
}
