package dev.commandworld.command;

import dev.commandworld.CommandWorldPlugin;
import dev.commandworld.config.PluginConfig;
import dev.commandworld.util.MiniMessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /cw <subcommand> [args]
 *
 * Subcommands:
 *   reload
 *
 *   global add|remove|list execution|tab-complete <command>
 *
 *   world <worldName> add|remove|list execution|tab-complete <command>
 *
 *   worldgroup <groupName> add|remove|list execution|tab-complete <command>
 *
 *   group <lpGroup> add|remove|list execution|tab-complete <command>
 *
 *   mode global|world <name>|worldgroup <name>|group <name>  whitelist|blacklist
 *
 * All changes are written to config.yml immediately and the config is reloaded.
 */
public class CwCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SCOPES      = List.of("global", "world", "worldgroup", "group");
    private static final List<String> ACTIONS      = List.of("add", "remove", "list", "mode");
    private static final List<String> TYPES        = List.of("execution", "tab-complete");
    private static final List<String> MODES        = List.of("whitelist", "blacklist");
    private static final List<String> TOP_ARGS     = List.of("reload", "global", "world", "worldgroup", "group");

    private final CommandWorldPlugin plugin;

    public CwCommand(CommandWorldPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("commandworld.admin")) {
            send(sender, plugin.getPluginConfig().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reload();
                send(sender, plugin.getPluginConfig().getMessage("reload-success"));
            }

            // /cw global add|remove|list execution|tab-complete [cmd]
            case "global" -> {
                if (args.length < 2) { sendHelp(sender, label); return true; }
                handleScopedAction(sender, "global", null, Arrays.copyOfRange(args, 1, args.length));
            }

            // /cw world <worldName> add|remove|list execution|tab-complete [cmd]
            case "world" -> {
                if (args.length < 3) { sendHelp(sender, label); return true; }
                String worldName = args[1];
                handleScopedAction(sender, "world", worldName, Arrays.copyOfRange(args, 2, args.length));
            }

            // /cw worldgroup <groupName> add|remove|list execution|tab-complete [cmd]
            case "worldgroup" -> {
                if (args.length < 3) { sendHelp(sender, label); return true; }
                String groupName = args[1];
                handleScopedAction(sender, "worldgroup", groupName, Arrays.copyOfRange(args, 2, args.length));
            }

            // /cw group <lpGroup> add|remove|list execution|tab-complete [cmd]
            case "group" -> {
                if (args.length < 3) { sendHelp(sender, label); return true; }
                String lpGroup = args[1];
                handleScopedAction(sender, "lp", lpGroup, Arrays.copyOfRange(args, 2, args.length));
            }

            default -> sendHelp(sender, label);
        }
        return true;
    }

    /**
     * Handles add / remove / list / mode for a given scope.
     *
     * @param scope    "global", "world", "worldgroup", "lp"
     * @param name     world/group name, or null for global
     * @param rest     remaining args after the scope identifier
     */
    private void handleScopedAction(CommandSender sender, String scope, String name, String[] rest) {
        if (rest.length == 0) {
            sendScopeHelp(sender, scope, name);
            return;
        }

        String action = rest[0].toLowerCase();

        switch (action) {
            case "list"   -> doList(sender, scope, name);
            case "mode"   -> {
                if (rest.length < 2) { send(sender, "<red>Usage: mode <whitelist|blacklist>"); return; }
                doMode(sender, scope, name, rest[1].toLowerCase());
            }
            case "add", "remove" -> {
                if (rest.length < 3) {
                    send(sender, "<red>Usage: " + action + " <execution|tab-complete> <command>");
                    return;
                }
                String type    = rest[1].toLowerCase();
                String command = normalise(rest[2]);
                if (!type.equals("execution") && !type.equals("tab-complete")) {
                    send(sender, "<red>Type must be 'execution' or 'tab-complete'.");
                    return;
                }
                if (action.equals("add")) doAdd(sender, scope, name, type, command);
                else                      doRemove(sender, scope, name, type, command);
            }
            default -> sendScopeHelp(sender, scope, name);
        }
    }

    // ── Config path resolution ────────────────────────────────────────────────

    /** Returns the YAML path prefix for a scope + name, e.g. "world-commands.world_nether" */
    private String configPath(String scope, String name) {
        return switch (scope) {
            case "global"      -> "global.commands";
            case "world"       -> "world-commands." + name;
            case "worldgroup"  -> "world-group-commands." + name;
            case "lp"          -> "groups." + name;
            default            -> "global.commands";
        };
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doList(CommandSender sender, String scope, String name) {
        FileConfiguration cfg = plugin.getConfig();
        String path = configPath(scope, name);

        String mode = cfg.getString(path + ".mode", "blacklist");
        List<String> exec = cfg.getStringList(path + ".execution");
        List<String> tab  = cfg.getStringList(path + ".tab-complete");

        String scopeLabel = scopeLabel(scope, name);
        send(sender, "<gold><bold>" + scopeLabel + "</bold></gold>");
        send(sender, "<yellow>Mode: <white>" + mode);
        send(sender, "<yellow>Execution (" + exec.size() + "):");
        if (exec.isEmpty()) send(sender, "  <gray>(none)");
        else exec.forEach(c -> send(sender, "  <white>" + c));
        send(sender, "<yellow>Tab-complete (" + tab.size() + "):");
        if (tab.isEmpty()) send(sender, "  <gray>(none)");
        else tab.forEach(c -> send(sender, "  <white>" + c));
    }

    private void doMode(CommandSender sender, String scope, String name, String mode) {
        if (!mode.equals("whitelist") && !mode.equals("blacklist")) {
            send(sender, "<red>Mode must be 'whitelist' or 'blacklist'.");
            return;
        }
        String path = configPath(scope, name);
        plugin.getConfig().set(path + ".mode", mode);
        // Ensure the lists exist so the section isn't empty
        if (!plugin.getConfig().contains(path + ".execution"))
            plugin.getConfig().set(path + ".execution", new ArrayList<>());
        if (!plugin.getConfig().contains(path + ".tab-complete"))
            plugin.getConfig().set(path + ".tab-complete", new ArrayList<>());
        saveAndReload();
        send(sender, "<green>Set mode to <yellow>" + mode + "</yellow> for <aqua>" + scopeLabel(scope, name) + "</aqua>.");
    }

    private void doAdd(CommandSender sender, String scope, String name, String type, String command) {
        String path = configPath(scope, name);
        // Ensure section exists
        ensureSectionExists(path);

        String listPath = path + "." + type;
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(listPath));

        String entry = "/" + command; // store with leading slash for readability (normalised on load)
        if (list.contains(entry) || list.contains(command)) {
            send(sender, "<yellow>'" + command + "' is already in " + type + " list for <aqua>" + scopeLabel(scope, name) + "</aqua>.");
            return;
        }
        list.add(entry);
        plugin.getConfig().set(listPath, list);
        saveAndReload();
        send(sender, "<green>Added <white>/" + command + "</white> to " + type + " list for <aqua>" + scopeLabel(scope, name) + "</aqua>.");
    }

    private void doRemove(CommandSender sender, String scope, String name, String type, String command) {
        String path     = configPath(scope, name);
        String listPath = path + "." + type;
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(listPath));

        boolean removed = list.remove("/" + command) || list.remove(command);
        if (!removed) {
            send(sender, "<yellow>'" + command + "' was not found in " + type + " list for <aqua>" + scopeLabel(scope, name) + "</aqua>.");
            return;
        }
        plugin.getConfig().set(listPath, list);
        saveAndReload();
        send(sender, "<green>Removed <white>/" + command + "</white> from " + type + " list for <aqua>" + scopeLabel(scope, name) + "</aqua>.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureSectionExists(String path) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.contains(path + ".mode"))
            cfg.set(path + ".mode", "whitelist");
        if (!cfg.contains(path + ".execution"))
            cfg.set(path + ".execution", new ArrayList<>());
        if (!cfg.contains(path + ".tab-complete"))
            cfg.set(path + ".tab-complete", new ArrayList<>());
    }

    private void saveAndReload() {
        plugin.saveConfig();
        plugin.reload();
    }

    private String normalise(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1).toLowerCase() : cmd.toLowerCase();
    }

    private String scopeLabel(String scope, String name) {
        return switch (scope) {
            case "global"     -> "Global";
            case "world"      -> "World: " + name;
            case "worldgroup" -> "World-Group: " + name;
            case "lp"         -> "LP-Group: " + name;
            default           -> scope;
        };
    }

    private void send(CommandSender sender, String mm) {
        sender.sendMessage(MiniMessageUtil.parse(mm));
    }

    private void sendHelp(CommandSender sender, String label) {
        send(sender, "<gold><bold>CommandWorld Help</bold></gold>");
        send(sender, "<yellow>/" + label + " reload");
        send(sender, "<yellow>/" + label + " global <add|remove|list|mode> ...");
        send(sender, "<yellow>/" + label + " world <name> <add|remove|list|mode> ...");
        send(sender, "<yellow>/" + label + " worldgroup <name> <add|remove|list|mode> ...");
        send(sender, "<yellow>/" + label + " group <lpGroup> <add|remove|list|mode> ...");
        send(sender, "<gray>add|remove: <white><execution|tab-complete> <command>");
        send(sender, "<gray>mode: <white><whitelist|blacklist>");
    }

    private void sendScopeHelp(CommandSender sender, String scope, String name) {
        String label = scopeLabel(scope, name);
        send(sender, "<gold>" + label + " — actions:");
        send(sender, "  <yellow>list");
        send(sender, "  <yellow>mode <whitelist|blacklist>");
        send(sender, "  <yellow>add <execution|tab-complete> <command>");
        send(sender, "  <yellow>remove <execution|tab-complete> <command>");
    }

    // ── Tab-complete ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("commandworld.admin")) return List.of();

        PluginConfig cfg = plugin.getPluginConfig();

        if (args.length == 1) return filter(TOP_ARGS, args[0]);

        return switch (args[0].toLowerCase()) {
            case "reload" -> List.of();

            // /cw global <action>
            case "global" -> {
                if (args.length == 2) yield filter(ACTIONS, args[1]);
                if (args.length == 3 && args[1].equalsIgnoreCase("mode")) yield filter(MODES, args[2]);
                if (args.length == 3 && isAddRemove(args[1])) yield filter(TYPES, args[2]);
                yield List.of();
            }

            // /cw world <name> <action> ...
            case "world" -> {
                if (args.length == 2) yield filter(worldNames(), args[1]);
                if (args.length == 3) yield filter(ACTIONS, args[2]);
                if (args.length == 4 && args[2].equalsIgnoreCase("mode")) yield filter(MODES, args[3]);
                if (args.length == 4 && isAddRemove(args[2])) yield filter(TYPES, args[3]);
                yield List.of();
            }

            // /cw worldgroup <name> <action> ...
            case "worldgroup" -> {
                if (args.length == 2) yield filter(new ArrayList<>(cfg.getWorldGroups().keySet()), args[1]);
                if (args.length == 3) yield filter(ACTIONS, args[2]);
                if (args.length == 4 && args[2].equalsIgnoreCase("mode")) yield filter(MODES, args[3]);
                if (args.length == 4 && isAddRemove(args[2])) yield filter(TYPES, args[3]);
                yield List.of();
            }

            // /cw group <lpGroup> <action> ...
            case "group" -> {
                if (args.length == 2) yield List.of(); // LP groups not enumerable without LP API here
                if (args.length == 3) yield filter(ACTIONS, args[2]);
                if (args.length == 4 && args[2].equalsIgnoreCase("mode")) yield filter(MODES, args[3]);
                if (args.length == 4 && isAddRemove(args[2])) yield filter(TYPES, args[3]);
                yield List.of();
            }

            default -> List.of();
        };
    }

    private List<String> worldNames() {
        return plugin.getServer().getWorlds().stream()
                .map(w -> w.getName())
                .collect(Collectors.toList());
    }

    private boolean isAddRemove(String s) {
        return s.equalsIgnoreCase("add") || s.equalsIgnoreCase("remove");
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
