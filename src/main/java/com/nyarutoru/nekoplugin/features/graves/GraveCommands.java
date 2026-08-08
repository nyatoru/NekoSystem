package com.nyarutoru.nekoplugin.features.graves;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class GraveCommands {
    private final GraveManager manager;

    GraveCommands(GraveManager manager) {
        this.manager = manager;
    }

    BasicCommand playerCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                handlePlayerCommand(source.getSender());
            }

            @Override
            public String permission() {
                return "nekoplugin.grave.use";
            }
        };
    }

    BasicCommand adminCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                handleAdminCommand(source.getSender(), args);
            }

            @Override
            public String permission() {
                return "nekoplugin.grave.admin";
            }
        };
    }

    private void handlePlayerCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        list(sender, manager.getForPlayer(player.getUniqueId()));
    }

    private void handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender, List.copyOf(manager.getAll()));
            return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            try {
                UUID graveId = UUID.fromString(args[1]);
                Grave grave = manager.getAll().stream()
                    .filter(candidate -> candidate.getId().equals(graveId))
                    .findFirst()
                    .orElse(null);
                if (grave == null) {
                    sender.sendMessage(Component.text("Grave not found.", NamedTextColor.RED));
                } else {
                    manager.remove(grave, true);
                    sender.sendMessage(Component.text("Grave deleted and its contents dropped.", NamedTextColor.GREEN));
                }
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(Component.text("Invalid grave ID.", NamedTextColor.RED));
            }
            return;
        }
        sender.sendMessage(Component.text("Usage: /graveadmin [list|delete <grave-id>]", NamedTextColor.YELLOW));
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
