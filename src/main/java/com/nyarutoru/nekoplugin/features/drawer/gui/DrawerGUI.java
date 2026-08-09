package com.nyarutoru.nekoplugin.features.drawer.gui;

import com.nyarutoru.nekoplugin.api.gui.BaseGUI;
import com.nyarutoru.nekoplugin.features.drawer.data.Drawer;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import com.nyarutoru.nekoplugin.utils.LocationUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Inventory;

import org.bukkit.Bukkit;
import java.text.NumberFormat;
import java.util.*;
import java.util.Iterator;

/**
 * Custom GUI for drawer interaction.
 * Supports multi-player synchronization.
 */
public class DrawerGUI extends BaseGUI {

    public static final int SIZE = 27;
    public static final int ITEM_SLOTS = 18;

    public static final int SLOT_DEPOSIT_1 = 19;
    public static final int SLOT_DEPOSIT_32 = 20;
    public static final int SLOT_DEPOSIT_64 = 21;
    public static final int SLOT_INFO = 22;
    public static final int SLOT_WITHDRAW_64 = 23;
    public static final int SLOT_WITHDRAW_32 = 24;
    public static final int SLOT_WITHDRAW_1 = 25;

    private static final Map<String, Set<DrawerGUI>> openViewers = new HashMap<>();

    private final Drawer drawer;
    private final String drawerKey;
    private final Set<UUID> viewerIds = new HashSet<>();
    private final NumberFormat numberFormat = NumberFormat.getInstance();

    @SuppressWarnings("this-escape")
    public DrawerGUI(Drawer drawer) {
        super(SIZE, Component.text("Drawer - ").append(drawer.getTier().getDisplayNameComponent()));
        this.drawer = drawer;
        this.drawerKey = locationKey(drawer.getLocation());
        refresh();
    }

    private static String locationKey(Location loc) {
        return LocationUtils.getLocationKey(loc);
    }

    /**
     * Close all GUIs viewing this drawer (e.g., when block is broken)
     */
    public static void closeAllViewers(Drawer drawer) {
        if (drawer == null || drawer.getLocation() == null) {
            return;
        }

        closeViewers(openViewers.remove(locationKey(drawer.getLocation())));
    }

    public static void closeAllViewers() {
        for (Set<DrawerGUI> viewers : List.copyOf(openViewers.values())) {
            closeViewers(viewers);
        }
        openViewers.clear();
    }

    private static void closeViewers(Set<DrawerGUI> viewers) {
        if (viewers == null) return;
        for (DrawerGUI gui : Set.copyOf(viewers)) {
            gui.forceClose();
        }
        viewers.clear();
    }

