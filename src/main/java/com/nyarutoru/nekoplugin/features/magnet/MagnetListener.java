package com.nyarutoru.nekoplugin.features.magnet;

import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

public class MagnetListener implements Listener {

    public static final String TOOL_NAME = "Magnet";
    public static final NamespacedKey MAGNET_KEY = new NamespacedKey("nekoplugin", "magnet");
    private static final Set<Material> MAGNET_TYPES = EnumSet.of(Material.COMPASS, Material.RECOVERY_COMPASS);

    private volatile int range = 10;
    // ponytail: single global tick (cap 40 items/tick) avoids per-item tasks; per-item tasks if this becomes bottleneck
    private volatile double pullSpeed = 0.85;
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, PullEntry> pulling = new java.util.concurrent.ConcurrentHashMap<>();
    private com.nyarutoru.nekoplugin.utils.SchedulerUtils.TaskHandle pullTask;

    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Integer> rangeDesc = SettingDescriptor.integer(
                "magnet-range", "Magnet pickup range (blocks)", 10, 1, 64,
                ApplySemantics.IMMEDIATE, this::setRange);
        registry.register("magnet", rangeDesc);
        applyStored(state, rangeDesc);
    }

    public void start() {
        if (pullTask != null && !pullTask.isCancelled()) return;
        pullTask = com.nyarutoru.nekoplugin.utils.SchedulerUtils.runGlobalTimerTask(this::tick, 1, 1);
    }

    public void stop() {
        com.nyarutoru.nekoplugin.utils.SchedulerUtils.cancelTask(pullTask);
        pullTask = null;
        pulling.clear();
    }

    private <T> void applyStored(AdminState state, SettingDescriptor<T> descriptor) {
        String stored = state.settingValue("magnet", descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    public int getRange() {
        return range;
    }

    public void setRange(int value) {
        range = value;
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        ActiveToolAPI.getInstance().onShift(player, TOOL_NAME, this::isHoldingMagnet, null);
    }

    private static final class PullEntry {
        final Item item;
        final Player player;
        PullEntry(Item item, Player player) { this.item = item; this.player = player; }
    }

    private void tick() {
        if (pulling.isEmpty()) return;
        int processed = 0;
        // cap per-tick work to avoid lag spikes when many drops (e.g. 500 logs)
        for (java.util.Iterator<java.util.Map.Entry<java.util.UUID, PullEntry>> it = pulling.entrySet().iterator(); it.hasNext(); ) {
            if (processed++ > 40) break;
            java.util.Map.Entry<java.util.UUID, PullEntry> e = it.next();
            PullEntry entry = e.getValue();
            Item item = entry.item;
            Player player = entry.player;
            try {
                if (item == null || !item.isValid() || item.isDead()) { it.remove(); continue; }
                if (player == null || !player.isOnline()) { it.remove(); continue; }
                if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME) || !isHoldingMagnet(player)) { it.remove(); continue; }
                Location iLoc = item.getLocation();
                Location pLoc = player.getLocation().add(0, 0.5, 0);
                if (iLoc.getWorld() == null || pLoc.getWorld() == null || !iLoc.getWorld().equals(pLoc.getWorld())) { it.remove(); continue; }
                double distSq = iLoc.distanceSquared(pLoc);
                if (distSq > (double) range * range + 4) { it.remove(); continue; }
                if (distSq < 2.25) { // <1.5 blocks -> collect
                    ItemStack stack = item.getItemStack();
                    if (stack == null || stack.getType().isAir()) { it.remove(); item.remove(); continue; }
                    ItemStack[] before = Arrays.stream(player.getInventory().getStorageContents())
                            .map(s -> s == null ? null : s.clone())
                            .toArray(ItemStack[]::new);
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
                    if (leftover.isEmpty()) {
                        item.remove();
                        it.remove();
                    } else {
                        player.getInventory().setStorageContents(before);
                        it.remove();
                    }
                    continue;
                }
                org.bukkit.util.Vector dir = pLoc.toVector().subtract(iLoc.toVector());
                double len = dir.length();
                if (len > 0) {
                    dir.normalize().multiply(pullSpeed);
                    // slight upward bias to lift ground items quickly
                    if (item.isOnGround()) dir.setY(dir.getY() + 0.15);
                    item.setVelocity(dir);
                    item.setPickupDelay(0);
                }
            } catch (Throwable ignored) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        // Except for player drops (thrower != null means player dropped via Q)
        if (item.getThrower() != null) return;

        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType().isAir()) return;

        Location loc = item.getLocation();
        Player best = null;
        double bestDistSq = Double.MAX_VALUE;
        double rangeSq = (double) range * range;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME)) continue;
            if (!isHoldingMagnet(player)) continue;
            if (!player.getWorld().equals(loc.getWorld())) continue;
            double distSq = player.getLocation().distanceSquared(loc);
            if (distSq > rangeSq) continue;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = player;
            }
        }

        if (best == null) return;

        // Fast path: if very close (<1.5) collect instantly without pull task (less lag)
        if (bestDistSq < 2.25) {
            ItemStack[] before = Arrays.stream(best.getInventory().getStorageContents())
                    .map(s -> s == null ? null : s.clone())
                    .toArray(ItemStack[]::new);
            HashMap<Integer, ItemStack> leftover = best.getInventory().addItem(stack.clone());
            if (leftover.isEmpty()) {
                event.setCancelled(true);
                item.remove();
            } else {
                best.getInventory().setStorageContents(before);
            }
            return;
        }

        // Otherwise track for quick pull (single global tick, not per-item task) -> low lag
        pulling.put(item.getUniqueId(), new PullEntry(item, best));
        // let item spawn; our tick will pull it quickly
        item.setPickupDelay(10);
    }

    public boolean isHoldingMagnet(Player player) {
        return isMagnetItem(player.getInventory().getItemInMainHand())
                || isMagnetItem(player.getInventory().getItemInOffHand());
    }

    public static boolean isMagnetItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Byte b = meta.getPersistentDataContainer().get(MAGNET_KEY, PersistentDataType.BYTE);
            if (b != null && b == 1) return true;
            NamespacedKey model = meta.getItemModel();
            if (model != null && "nekoplugin".equals(model.getNamespace()) && "magnet".equals(model.getKey())) return true;
        }
        return MAGNET_TYPES.contains(item.getType());
    }

    public static ItemStack createMagnetItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Magnet")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(Arrays.asList(
                    Component.text("Hold in main hand or offhand")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift 10 times to activate")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Sucks nearby drops into inventory")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Except player drops")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(MAGNET_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.setItemModel(new NamespacedKey("nekoplugin", "magnet"));
            item.setItemMeta(meta);
        }
        return item;
    }
}
