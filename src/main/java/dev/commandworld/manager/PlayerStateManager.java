package dev.commandworld.manager;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player in-session toggles.
 * All state is ephemeral — cleared on quit.
 *
 * ChatSpy is toggle-only. No auto-enable, no bypass lock.
 */
public class PlayerStateManager {

    private final Set<UUID> staffChatActive = ConcurrentHashMap.newKeySet();
    private final Set<UUID> chatSpyActive   = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> chatCooldowns = new ConcurrentHashMap<>();

    // ── Staff chat ────────────────────────────────────────────────────────────

    public boolean toggleStaffChat(Player player) {
        UUID id = player.getUniqueId();
        if (staffChatActive.remove(id)) return false;
        staffChatActive.add(id);
        return true;
    }

    public void disableStaffChat(Player player) {
        staffChatActive.remove(player.getUniqueId());
    }

    public boolean isStaffChatActive(Player player) {
        return staffChatActive.contains(player.getUniqueId());
    }

    // ── Chat spy (toggle-only) ────────────────────────────────────────────────

    /** Toggles chat spy. Returns true if now active, false if now inactive. */
    public boolean toggleChatSpy(Player player) {
        UUID id = player.getUniqueId();
        if (chatSpyActive.remove(id)) return false;
        chatSpyActive.add(id);
        return true;
    }

    public boolean isChatSpyActive(Player player) {
        return chatSpyActive.contains(player.getUniqueId());
    }

    public Collection<UUID> getChatSpyPlayers() {
        return Collections.unmodifiableSet(chatSpyActive);
    }

    // ── Cooldowns ─────────────────────────────────────────────────────────────

    public double getRemainingCooldown(Player player) {
        Long expiry = chatCooldowns.get(player.getUniqueId());
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000.0 : 0;
    }

    public void setCooldown(Player player, double seconds) {
        if (seconds <= 0) return;
        chatCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + (long)(seconds * 1000));
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        staffChatActive.remove(id);
        chatSpyActive.remove(id);
        chatCooldowns.remove(id);
    }
}