    /**
     * Clean up all GUI references for a player who disconnected.
     * Prevents memory leaks when players disconnect without closing GUIs.
     */
    public static void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }
        
        // Find and remove all GUI instances this player has open
        Iterator<Map.Entry<String, Set<DrawerGUI>>> iterator = openViewers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<DrawerGUI>> entry = iterator.next();
            Set<DrawerGUI> viewers = entry.getValue();
            
            viewers.removeIf(gui -> {
                gui.viewerIds.remove(player.getUniqueId());
                return gui.viewerIds.isEmpty();
            });

            // Clean up empty sets
            if (viewers.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static void refreshAllViewers(Drawer drawer) {
        String key = locationKey(drawer.getLocation());
        Set<DrawerGUI> viewers = openViewers.get(key);
        if (viewers != null) {
            for (DrawerGUI gui : viewers)
                gui.refresh();
        }
        // Also update the barrel inventory for hopper compatibility
        updateBarrelInventory(drawer);
    }

    /**
     * Updates the barrel's inventory with a single representative item
     * so hoppers can detect and pull from it.
     * Includes comprehensive null safety and validation.
     */
    public static void updateBarrelInventory(Drawer drawer) {
        if (drawer == null) {
            return;
        }
        
        Location loc = drawer.getLocation();
        if (loc == null) {
            return;
        }
        
        // Validate world is loaded
        if (loc.getWorld() == null) {
            return;
        }
        
        // Validate block exists and is loaded
        if (!loc.getChunk().isLoaded()) {
            return;
        }
        
        org.bukkit.block.Block block = loc.getBlock();
        if (block == null || block.getType() != Material.BARREL) {
            // Block is not a barrel - drawer data may be stale
            // Don't update inventory, but don't crash either
            return;
        }

        org.bukkit.block.Barrel barrel = (org.bukkit.block.Barrel) block.getState();
        if (barrel == null) {
            return;
        }
        
        Inventory inv = barrel.getInventory();
        if (inv == null) {
            return;
        }
        
        inv.clear();

        if (!drawer.isEmpty()) {
            Material itemType = drawer.getItemType();
            if (itemType != null && itemType.isItem()) {
                // Keep a single item in the barrel so hoppers can detect it
                inv.setItem(0, new ItemStack(itemType, 1));
            }
        }
    }

    public Drawer getDrawer() {
        return drawer;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        viewerIds.add(player.getUniqueId());
        openViewers.computeIfAbsent(drawerKey, k -> new HashSet<>()).add(this);
    }

    @Override
    public void onClose(Player player) {
        super.onClose(player);
        viewerIds.remove(player.getUniqueId());
        if (!viewerIds.isEmpty()) return;
        Set<DrawerGUI> viewers = openViewers.get(drawerKey);
        if (viewers != null) {
            viewers.remove(this);
            if (viewers.isEmpty())
                openViewers.remove(drawerKey);
        }
    }

    /**
     * Force close this GUI for the current viewer
     */
    public void forceClose() {
        // Close for all viewers of this specific GUI instance
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
            }
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        ClickType clickType = event.getClick();

        if (event.isShiftClick() && event.getClickedInventory() != inventory) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                int slotIndex = event.getSlot();
                depositItemFromInventory(player, clickedItem, event.getClickedInventory(), slotIndex);
                event.setCancelled(true);
                return;
            }
        }

        if (slot >= 0 && slot < ITEM_SLOTS) {
            event.setCancelled(true);
            if (cursor != null && cursor.getType() != Material.AIR) {
                depositItem(player, cursor);
                return;
            }
            if (clickType == ClickType.LEFT && !drawer.isEmpty()) {
                withdrawToHand(player, drawer.getItemType().getMaxStackSize());
                return;
            }
            if (clickType == ClickType.RIGHT && !drawer.isEmpty()) {
                withdrawToHand(player, 1);
            }
            return;
        }

        if (slot >= ITEM_SLOTS && slot < SIZE && clickHandlers.containsKey(slot)) {
            event.setCancelled(true);
            clickHandlers.get(slot).accept(event);
        } else if (slot >= ITEM_SLOTS && slot < SIZE) {
            event.setCancelled(true);
        }
    }

    @Override
    public void refresh() {
        inventory.clear();
        clickHandlers.clear();

        refreshItemSlots();

        setItem(18, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        setItem(SLOT_DEPOSIT_1, createDepositButton(1), e -> deposit((Player) e.getWhoClicked(), 1));
        setItem(SLOT_DEPOSIT_32, createDepositButton(32), e -> deposit((Player) e.getWhoClicked(), 32));
        setItem(SLOT_DEPOSIT_64, createDepositButton(64), e -> deposit((Player) e.getWhoClicked(), 64));
        setItem(SLOT_INFO, createInfoItem());
        setItem(SLOT_WITHDRAW_64, createWithdrawButton(64), e -> withdraw((Player) e.getWhoClicked(), 64));
        setItem(SLOT_WITHDRAW_32, createWithdrawButton(32), e -> withdraw((Player) e.getWhoClicked(), 32));
        setItem(SLOT_WITHDRAW_1, createWithdrawButton(1), e -> withdraw((Player) e.getWhoClicked(), 1));
        setItem(26, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    private void refreshItemSlots() {
        Material itemType = drawer.getItemType();
        int itemCount = drawer.getItemCount();

        if (itemType == null || itemCount == 0) {
            for (int i = 0; i < ITEM_SLOTS; i++)
                inventory.setItem(i, null);
            return;
        }

        int maxStackSize = itemType.getMaxStackSize();
        int remaining = itemCount;

        for (int i = 0; i < ITEM_SLOTS; i++) {
            if (remaining > 0) {
                int stackSize = Math.min(remaining, maxStackSize);
                inventory.setItem(i, new ItemStack(itemType, stackSize));
                remaining -= stackSize;
            } else {
                inventory.setItem(i, null);
            }
        }
    }

    private void withdrawToHand(Player player, int amount) {
        if (!Drawer.areWithdrawalsEnabled() || drawer.isEmpty())
            return;

        Material itemType = drawer.getItemType();
        int toWithdraw = Math.min(Math.min(amount, drawer.getItemCount()), itemType.getMaxStackSize());
        if (toWithdraw <= 0)
            return;

        drawer.removeItems(toWithdraw);
        player.setItemOnCursor(new ItemStack(itemType, toWithdraw));

        DrawerManager.getInstance().markDirty();
        refreshAllViewers(drawer);
    }

    private void depositItem(Player player, ItemStack item) {
        if (!Drawer.isAllowedItem(item)) {
            return;
        }

        if (!drawer.canAcceptItem(item)) {
            return;
        }

        int overflow = drawer.addItems(item.getType(), item.getAmount());
        int deposited = item.getAmount() - overflow;

        if (deposited > 0) {
            item.setAmount(overflow);
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        }
    }

    /**
     * Deposit item from player inventory via shift-click.
     * Properly updates the item stack in the player's inventory.
     */
    private void depositItemFromInventory(Player player, ItemStack item, org.bukkit.inventory.Inventory sourceInventory,
            int slot) {
        if (!Drawer.isAllowedItem(item)) {
            return;
        }

        if (!drawer.canAcceptItem(item)) {
            return;
        }

        int overflow = drawer.addItems(item.getType(), item.getAmount());
        int deposited = item.getAmount() - overflow;

        if (deposited > 0) {
            // Update the item stack in source inventory
            if (overflow == 0) {
                sourceInventory.setItem(slot, null);
            } else {
                item.setAmount(overflow);
                sourceInventory.setItem(slot, item);
            }
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        }
    }

    private void deposit(Player player, int amount) {
        // If drawer already has items, scan inventory for matching items
        if (!drawer.isEmpty()) {
            depositFromInventory(player, amount);
            return;
        }

        // Otherwise, use main hand (original behavior for empty drawers)
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (mainHand.getType() == Material.AIR) {
            return;
        }

        if (!Drawer.isAllowedItem(mainHand)) {
            return;
        }

        if (!drawer.canAcceptItem(mainHand)) {
            return;
        }

        int toDeposit = Math.min(amount, mainHand.getAmount());
        int overflow = drawer.addItems(mainHand.getType(), toDeposit);
        int deposited = toDeposit - overflow;

        if (deposited > 0) {
            mainHand.setAmount(mainHand.getAmount() - deposited);
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        }
    }

    /**
     * Deposit items from player's entire inventory (when drawer already has items)
     */
    private void depositFromInventory(Player player, int amount) {
        Material targetType = drawer.getItemType();
        if (targetType == null)
            return;

        int remaining = amount;
        PlayerInventory inv = player.getInventory();

        // Scan entire inventory for matching items
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != targetType)
                continue;
            if (!Drawer.isAllowedItem(item))
                continue;

            int toTake = Math.min(remaining, item.getAmount());
            int overflow = drawer.addItems(targetType, toTake);
            int deposited = toTake - overflow;

            if (deposited > 0) {
                item.setAmount(item.getAmount() - deposited);
                remaining -= deposited;
            }

            if (overflow > 0) {
                // Drawer is full
                break;
            }
        }

        if (remaining < amount) {
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        }
    }

    private void withdraw(Player player, int amount) {
        if (!Drawer.areWithdrawalsEnabled() || drawer.isEmpty()) {
            return;
        }

        Material itemType = drawer.getItemType();
        int toWithdraw = Math.min(amount, drawer.getItemCount());
        int withdrawn = 0;

        while (withdrawn < toWithdraw) {
            int stackSize = Math.min(itemType.getMaxStackSize(), toWithdraw - withdrawn);
            ItemStack stack = new ItemStack(itemType, stackSize);

            var leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                withdrawn += stackSize - notAdded;
                break;
            }
            withdrawn += stackSize;
        }

        if (withdrawn > 0) {
            drawer.removeItems(withdrawn);
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        }
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Drawer Info")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            // Item info
            String itemName = drawer.getItemType() != null ? formatMaterial(drawer.getItemType()) : "Empty";
            lore.add(Component.text("Item: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(itemName).color(NamedTextColor.WHITE)));

            lore.add(Component.text("Count: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(numberFormat.format(drawer.getItemCount())).color(NamedTextColor.WHITE)));

            // Capacity
            String capacityText = drawer.getTier().getStackCapacity() < 0 ? "Unlimited"
                    : numberFormat.format(drawer.getMaxCapacity());
            lore.add(Component.text("Capacity: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(capacityText).color(NamedTextColor.WHITE)));

            // Fill percentage
            if (drawer.getTier().getStackCapacity() >= 0 && drawer.getMaxCapacity() > 0) {
                lore.add(Component.text("Fill: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(String.format("%.1f%%", drawer.getFillPercentage() * 100))
                                .color(NamedTextColor.WHITE)));
            }

            lore.add(Component.empty());

            // Tier info
            lore.add(Component.text("Tier: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(drawer.getTier().getDisplayNameComponent()));

            // Next tier upgrade
            DrawerTier nextTier = drawer.getTier().getNextTier();
            if (nextTier != null) {
                lore.add(Component.text("Upgrade: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(formatMaterial(nextTier.getUpgradeMaterial()))
                                .color(NamedTextColor.YELLOW)));
            }

            lore.add(Component.empty());

            // Instructions
            lore.add(Component.text("Left-click slot: ").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("withdraw stack").color(NamedTextColor.GRAY)));
            lore.add(Component.text("Right-click slot: ").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("withdraw 1").color(NamedTextColor.GRAY)));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createDepositButton(int amount) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("+" + amount)
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            // Change lore based on whether drawer has items
            String sourceText = drawer.isEmpty() ? "from main hand" : "from inventory";
            meta.lore(List.of(
                    Component.text("Deposit " + amount + " items").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(sourceText).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,
                            false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWithdrawButton(int amount) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("-" + amount)
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Withdraw " + amount + " items").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("to your inventory").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,
                            false)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
