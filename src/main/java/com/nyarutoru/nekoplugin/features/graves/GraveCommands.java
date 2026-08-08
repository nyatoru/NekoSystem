package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class GraveCommands implements CommandExecutor {
    private final NekoPlugin plugin;
    private final GraveManager manager;

    GraveCommands(NekoPlugin plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    void register() {
        command("grave").setExecutor(this);
        command("graveadmin").setExecutor(this);
    }

    void unregister() {
        command("grave").setExecutor(null);
        command("graveadmin").setExecutor(null);
    }

    private PluginCommand command(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) throw new IllegalStateException("Missing command declaration: " + name);
        return command;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("grave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return true;
            }
            list(sender, manager.getForPlayer(player.getUniqueId()));
            return true;
        }
        if (!sender.hasPermission("nekoplugin.grave.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender, List.copyOf(manager.getAll()));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            try {
                Grave grave = manager.getAll().stream().filter(candidate -> candidate.getId().equals(UUID.fromString(args[1]))).findFirst().orElse(null);
                if (grave == null) sender.sendMessage(Component.text("Grave not found.", NamedTextColor.RED));
                else {
                    manager.remove(grave, true);
                    sender.sendMessage(Component.text("Grave deleted and its contents dropped.", NamedTextColor.GREEN));
                }
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(Component.text("Invalid grave ID.", NamedTextColor.RED));
            }
            return true;
        }
        sender.sendMessage(Component.text("Usage: /graveadmin [list|delete <grave-id>]", NamedTextColor.YELLOW));
        return true;
    }

    private static void list(CommandSender sender, List<Grave> graves) {
        if (graves.isEmpty()) {
            sender.sendMessage(Component.text("No graves found.", NamedTextColor.GREEN));
            return;
        }
        for (Grave grave : graves) {
            GravePosition position = grave.getGravePosition();
            sender.sendMessage(Component.text(grave.getId() + " | " + grave.getOwnerName() + " | " + grave.getStackCount()
                + " stacks | " + position.worldName() + " (" + position.x() + ", " + position.y() + ", " + position.z() + ")",
                NamedTextColor.YELLOW));
        }
    }
}
