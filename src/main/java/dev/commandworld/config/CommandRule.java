package dev.commandworld.config;

import java.util.List;

public final class CommandRule {

    public static final CommandRule ALLOW_ALL = new CommandRule(false, List.of(), List.of());

    private final boolean whitelist;
    private final List<String> execution;    
    private final List<String> tabComplete;  

    public CommandRule(boolean whitelist, List<String> execution, List<String> tabComplete) {
        this.whitelist   = whitelist;
        this.execution   = execution;
        this.tabComplete = tabComplete;
    }

    public boolean canExecute(String command) {
        boolean inList = execution.contains(command);
        return whitelist ? inList : !inList;
    }

    public boolean isVisible(String command) {
        boolean inList = tabComplete.contains(command);
        return whitelist ? inList : !inList;
    }

    public boolean isWhitelist()      { return whitelist; }
    public List<String> getExecution()   { return execution; }
    public List<String> getTabComplete() { return tabComplete; }
}
