package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command handler for TreeFeller feature.
 * Admin commands: /treefeller debug on|off|status
 */
public class TreeFellerCommands implements CommandExecutor, TabCompleter {

    private final NekoPlugin plugin;
    private final TreeFellerListener treeFellerListener;

    public TreeFellerCommands(NekoPlugin plugin, TreeFellerListener treeFellerListener) {
        this.plugin = plugin;
        this.treeFellerListener = treeFellerListener;
    }

    /**
     * Registers all treefeller commands.
     * Paper plugins must register commands programmatically via CommandMap.
     */
    public void register() {
        org.bukkit.command.Command command = new org.bukkit.command.Command("treefeller") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                return TreeFellerCommands.this.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                return TreeFellerCommands.this.onTabComplete(sender, this, alias, args);
            }
        };
        
        command.setDescription("TreeFeller feature commands");
        command.setUsage("/treefeller <debug|help>");
        command.setPermission("op");
        command.setAliases(java.util.List.of("tf"));
        
        // Register via CommandMap (Paper plugin standard)
        plugin.getServer().getCommandMap().register("treefeller", command);
    }

    /**
     * Unregisters all treefeller commands.
     */
    public void unregister() {
        org.bukkit.command.Command command = plugin.getServer().getCommandMap().getCommand("treefeller");
        if (command != null) {
            command.unregister(plugin.getServer().getCommandMap());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            return handleDebug(sender, args);
        }

        if (args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        sender.sendMessage(Component.text("Unknown command. Use /treefeller help for help.")
            .color(NamedTextColor.RED));
        return true;
    }

    /**
     * Handles debug subcommand.
     */
    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Show current debug status
            boolean isDebug = treeFellerListener.isDebugging(sender instanceof Player ? (Player) sender : null);
            sender.sendMessage(Component.text("TreeFeller debug is currently: ")
                .color(NamedTextColor.GOLD)
                .append(Component.text(isDebug ? "ON" : "OFF")
                    .color(isDebug ? NamedTextColor.GREEN : NamedTextColor.RED)));
            return true;
        }

        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("on")) {
                treeFellerListener.setDebugging(sender instanceof Player ? (Player) sender : null, true);
                sender.sendMessage(Component.text("TreeFeller debug enabled!")
                    .color(NamedTextColor.GREEN));
                return true;
            }

            if (args[1].equalsIgnoreCase("off")) {
                treeFellerListener.setDebugging(sender instanceof Player ? (Player) sender : null, false);
                sender.sendMessage(Component.text("TreeFeller debug disabled!")
                    .color(NamedTextColor.GREEN));
                return true;
            }

            if (args[1].equalsIgnoreCase("status")) {
                boolean isDebug = treeFellerListener.isDebugging(sender instanceof Player ? (Player) sender : null);
                sender.sendMessage(Component.text("TreeFeller debug status: ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(isDebug ? "ON" : "OFF")
                        .color(isDebug ? NamedTextColor.GREEN : NamedTextColor.RED)));
                return true;
            }
        }

        sender.sendMessage(Component.text("Usage: /treefeller debug <on|off|status>")
            .color(NamedTextColor.RED));
        return true;
    }

    /**
     * Shows help message.
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== TreeFeller Commands ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/treefeller debug on - Enable debug messages").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/treefeller debug off - Disable debug messages").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/treefeller debug status - Check debug status").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/treefeller help - Show this help").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Note: Only OPs can use these commands.").color(NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.isOp()) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("debug");
            completions.add("help");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            completions.add("on");
            completions.add("off");
            completions.add("status");
        }

        return completions;
    }
}
