package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Command handler for graves feature.
 * Player commands: /grave, /graves
 * Admin commands: /graveadmin
 */
public class GraveCommands implements CommandExecutor, TabCompleter {

    private final NekoPlugin plugin;
    private final GraveManager graveManager;

    public GraveCommands(NekoPlugin plugin, GraveManager graveManager) {
        this.plugin = plugin;
        this.graveManager = graveManager;
    }

    /**
     * Registers all grave commands.
     */
    public void register() {
        plugin.getCommand("grave").setExecutor(this);
        plugin.getCommand("grave").setTabCompleter(this);
        plugin.getCommand("graves").setExecutor(this);
        plugin.getCommand("graves").setTabCompleter(this);
        plugin.getCommand("graveadmin").setExecutor(this);
        plugin.getCommand("graveadmin").setTabCompleter(this);
    }

    /**
     * Unregisters all grave commands.
     */
    public void unregister() {
        PluginCommand graveCmd = plugin.getCommand("grave");
        if (graveCmd != null) {
            graveCmd.setExecutor(null);
            graveCmd.setTabCompleter(null);
        }
        
        PluginCommand gravesCmd = plugin.getCommand("graves");
        if (gravesCmd != null) {
            gravesCmd.setExecutor(null);
            gravesCmd.setTabCompleter(null);
        }
        
        PluginCommand adminCmd = plugin.getCommand("graveadmin");
        if (adminCmd != null) {
            adminCmd.setExecutor(null);
            adminCmd.setTabCompleter(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("grave") || cmd.getName().equalsIgnoreCase("graves")) {
            return handlePlayerCommand(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("graveadmin")) {
            return handleAdminCommand(sender, args);
        }
        return false;
    }

    /**
     * Handles player grave commands.
     */
    private boolean handlePlayerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!")
                .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // Show player's graves
            showPlayerGraves(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        player.sendMessage(Component.text("Unknown command. Use /grave help for help.")
            .color(NamedTextColor.RED));
        return true;
    }

    /**
     * Handles admin grave commands.
     */
    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showAdminHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listAllGraves(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("delete") && args.length >= 2) {
            deletePlayerGrave(sender, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("retrieve") && args.length >= 2) {
            retrievePlayerGrave(sender, args[1]);
            return true;
        }

        sender.sendMessage(Component.text("Unknown command. Use /graveadmin for help.")
            .color(NamedTextColor.RED));
        return true;
    }

    /**
     * Shows a player their graves.
     */
    private void showPlayerGraves(Player player) {
        List<Grave> graves = graveManager.getPlayerGraves(player.getUniqueId());
        
        if (graves.isEmpty()) {
            player.sendMessage(Component.text("You don't have any graves.")
                .color(NamedTextColor.GREEN));
            return;
        }

        player.sendMessage(Component.text("=== Your Graves (" + graves.size() + ") ===")
            .color(NamedTextColor.GOLD));
        
        for (Grave grave : graves) {
            Component graveInfo = Component.text("• ")
                .append(Component.text(grave.getItemCount() + " items")
                    .color(NamedTextColor.WHITE))
                .append(Component.text(" at ")
                    .color(NamedTextColor.GRAY))
                .append(Component.text(formatLocation(grave.getGraveLocation()))
                    .color(NamedTextColor.GREEN))
                .append(Component.text(" (expires in " + grave.getFormattedRemainingTime() + ")")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(graveInfo);
        }
    }

    /**
     * Lists all active graves (admin only).
     */
    private void listAllGraves(CommandSender sender) {
        Collection<Grave> allGraves = graveManager.getAllGraves();
        
        if (allGraves.isEmpty()) {
            sender.sendMessage(Component.text("No active graves.")
                .color(NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.text("=== All Active Graves (" + allGraves.size() + ") ===")
            .color(NamedTextColor.GOLD));
        
        for (Grave grave : allGraves) {
            Component graveInfo = Component.text("• ")
                .append(Component.text(grave.getPlayerName())
                    .color(NamedTextColor.WHITE))
                .append(Component.text(" - ")
                    .color(NamedTextColor.GRAY))
                .append(Component.text(grave.getItemCount() + " items")
                    .color(NamedTextColor.YELLOW))
                .append(Component.text(" at ")
                    .color(NamedTextColor.GRAY))
                .append(Component.text(formatLocation(grave.getGraveLocation()))
                    .color(NamedTextColor.GREEN));
            sender.sendMessage(graveInfo);
        }
    }

    /**
     * Deletes a player's grave (admin only).
     */
    private void deletePlayerGrave(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        List<Grave> graves = graveManager.getPlayerGraves(target.getUniqueId());
        
        if (graves.isEmpty()) {
            sender.sendMessage(Component.text(playerName + " has no graves.")
                .color(NamedTextColor.RED));
            return;
        }

        int deletedCount = 0;
        for (Grave grave : graves) {
            graveManager.removeGrave(grave, true); // Drop items
            deletedCount++;
        }

        sender.sendMessage(Component.text("Deleted " + deletedCount + " grave(s) for " + playerName)
            .color(NamedTextColor.GREEN));
        plugin.getLogger().info(sender.getName() + " deleted " + deletedCount + " graves for " + playerName);
    }

    /**
     * Retrieves a player's grave items (admin only).
     */
    private void retrievePlayerGrave(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        List<Grave> graves = graveManager.getPlayerGraves(target.getUniqueId());
        
        if (graves.isEmpty()) {
            sender.sendMessage(Component.text(playerName + " has no graves.")
                .color(NamedTextColor.RED));
            return;
        }

        // Give items to admin or drop at their location
        if (sender instanceof Player adminPlayer) {
            for (Grave grave : graves) {
                for (ItemStack item : grave.getItems()) {
                    if (item != null && !item.getType().isAir()) {
                        adminPlayer.getInventory().addItem(item);
                    }
                }
                graveManager.removeGrave(grave, false);
            }
            sender.sendMessage(Component.text("Retrieved items from " + playerName + "'s grave(s)")
                .color(NamedTextColor.GREEN));
        }
    }

    /**
     * Shows help message to players.
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== Grave Commands ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/grave - View your graves").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("/grave help - Show this help").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("Right-click your grave to retrieve items.").color(NamedTextColor.YELLOW));
    }

    /**
     * Shows admin help message.
     */
    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Grave Admin Commands ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/graveadmin list - List all graves").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/graveadmin delete <player> - Delete player's graves").color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/graveadmin retrieve <player> - Retrieve player's items").color(NamedTextColor.WHITE));
    }

    /**
     * Formats a location as a string.
     */
    private String formatLocation(org.bukkit.Location location) {
        return String.format("%s (%d, %d, %d)",
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (cmd.getName().equalsIgnoreCase("graveadmin") && sender.isOp()) {
            if (args.length == 1) {
                completions.add("list");
                completions.add("delete");
                completions.add("retrieve");
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("retrieve"))) {
                // Complete with online player names
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if ((cmd.getName().equalsIgnoreCase("grave") || cmd.getName().equalsIgnoreCase("graves")) && args.length == 1) {
            completions.add("help");
        }
        
        return completions;
    }
}
