package dev.commandworld.config;

import java.util.List;

/**
 * Represents a resolved command rule for a specific scope.
 *
 * <p>In <b>whitelist</b> mode only commands in the lists are allowed/visible.
 * In <b>blacklist</b> mode only commands in the lists are blocked/hidden.
 */
public final class CommandRule {

    /** A rule that allows everything — used as the fallback global default. */
    public static final CommandRule ALLOW_ALL = new CommandRule(false, List.of(), List.of());

    private final boolean whitelist;
    private final List<String> execution;    // normalised (no leading slash, lowercase)
    private final List<String> tabComplete;  // normalised

    public CommandRule(boolean whitelist, List<String> execution, List<String> tabComplete) {
        this.whitelist   = whitelist;
        this.execution   = execution;
        this.tabComplete = tabComplete;
    }

    /**
     * Returns {@code true} when the command is allowed to execute.
     * @param command Bare command label, lowercase, no leading slash.
     */
    public boolean canExecute(String command) {
        boolean inList = execution.contains(command);
        return whitelist ? inList : !inList;
    }

    /**
     * Returns {@code true} when the command should appear in tab-complete.
     * @param command Bare command label, lowercase, no leading slash.
     */
    public boolean isVisible(String command) {
        boolean inList = tabComplete.contains(command);
        return whitelist ? inList : !inList;
    }

    public boolean isWhitelist()      { return whitelist; }
    public List<String> getExecution()   { return execution; }
    public List<String> getTabComplete() { return tabComplete; }
}
