package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.features.hammer.HammerRecipes.HammerTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to give an unbreakable hammer.
 * Usage: /givehammer <tier> [player]
 */
public class HammerCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("nekoplugin.givehammer")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /givehammer <tier> [player]")
                    .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Tiers: " + String.join(", ", HammerRecipes.TIERS.keySet()))
                    .color(NamedTextColor.GRAY));
            return true;
        }

        String tierName = args[0].toLowerCase();
        HammerTier tier = HammerRecipes.TIERS.get(tierName);

        if (tier == null) {
            sender.sendMessage(Component.text("Unknown tier: " + tierName)
                    .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Available tiers: " + String.join(", ", HammerRecipes.TIERS.keySet()))
                    .color(NamedTextColor.GRAY));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = sender.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1])
                        .color(NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(Component.text("Please specify a player when using from console.")
                    .color(NamedTextColor.RED));
            return true;
        }

        // Create unbreakable hammer
        ItemStack hammer = createUnbreakableHammer(tierName, tier);
        target.getInventory().addItem(hammer);

        sender.sendMessage(Component.text("Gave unbreakable " + tier.displayName() + " Hammer to " + target.getName())
                .color(NamedTextColor.GREEN));

        if (target != sender) {
            target.sendMessage(Component.text("You received an unbreakable " + tier.displayName() + " Hammer!")
                    .color(NamedTextColor.GREEN));
        }

        return true;
    }

    private ItemStack createUnbreakableHammer(String tierName, HammerTier tier) {
        ItemStack hammer = HammerRecipes.createHammer(tierName, tier);
        ItemMeta meta = hammer.getItemMeta();

        if (meta != null) {
            meta.setUnbreakable(true);

            // Update lore to show unbreakable
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("3×3 Mining Area")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Unbreakable")
                            .color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Cannot use Ore Excavation")
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)));

            hammer.setItemMeta(meta);
        }

        return hammer;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("nekoplugin.givehammer")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete tier names
            String partial = args[0].toLowerCase();
            for (String tier : HammerRecipes.TIERS.keySet()) {
                if (tier.startsWith(partial)) {
                    completions.add(tier);
                }
            }
        } else if (args.length == 2) {
            // Complete player names
            String partial = args[1].toLowerCase();
            for (Player player : sender.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
