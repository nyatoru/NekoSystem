package com.nyarutoru.nekoplugin.features.mending;

import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.PlayerExpUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Right-clicking a damaged item with Mending repairs it, costing the
 * player's current experience points (level + progress), not lifetime total.
 */
public final class MendingRepairListener implements Listener {

    private volatile int repairRate = 2;

    public void setRepairRate(int rate) {
        if (rate < 1 || rate > 64) throw new IllegalArgumentException("Repair rate must be between 1 and 64");
        repairRate = rate;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && event.useInteractedBlock() == Event.Result.ALLOW) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isRepairable(item)) {
            item = player.getInventory().getItemInOffHand();
            if (!isRepairable(item)) return;
        }

        int availableXp = PlayerExpUtils.getCurrentExp(player);
        int damage = ItemUtils.getDurability(item);
        RepairCost cost = RepairCost.compute(damage, availableXp, repairRate);
        if (cost.xpCost() <= 0) return;

        // Right-click on armor would otherwise trigger vanilla equip/swap logic.
        event.setUseItemInHand(Event.Result.DENY);

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(Math.max(0, damage - cost.repairDamage()));
            item.setItemMeta(meta);
        }
        PlayerExpUtils.setCurrentExp(player, availableXp - cost.xpCost());
        player.sendActionBar(Component.text("+" + cost.repairDamage() + " durability ")
                .color(NamedTextColor.GREEN)
                .append(Component.text("(" + cost.xpCost() + " XP)")
                        .color(NamedTextColor.GRAY)));
    }

    private static boolean isRepairable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.containsEnchantment(Enchantment.MENDING)) return false;
        int damage = ItemUtils.getDurability(item);
        return damage > 0;
    }
}
