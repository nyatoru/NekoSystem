package com.nyarutoru.nekoplugin.features.magnet;

import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
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
    // Custom model data: pack override on vanilla compass shows the magnet; vanilla compass fallback without the pack
    public static final int MAGNET_CMD = 2001;
    private static final Set<Material> MAGNET_TYPES = EnumSet.of(Material.COMPASS, Material.RECOVERY_COMPASS);

    private volatile int range = 10;
    // ponytail: single global tick (cap 40 items/tick) avoids per-item tasks; per-item tasks if this becomes bottleneck
    private volatile double pullSpeed = 0.85;
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, PullEntry> pulling = new java.util.concurrent.ConcurrentHashMap<>();
    private SchedulerUtils.TaskHandle pullTask;

    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Integer> rangeDesc = SettingDescriptor.integer(
                "magnet-range", "Magnet pickup range (blocks)", 10, 1, 64,
                ApplySemantics.IMMEDIATE, this::setRange);
        registry.register("magnet", rangeDesc);
        applyStored(state, rangeDesc);
    }

    public void start() {
        if (pullTask != null && !pullTask.isCancelled()) return;
        pullTask = SchedulerUtils.runGlobalTimerTask(this::tick, 1, 1);
    }

    public void stop() {
        SchedulerUtils.cancelTask(pullTask);
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
        ActiveToolAPI.getInstance().onShift(player, TOOL_NAME, this::isHoldingMagnet, () -> {
            // Fix 2: when magnet activates, immediately consider ground items that were lying before activation
            // (previously only ItemSpawnEvent after activation was handled)
            scanGroundItemsFor(player);
        });
    }

    private void scanGroundItemsFor(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME)) return;
        if (!isHoldingMagnet(player)) return;
        Location pLoc = player.getLocation().add(0, 0.5, 0);
        if (pLoc.getWorld() == null) return;
        try {
            // On Folia this runs on player's entity thread (via onShift -> activate), so nearby lookup is safe
            for (org.bukkit.entity.Entity e : pLoc.getWorld().getNearbyEntities(pLoc, range, range, range, en -> en instanceof Item)) {
                Item item = (Item) e;
                if (item.getThrower() != null) continue;
                if (pulling.containsKey(item.getUniqueId())) continue;
                try {
                    if (item.getLocation().distanceSquared(pLoc) > (double) range * range) continue;
                } catch (Throwable ignored) {
                    continue;
                }
                if (item.getItemStack() == null || item.getItemStack().getType().isAir()) continue;
                pulling.put(item.getUniqueId(), new PullEntry(item, player));
                try {
                    if (item.getPickupDelay() > 10) {
                        // Folia: item access must be on item's thread, try direct then fallback
                        try {
                            item.setPickupDelay(0);
                        } catch (Throwable ex) {
                            SchedulerUtils.runAtEntity(item, () -> {
                                try { item.setPickupDelay(0); } catch (Throwable ignored) {}
                            });
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static final class PullEntry {
        final Item item;
        final Player player;
        PullEntry(Item item, Player player) { this.item = item; this.player = player; }
    }

    private int groundScanTick = 0;

    private void tick() {
        if (SchedulerUtils.isFolia()) {
            tickFolia();
        } else {
            tickPaper();
        }
    }

    // Paper path: efficient single global tick that directly accesses world/entities (safe off Folia)
    private void tickPaper() {
        // Fix 2: periodic ground scan so items lying before activation are also pulled (previously only ItemSpawnEvent)
        // Run every 10 ticks (0.5s) to avoid per-tick world scan lag
        if (++groundScanTick % 10 == 0) {
            // also auto-deactivate magnet if player no longer holds it in either hand (covers offhand swap/removal)
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (ActiveToolAPI.getInstance().isActive(p, TOOL_NAME) && !isHoldingMagnet(p)) {
                    ActiveToolAPI.getInstance().deactivate(p, "no magnet");
                }
            }
            // scan ground items for each active holder (cap players per scan to avoid lag)
            int scannedPlayers = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (scannedPlayers++ > 6) break; // ponytail: cap scanned players per ground sweep
                if (!ActiveToolAPI.getInstance().isActive(p, TOOL_NAME) || !isHoldingMagnet(p)) continue;
                Location pLoc = p.getLocation().add(0, 0.5, 0);
                if (pLoc.getWorld() == null) continue;
                try {
                    // getNearbyEntities already bounded by range, usually < 30 entities
                    for (org.bukkit.entity.Entity e : pLoc.getWorld().getNearbyEntities(pLoc, range, range, range, en -> en instanceof Item)) {
                        if (pulling.size() > 80) break; // cap total tracked to avoid explosion
                        Item item = (Item) e;
                        if (item.getThrower() != null) continue;
                        if (pulling.containsKey(item.getUniqueId())) continue;
                        ItemStack st = item.getItemStack();
                        if (st == null || st.getType().isAir()) continue;
                        if (item.getLocation().distanceSquared(pLoc) > (double) range * range) continue;
                        pulling.put(item.getUniqueId(), new PullEntry(item, p));
                        if (item.getPickupDelay() > 10) item.setPickupDelay(0);
                    }
                } catch (Throwable ignored) {}
            }
        }

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

    // Folia path: global tick only schedules per-player/per-item tasks on correct threads
    private void tickFolia() {
        if (++groundScanTick % 10 == 0) {
            // Deactivate and ground scan must run on each player's entity thread
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Schedule on player's thread to safely check inventory and location
                SchedulerUtils.runAtPlayer(p, () -> handleFoliaGroundScanForPlayer(p));
            }
        }

        if (pulling.isEmpty()) return;
        int processed = 0;
        for (java.util.Map.Entry<java.util.UUID, PullEntry> e : pulling.entrySet()) {
            if (processed++ > 40) break;
            java.util.UUID itemId = e.getKey();
            PullEntry entry = e.getValue();
            Player player = entry.player;
            if (player == null || !player.isOnline()) {
                pulling.remove(itemId);
                continue;
            }
            // Schedule pull handling on player's entity thread (player and nearby item likely same region)
            SchedulerUtils.runAtPlayer(player, () -> handleFoliaPull(itemId, entry));
        }
    }

    private void handleFoliaGroundScanForPlayer(Player p) {
        try {
            if (!p.isOnline()) return;
            if (ActiveToolAPI.getInstance().isActive(p, TOOL_NAME) && !isHoldingMagnet(p)) {
                ActiveToolAPI.getInstance().deactivate(p, "no magnet");
                return;
            }
            if (!ActiveToolAPI.getInstance().isActive(p, TOOL_NAME) || !isHoldingMagnet(p)) return;
            Location pLoc = p.getLocation().add(0, 0.5, 0);
            if (pLoc.getWorld() == null) return;
            int scanned = 0;
            try {
                for (org.bukkit.entity.Entity e : pLoc.getWorld().getNearbyEntities(pLoc, range, range, range, en -> en instanceof Item)) {
                    if (pulling.size() > 80) break;
                    if (scanned++ > 40) break; // cap per player per scan
                    Item item = (Item) e;
                    if (item.getThrower() != null) continue;
                    if (pulling.containsKey(item.getUniqueId())) continue;
                    ItemStack st;
                    try { st = item.getItemStack(); } catch (Throwable ignored) { continue; }
                    if (st == null || st.getType().isAir()) continue;
                    try {
                        if (item.getLocation().distanceSquared(pLoc) > (double) range * range) continue;
                    } catch (Throwable ignored) {
                        continue;
                    }
                    pulling.put(item.getUniqueId(), new PullEntry(item, p));
                    // Pickup delay must be set on item's thread; try direct then schedule
                    try {
                        if (item.getPickupDelay() > 10) item.setPickupDelay(0);
                    } catch (Throwable ex) {
                        SchedulerUtils.runAtEntity(item, () -> {
                            try { if (item.getPickupDelay() > 10) item.setPickupDelay(0); } catch (Throwable ignored) {}
                        });
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void handleFoliaPull(java.util.UUID itemId, PullEntry entry) {
        Item item = entry.item;
        Player player = entry.player;
        try {
            if (item == null) { pulling.remove(itemId); return; }
            try {
                if (!item.isValid() || item.isDead()) { pulling.remove(itemId); return; }
            } catch (Throwable t) {
                // isValid may require item's thread; if we fail, try via item scheduler
                SchedulerUtils.runAtEntity(item, () -> {
                    try {
                        if (!item.isValid() || item.isDead()) pulling.remove(itemId);
                    } catch (Throwable ignored) { pulling.remove(itemId); }
                });
                return;
            }
            if (player == null || !player.isOnline()) { pulling.remove(itemId); return; }
            if (!ActiveToolAPI.getInstance().isActive(player, TOOL_NAME) || !isHoldingMagnet(player)) { pulling.remove(itemId); return; }
            Location iLoc;
            Location pLoc;
            try {
                iLoc = item.getLocation();
                pLoc = player.getLocation().add(0, 0.5, 0);
            } catch (Throwable t) {
                // Cross-region access failure, reschedule on item thread
                SchedulerUtils.runAtEntity(item, () -> handleFoliaPull(itemId, entry));
                return;
            }
            if (iLoc.getWorld() == null || pLoc.getWorld() == null || !iLoc.getWorld().equals(pLoc.getWorld())) { pulling.remove(itemId); return; }
            double distSq;
            try { distSq = iLoc.distanceSquared(pLoc); } catch (Throwable t) { pulling.remove(itemId); return; }
            if (distSq > (double) range * range + 4) { pulling.remove(itemId); return; }
            if (distSq < 2.25) {
                ItemStack stack;
                try { stack = item.getItemStack(); } catch (Throwable t) { pulling.remove(itemId); return; }
                if (stack == null || stack.getType().isAir()) {
                    pulling.remove(itemId);
                    SchedulerUtils.runAtEntity(item, () -> { try { item.remove(); } catch (Throwable ignored) {} });
                    return;
                }
                ItemStack[] before = Arrays.stream(player.getInventory().getStorageContents())
                        .map(s -> s == null ? null : s.clone())
                        .toArray(ItemStack[]::new);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
                if (leftover.isEmpty()) {
                    SchedulerUtils.runAtEntity(item, () -> { try { item.remove(); } catch (Throwable ignored) {} });
                    pulling.remove(itemId);
                } else {
                    player.getInventory().setStorageContents(before);
                    pulling.remove(itemId);
                }
                return;
            }
            org.bukkit.util.Vector dir = pLoc.toVector().subtract(iLoc.toVector());
            double len = dir.length();
            if (len > 0) {
                dir.normalize().multiply(pullSpeed);
                try {
                    if (item.isOnGround()) dir.setY(dir.getY() + 0.15);
                } catch (Throwable ignored) {}
                try {
                    item.setVelocity(dir);
                    item.setPickupDelay(0);
                } catch (Throwable ex) {
                    SchedulerUtils.runAtEntity(item, () -> {
                        try { item.setVelocity(dir); item.setPickupDelay(0); } catch (Throwable ignored) {}
                    });
                }
            }
        } catch (Throwable ignored) {
            pulling.remove(itemId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Folia: ItemSpawn runs on item's region thread; iterating all players and accessing their locations from here
        // would be cross-region and unsafe. Rely on periodic ground scan instead (0.5s).
        if (SchedulerUtils.isFolia()) {
            // Still handle immediate very-close pickup via scheduling to avoid lag, but defer correctly
            // For Folia we skip immediate handling; tickFolia will pick it up within 0.5s
            return;
        }
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
            Integer cmd = ItemUtils.getCustomModelData(meta);
            if (cmd != null && cmd == MAGNET_CMD) return true;
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
            ItemUtils.setCustomModelData(meta, MAGNET_CMD);
            item.setItemMeta(meta);
        }
        return item;
    }
}
