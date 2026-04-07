package dev.commandworld.config;

import dev.commandworld.CommandWorldPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Typed wrapper around config.yml.
 * Call {@link #load()} after every reload.
 */
public class PluginConfig {

    private final CommandWorldPlugin plugin;

    // ── World groups: groupName → list of world names ─────────────────────────
    private Map<String, List<String>> worldGroups = new HashMap<>();

    // Reverse lookup: worldName → groupName
    private Map<String, String> worldToGroup = new HashMap<>();

    // ── Command rules ─────────────────────────────────────────────────────────
    // Key formats:
    //   "global"
    //   "group:<groupName>"   (world-group)
    //   "world:<worldName>"
    //   "lp:<lpGroup>"        (LuckPerms group)
    private Map<String, CommandRule> commandRules = new HashMap<>();

    // ── Chat ──────────────────────────────────────────────────────────────────
    private boolean chatEnabled;
    private Map<String, String> chatFormats = new HashMap<>();
    private Map<String, Double> chatCooldowns = new HashMap<>();
    private double globalCooldown;
    private String cooldownMessage;
    private List<String> blockedWords = new ArrayList<>();
    private String blockedWordMessage;
    private boolean perWorldJoinLeave;
    private Map<String, String> joinFormats = new HashMap<>();
    private Map<String, String> leaveFormats = new HashMap<>();
    private boolean clearOnSwitch;

    // ── Global chat ───────────────────────────────────────────────────────────
    private boolean globalChatEnabled;
    private String globalChatPrefix;
    private String globalChatFormat;
    private boolean globalChatRequirePermission;

    // ── Staff chat ────────────────────────────────────────────────────────────
    private boolean staffChatEnabled;
    private String staffChatFormat;
    private String staffChatSound;

    // ── Chat spy ──────────────────────────────────────────────────────────────
    private boolean chatSpyEnabled;
    private String  chatSpyFormat;
    private boolean chatSpyIncludeStaff;
    private boolean chatSpyIncludeGlobal;
    private boolean chatSpyIncludeOwn;
    private String  chatSpySound;

    // ── Messages ──────────────────────────────────────────────────────────────
    private Map<String, String> messages = new HashMap<>();

    public PluginConfig(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration cfg = plugin.getConfig();

        // ── World groups ──────────────────────────────────────────────────────
        worldGroups.clear();
        worldToGroup.clear();
        ConfigurationSection wgSec = cfg.getConfigurationSection("world-groups");
        if (wgSec != null) {
            for (String group : wgSec.getKeys(false)) {
                List<String> worlds = wgSec.getStringList(group);
                worldGroups.put(group, worlds);
                for (String w : worlds) worldToGroup.put(w.toLowerCase(), group);
            }
        }

        // ── Command rules ─────────────────────────────────────────────────────
        commandRules.clear();

        // Global
        loadCommandRule(cfg, "global", "global");

        // World-group rules
        ConfigurationSection wgCmd = cfg.getConfigurationSection("world-group-commands");
        if (wgCmd != null) {
            for (String g : wgCmd.getKeys(false)) {
                loadCommandRule(cfg, "world-group-commands." + g, "group:" + g);
            }
        }

        // Per-world rules
        ConfigurationSection wcmd = cfg.getConfigurationSection("world-commands");
        if (wcmd != null) {
            for (String w : wcmd.getKeys(false)) {
                loadCommandRule(cfg, "world-commands." + w, "world:" + w.toLowerCase());
            }
        }

        // LuckPerms group rules
        ConfigurationSection lpGroups = cfg.getConfigurationSection("groups");
        if (lpGroups != null) {
            for (String g : lpGroups.getKeys(false)) {
                loadCommandRule(cfg, "groups." + g, "lp:" + g);
            }
        }

        // ── Chat ──────────────────────────────────────────────────────────────
        chatEnabled = cfg.getBoolean("chat.enabled", true);

        chatFormats.clear();
        ConfigurationSection fmtSec = cfg.getConfigurationSection("chat.format");
        if (fmtSec != null) {
            for (String k : fmtSec.getKeys(false)) chatFormats.put(k, fmtSec.getString(k, ""));
        }

        chatCooldowns.clear();
        globalCooldown = cfg.getDouble("chat.cooldown.global", 0);
        ConfigurationSection cdSec = cfg.getConfigurationSection("chat.cooldown");
        if (cdSec != null) {
            for (String k : cdSec.getKeys(false)) {
                if (!k.equals("global")) chatCooldowns.put(k, cdSec.getDouble(k, 0));
            }
        }

        cooldownMessage    = cfg.getString("chat.cooldown-message", "<red>Please wait before chatting again.");
        blockedWords       = cfg.getStringList("chat.blocked-words");
        blockedWordMessage = cfg.getString("chat.blocked-word-message", "<red>Message blocked.");
        perWorldJoinLeave  = cfg.getBoolean("chat.per-world-join-leave", true);
        clearOnSwitch      = cfg.getBoolean("chat.clear-on-switch", false);

        joinFormats.clear();
        ConfigurationSection jfSec = cfg.getConfigurationSection("chat.join-format");
        if (jfSec != null) {
            for (String k : jfSec.getKeys(false)) joinFormats.put(k, jfSec.getString(k, ""));
        }

        leaveFormats.clear();
        ConfigurationSection lfSec = cfg.getConfigurationSection("chat.leave-format");
        if (lfSec != null) {
            for (String k : lfSec.getKeys(false)) leaveFormats.put(k, lfSec.getString(k, ""));
        }

        // ── Global chat ───────────────────────────────────────────────────────
        globalChatEnabled           = cfg.getBoolean("global-chat.enabled", true);
        globalChatPrefix            = cfg.getString("global-chat.prefix", "!");
        globalChatFormat            = cfg.getString("global-chat.format", "<dark_aqua>[Global] <white>%player%: %message%");
        globalChatRequirePermission = cfg.getBoolean("global-chat.require-permission", false);

        // ── Staff chat ────────────────────────────────────────────────────────
        staffChatEnabled = cfg.getBoolean("staff-chat.enabled", true);
        staffChatFormat  = cfg.getString("staff-chat.format", "<dark_red>[Staff] <gray>%player%: <red>%message%");
        staffChatSound   = cfg.getString("staff-chat.sound", "");

        // ── Chat spy ──────────────────────────────────────────────────────────
        chatSpyEnabled        = cfg.getBoolean("chat-spy.enabled", true);
        chatSpyFormat         = cfg.getString("chat-spy.format", "<dark_gray>[<gray>Spy<dark_gray>][<aqua>%world%</aqua>] <gray>%player%<dark_gray>: <white>%message%");
        chatSpyIncludeStaff   = cfg.getBoolean("chat-spy.include-staff-chat", false);
        chatSpyIncludeGlobal  = cfg.getBoolean("chat-spy.include-global-chat", false);
        chatSpyIncludeOwn     = cfg.getBoolean("chat-spy.include-own-context", true);
        chatSpySound          = cfg.getString("chat-spy.sound", "");

        // ── Messages ──────────────────────────────────────────────────────────
        messages.clear();
        ConfigurationSection msgSec = cfg.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String k : msgSec.getKeys(false)) messages.put(k, msgSec.getString(k, ""));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void loadCommandRule(FileConfiguration cfg, String path, String key) {
        String mode       = cfg.getString(path + ".mode", "blacklist");
        List<String> exec = normalise(cfg.getStringList(path + ".execution"));
        List<String> tab  = normalise(cfg.getStringList(path + ".tab-complete"));
        commandRules.put(key, new CommandRule(mode.equalsIgnoreCase("whitelist"), exec, tab));
    }

    private List<String> normalise(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            out.add(s.startsWith("/") ? s.substring(1).toLowerCase() : s.toLowerCase());
        }
        return out;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the group name for a world, or null if not grouped. */
    public String getWorldGroup(String worldName) {
        return worldToGroup.get(worldName.toLowerCase());
    }

    public Map<String, List<String>> getWorldGroups() { return worldGroups; }

    /**
     * Resolve the effective CommandRule for a player given their LuckPerms
     * primary group, the world they are in, and the world-group.
     * Priority: lp-group > world-group > individual world > global
     */
    public CommandRule resolveCommandRule(String lpGroup, String worldName, String worldGroup) {
        // 1. LuckPerms group
        if (lpGroup != null) {
            CommandRule r = commandRules.get("lp:" + lpGroup);
            if (r != null) return r;
        }
        // 2. World group
        if (worldGroup != null) {
            CommandRule r = commandRules.get("group:" + worldGroup);
            if (r != null) return r;
        }
        // 3. Individual world
        CommandRule r = commandRules.get("world:" + worldName.toLowerCase());
        if (r != null) return r;
        // 4. Global
        r = commandRules.get("global");
        return r != null ? r : CommandRule.ALLOW_ALL;
    }

    public boolean isChatEnabled()            { return chatEnabled; }
    public boolean isGlobalChatEnabled()      { return globalChatEnabled; }
    public String  getGlobalChatPrefix()      { return globalChatPrefix; }
    public String  getGlobalChatFormat()      { return globalChatFormat; }
    public boolean isGlobalChatPermRequired() { return globalChatRequirePermission; }

    public boolean isStaffChatEnabled()  { return staffChatEnabled; }
    public String  getStaffChatFormat()  { return staffChatFormat; }
    public String  getStaffChatSound()   { return staffChatSound; }

    public boolean isChatSpyEnabled()       { return chatSpyEnabled; }
    public String  getChatSpyFormat()       { return chatSpyFormat; }
    public boolean isChatSpyIncludeStaff()  { return chatSpyIncludeStaff; }
    public boolean isChatSpyIncludeGlobal() { return chatSpyIncludeGlobal; }
    public boolean isChatSpyIncludeOwn()    { return chatSpyIncludeOwn; }
    public String  getChatSpySound()        { return chatSpySound; }

    public String getChatFormat(String groupOrWorld) {
        return chatFormats.getOrDefault(groupOrWorld,
               chatFormats.getOrDefault("global",
               "<white>%player%<dark_gray>: <gray>%message%"));
    }

    public double getChatCooldown(String groupOrWorld) {
        return chatCooldowns.getOrDefault(groupOrWorld, globalCooldown);
    }

    public String getCooldownMessage()  { return cooldownMessage; }
    public List<String> getBlockedWords() { return blockedWords; }
    public String getBlockedWordMessage() { return blockedWordMessage; }
    public boolean isPerWorldJoinLeave()  { return perWorldJoinLeave; }
    public boolean isClearOnSwitch()      { return clearOnSwitch; }

    public String getJoinFormat(String groupOrWorld) {
        return joinFormats.getOrDefault(groupOrWorld,
               joinFormats.getOrDefault("global", null));
    }

    public String getLeaveFormat(String groupOrWorld) {
        return leaveFormats.getOrDefault(groupOrWorld,
               leaveFormats.getOrDefault("global", null));
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "<red>Missing message: " + key);
    }
}
