package com.nyarutoru.nekoplugin.features.drawer.gui;

import com.nyarutoru.nekoplugin.api.gui.BaseGUI;
import com.nyarutoru.nekoplugin.features.drawer.data.Drawer;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerManager;
import com.nyarutoru.nekoplugin.features.drawer.data.DrawerTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.*;

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
    private final NumberFormat numberFormat = NumberFormat.getInstance();

    public DrawerGUI(Drawer drawer) {
        super(SIZE, Component.text("Drawer - ").append(Component.text(drawer.getTier().getDisplayName())));
        this.drawer = drawer;
        this.drawerKey = locationKey(drawer.getLocation());
        refresh();
    }

    private static String locationKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public Drawer getDrawer() {
        return drawer;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        openViewers.computeIfAbsent(drawerKey, k -> new HashSet<>()).add(this);
    }

    @Override
    public void onClose(Player player) {
        super.onClose(player);
        Set<DrawerGUI> viewers = openViewers.get(drawerKey);
        if (viewers != null) {
            viewers.remove(this);
            if (viewers.isEmpty())
                openViewers.remove(drawerKey);
        }
    }

    public static void refreshAllViewers(Drawer drawer) {
        String key = locationKey(drawer.getLocation());
        Set<DrawerGUI> viewers = openViewers.get(key);
        if (viewers != null) {
            for (DrawerGUI gui : viewers)
                gui.refresh();
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
        if (drawer.isEmpty())
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
            player.sendMessage(Component.text("This item cannot be stored in a drawer!").color(NamedTextColor.RED));
            return;
        }

        if (!drawer.canAcceptItem(item)) {
            if (!drawer.isEmpty()) {
                player.sendMessage(Component.text("Drawer only accepts: ").color(NamedTextColor.RED)
                        .append(Component.text(formatMaterial(drawer.getItemType())).color(NamedTextColor.YELLOW)));
            }
            return;
        }

        int overflow = drawer.addItems(item.getType(), item.getAmount());
        int deposited = item.getAmount() - overflow;

        if (deposited > 0) {
            item.setAmount(overflow);
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        } else {
            player.sendMessage(Component.text("Drawer is full!").color(NamedTextColor.RED));
        }
    }

    /**
     * Deposit item from player inventory via shift-click.
     * Properly updates the item stack in the player's inventory.
     */
    private void depositItemFromInventory(Player player, ItemStack item, org.bukkit.inventory.Inventory sourceInventory,
            int slot) {
        if (!Drawer.isAllowedItem(item)) {
            player.sendMessage(Component.text("This item cannot be stored in a drawer!").color(NamedTextColor.RED));
            return;
        }

        if (!drawer.canAcceptItem(item)) {
            if (!drawer.isEmpty()) {
                player.sendMessage(Component.text("Drawer only accepts: ").color(NamedTextColor.RED)
                        .append(Component.text(formatMaterial(drawer.getItemType())).color(NamedTextColor.YELLOW)));
            }
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
        } else {
            player.sendMessage(Component.text("Drawer is full!").color(NamedTextColor.RED));
        }
    }

    private void deposit(Player player, int amount) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (mainHand.getType() == Material.AIR) {
            player.sendMessage(Component.text("Hold an item in your main hand to deposit!").color(NamedTextColor.RED));
            return;
        }

        if (!Drawer.isAllowedItem(mainHand)) {
            player.sendMessage(Component.text("This item cannot be stored in a drawer!").color(NamedTextColor.RED));
            return;
        }

        if (!drawer.canAcceptItem(mainHand)) {
            if (!drawer.isEmpty()) {
                player.sendMessage(Component.text("Drawer only accepts: ").color(NamedTextColor.RED)
                        .append(Component.text(formatMaterial(drawer.getItemType())).color(NamedTextColor.YELLOW)));
            }
            return;
        }

        int toDeposit = Math.min(amount, mainHand.getAmount());
        int overflow = drawer.addItems(mainHand.getType(), toDeposit);
        int deposited = toDeposit - overflow;

        if (deposited > 0) {
            mainHand.setAmount(mainHand.getAmount() - deposited);
            player.sendMessage(Component.text("Deposited " + deposited + " items.").color(NamedTextColor.GREEN));
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        } else {
            player.sendMessage(Component.text("Drawer is full!").color(NamedTextColor.RED));
        }
    }

    private void withdraw(Player player, int amount) {
        if (drawer.isEmpty()) {
            player.sendMessage(Component.text("Drawer is empty!").color(NamedTextColor.RED));
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
            player.sendMessage(Component.text("Withdrew " + withdrawn + " items.").color(NamedTextColor.GREEN));
            DrawerManager.getInstance().markDirty();
            refreshAllViewers(drawer);
        } else {
            player.sendMessage(Component.text("Your inventory is full!").color(NamedTextColor.RED));
        }
    }

    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§7Item: §f" + (drawer.getItemType() != null ? formatMaterial(drawer.getItemType()) : "Empty"));
        lore.add("§7Count: §f" + numberFormat.format(drawer.getItemCount()));

        String capacityText = drawer.getTier().getStackCapacity() < 0 ? "Unlimited"
                : numberFormat.format(drawer.getMaxCapacity());
        lore.add("§7Capacity: §f" + capacityText);

        if (drawer.getTier().getStackCapacity() >= 0 && drawer.getMaxCapacity() > 0) {
            lore.add("§7Fill: §f" + String.format("%.1f%%", drawer.getFillPercentage() * 100));
        }

        lore.add("§7");
        lore.add("§7Tier: §f" + drawer.getTier().getDisplayName());

        DrawerTier nextTier = drawer.getTier().getNextTier();
        if (nextTier != null) {
            lore.add("§7Upgrade: §e" + formatMaterial(nextTier.getUpgradeMaterial()));
        }

        lore.add("§7");
        lore.add("§eLeft-click slot: §7withdraw stack");
        lore.add("§eRight-click slot: §7withdraw 1");

        return createItem(Material.BOOK, "§b§lDrawer Info", lore);
    }

    private ItemStack createDepositButton(int amount) {
        return createItem(Material.LIME_STAINED_GLASS_PANE, "§a§l+" + amount,
                List.of("§7Deposit " + amount + " items", "§7from main hand"));
    }

    private ItemStack createWithdrawButton(int amount) {
        return createItem(Material.RED_STAINED_GLASS_PANE, "§c§l-" + amount,
                List.of("§7Withdraw " + amount + " items", "§7to your inventory"));
    }
}
