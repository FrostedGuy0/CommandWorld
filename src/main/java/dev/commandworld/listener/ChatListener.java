package dev.commandworld.listener;

import dev.commandworld.CommandWorldPlugin;
import dev.commandworld.config.PluginConfig;
import dev.commandworld.manager.PlayerStateManager;
import dev.commandworld.util.ChatHelper;
import dev.commandworld.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Central chat router for CommandWorld.
 *
 * Message routing priority (first match wins):
 *   1. Blocked-word filter
 *   2. Global chat (! prefix) — checked BEFORE staff chat so !hello always works
 *   3. Staff chat (toggled via /staffchat)
 *   4. Per-world / world-group chat
 *
 * ChatSpy is layered on top of world chat only.
 * ChatSpy is toggle-only — no auto-enable on join.
 */
@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final CommandWorldPlugin plugin;

    public ChatListener(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Join / quit ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PluginConfig cfg = plugin.getPluginConfig();

        // ChatSpy is toggle-only — never auto-enabled on join

        if (!cfg.isPerWorldJoinLeave()) return;

        String context = ChatHelper.getEffectiveContext(plugin, player);
        String fmt = cfg.getJoinFormat(context);
        if (fmt == null) return;

        String formatted = MiniMessageUtil.applyPlaceholders(
                fmt, player.getName(), "", context,
                ChatHelper.getPrefix(player), ChatHelper.getSuffix(player));
        event.joinMessage(null);
        broadcastToContext(MiniMessageUtil.parse(formatted), context);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PluginConfig cfg = plugin.getPluginConfig();

        plugin.getPlayerStateManager().remove(player);

        if (!cfg.isPerWorldJoinLeave()) return;

        String context = ChatHelper.getEffectiveContext(plugin, player);
        String fmt = cfg.getLeaveFormat(context);
        if (fmt == null) return;

        String formatted = MiniMessageUtil.applyPlaceholders(
                fmt, player.getName(), "", context,
                ChatHelper.getPrefix(player), ChatHelper.getSuffix(player));
        event.quitMessage(null);
        broadcastToContext(MiniMessageUtil.parse(formatted), context);
    }

    // ── Main chat handler ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        event.setCancelled(true);

        Player player  = event.getPlayer();
        String message = event.getMessage();
        PluginConfig cfg = plugin.getPluginConfig();
        PlayerStateManager states = plugin.getPlayerStateManager();

        // 1. Blocked words — always checked first regardless of mode
        for (String word : cfg.getBlockedWords()) {
            if (message.toLowerCase().contains(word.toLowerCase())) {
                MiniMessageUtil.send(player, cfg.getBlockedWordMessage());
                return;
            }
        }

        // 2. Global chat prefix — checked BEFORE staff chat so !hello always
        //    broadcasts globally even when staffchat mode is active
        if (cfg.isGlobalChatEnabled()) {
            String gPrefix = cfg.getGlobalChatPrefix();
            if (!gPrefix.isEmpty() && message.startsWith(gPrefix)) {
                if (cfg.isGlobalChatPermRequired() && !player.hasPermission("commandworld.globalchat")) {
                    MiniMessageUtil.send(player, cfg.getMessage("no-permission"));
                    return;
                }
                String trimmed = message.substring(gPrefix.length()).trim();
                if (!trimmed.isEmpty()) {
                    sendGlobalChat(player, trimmed);
                    return;
                }
            }
        }

        // 3. Staff chat — only reached if message didn't start with global prefix
        if (cfg.isStaffChatEnabled() && states.isStaffChatActive(player)) {
            if (!player.hasPermission("commandworld.staffchat")) {
                states.disableStaffChat(player);
            } else {
                sendStaffChat(player, message);
                return;
            }
        }

        // 4. World / group chat
        if (!cfg.isChatEnabled()) {
            broadcastToAll(player, message);
            return;
        }

        double remaining = states.getRemainingCooldown(player);
        if (remaining > 0) {
            MiniMessageUtil.send(player, cfg.getCooldownMessage()
                    .replace("%remaining%", String.format("%.1f", remaining)));
            return;
        }

        sendWorldChat(player, message);
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    private void sendWorldChat(Player player, String message) {
        PluginConfig cfg = plugin.getPluginConfig();
        PlayerStateManager states = plugin.getPlayerStateManager();

        String world   = player.getWorld().getName();
        String context = ChatHelper.getEffectiveContext(plugin, player);
        String prefix  = ChatHelper.getPrefix(player);
        String suffix  = ChatHelper.getSuffix(player);

        String fmt       = cfg.getChatFormat(context);
        String formatted = MiniMessageUtil.applyPlaceholders(fmt, player.getName(), message, context, prefix, suffix);
        Component msg    = MiniMessageUtil.parse(formatted);

        states.setCooldown(player, cfg.getChatCooldown(context));

        // Collect UUIDs of active spies so we can exclude them from normal
        // delivery and send them the spy-formatted version instead
        java.util.Set<UUID> spyIds = new java.util.HashSet<>(states.getChatSpyPlayers());

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            boolean isSpy = spyIds.contains(recipient.getUniqueId());
            boolean sameContext = ChatHelper.getEffectiveContext(plugin, recipient).equals(context);

            if (sameContext) {
                // Always deliver normal message to same-context players,
                // even if they're a spy — they're part of this chat
                recipient.sendMessage(msg);
            }
            // Spies in other contexts get the spy-formatted copy below
        }

        // ChatSpy: send spy-formatted copy to spies NOT in this context
        dispatchToSpies(player, world, context, message, prefix, suffix);

        plugin.getLogger().info(String.format("[%s] %s: %s", context, player.getName(), message));
    }

    private void sendGlobalChat(Player player, String message) {
        PluginConfig cfg = plugin.getPluginConfig();
        String context   = ChatHelper.getEffectiveContext(plugin, player);
        String prefix    = ChatHelper.getPrefix(player);
        String suffix    = ChatHelper.getSuffix(player);

        String formatted = MiniMessageUtil.applyPlaceholders(
                cfg.getGlobalChatFormat(), player.getName(), message, context, prefix, suffix);
        Component msg = MiniMessageUtil.parse(formatted);

        // Send to all players — global chat is for everyone, no spy duplication
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));

        plugin.getLogger().info(String.format("[Global] %s: %s", player.getName(), message));
    }

    private void sendStaffChat(Player player, String message) {
        PluginConfig cfg = plugin.getPluginConfig();
        String prefix    = ChatHelper.getPrefix(player);
        String suffix    = ChatHelper.getSuffix(player);

        String formatted = MiniMessageUtil.applyPlaceholders(
                cfg.getStaffChatFormat(), player.getName(), message,
                player.getWorld().getName(), prefix, suffix);
        Component msg = MiniMessageUtil.parse(formatted);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("commandworld.staffchat") || p.hasPermission("commandworld.staff")) {
                p.sendMessage(msg);
            }
        }

        playStaffChatSound(cfg);
        plugin.getLogger().info(String.format("[StaffChat] %s: %s", player.getName(), message));
    }

    // ── ChatSpy ───────────────────────────────────────────────────────────────
    // Only called for world/group chat. Sends spy-formatted copy to active
    // spies who are NOT already in the same context as the sender
    // (those players already got the normal message above).

    private void dispatchToSpies(Player sender, String world, String context,
                                  String message, String prefix, String suffix) {
        PluginConfig cfg = plugin.getPluginConfig();
        PlayerStateManager states = plugin.getPlayerStateManager();
        if (!cfg.isChatSpyEnabled()) return;

        String spyFormatted = cfg.getChatSpyFormat()
                .replace("%world%",   world)
                .replace("%group%",   context)
                .replace("%player%",  sender.getName())
                .replace("%message%", message)
                .replace("%prefix%",  prefix != null ? prefix : "")
                .replace("%suffix%",  suffix != null ? suffix : "");

        Component spyMsg = MiniMessageUtil.parse(spyFormatted);

        for (UUID spyId : states.getChatSpyPlayers()) {
            Player spy = Bukkit.getPlayer(spyId);
            if (spy == null || !spy.isOnline()) continue;
            // Don't send back to sender
            if (spy.getUniqueId().equals(sender.getUniqueId())) continue;
            // Skip spies already in the same context — they got the normal message
            if (ChatHelper.getEffectiveContext(plugin, spy).equals(context)) continue;

            spy.sendMessage(spyMsg);
            playSpySound(spy, cfg);
        }
    }

    // ── Sound helpers ─────────────────────────────────────────────────────────

    private void playSpySound(Player spy, PluginConfig cfg) {
        String soundName = cfg.getChatSpySound();
        if (soundName == null || soundName.isBlank()) return;
        try {
            Sound s = Sound.valueOf(soundName.toUpperCase());
            Bukkit.getScheduler().runTask(plugin, () -> spy.playSound(spy.getLocation(), s, 0.5f, 1.2f));
        } catch (IllegalArgumentException ignored) {}
    }

    private void playStaffChatSound(PluginConfig cfg) {
        String soundName = cfg.getStaffChatSound();
        if (soundName == null || soundName.isBlank()) return;
        try {
            Sound s = Sound.valueOf(soundName.toUpperCase());
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("commandworld.staffchat") || p.hasPermission("commandworld.staff")) {
                        p.playSound(p.getLocation(), s, 1f, 1f);
                    }
                }
            });
        } catch (IllegalArgumentException ignored) {}
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    private void broadcastToAll(Player sender, String message) {
        Component msg = MiniMessageUtil.parse("<gray>" + sender.getName() + ": " + message);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    private void broadcastToContext(Component msg, String context) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ChatHelper.getEffectiveContext(plugin, p).equals(context)) {
                p.sendMessage(msg);
            }
        }
    }
}
