package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Custom Crafting Table with Double Chest GUI.
 * Replaces vanilla crafting table GUI to support custom recipes.
 * Slot 8 = craftable book (vanilla+custom, like vanilla book), Slot 18 = custom recipe book (original).
 */
public class CustomCraftingListener implements Listener {

    private static final int[] CRAFTING_SLOTS = { 10, 11, 12, 19, 20, 21, 28, 29, 30 };
    private static final int RESULT_SLOT = 24;
    private static final int CRAFT_BUTTON_SLOT = 23;

    // GUI Layout (54 slots - double chest)
    private static final int CLOSE_SLOT = 49;
    private static final int RECIPE_BOOK_SLOT = 18; // original custom book (kept)
    private static final int CRAFTABLE_BOOK_SLOT = 8; // new: shows craftable vanilla+custom like vanilla book
    private static final Component TITLE = Component.text("✦ Crafting Table ✦")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true);
    private final NekoPlugin plugin;
    private volatile boolean running = true;
    private final Set<UUID> openCraftingGUIs = new HashSet<>();
    private final RecipeBookGUI recipeBookGUI;

    public CustomCraftingListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.recipeBookGUI = new RecipeBookGUI();
        RecipePreviewGUI recipePreviewGUI = new RecipePreviewGUI(plugin, recipeBookGUI);
        this.recipeBookGUI.setRecipePreviewGUI(recipePreviewGUI);
    }

    public RecipeBookGUI getRecipeBookGUI() {
        return recipeBookGUI;
    }

    public RecipePreviewGUI getRecipePreviewGUI() {
        return recipeBookGUI.getRecipePreviewGUI();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftingTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;
        if (event.getClickedBlock().getType() != Material.CRAFTING_TABLE)
            return;
        event.setCancelled(true);
        openCraftingGUI(event.getPlayer());
    }

    public void cleanup() {
        running = false;
        for (UUID playerId : Set.copyOf(openCraftingGUIs)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.closeInventory();
        }
        openCraftingGUIs.clear();
    }

    private void openCraftingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE);
        ItemStack blackGlass = createBlackGlassPane();

        // Top row - black glass except slot 8 (new craftable book)
        for (int i = 0; i < 9; i++) {
            if (i == CRAFTABLE_BOOK_SLOT) continue;
            gui.setItem(i, blackGlass);
        }
        gui.setItem(CRAFTABLE_BOOK_SLOT, createCraftableBookButton());

        // Bottom row - static black glass, close button at 49 (no craftable indicator here - Recipe Book only)
        for (int i = 45; i < 54; i++) {
            if (i == CLOSE_SLOT) continue;
            gui.setItem(i, blackGlass);
        }

        // Side decorations - black glass
        gui.setItem(9, blackGlass);
        gui.setItem(17, blackGlass);
        gui.setItem(26, blackGlass);
        gui.setItem(27, blackGlass);
        gui.setItem(35, blackGlass);

        // Original recipe book kept at slot 18
        gui.setItem(RECIPE_BOOK_SLOT, createRecipeBookButton());

        // Row 36-44: black glass pane (unused row)
        for (int i = 36; i <= 44; i++) gui.setItem(i, blackGlass);

        // Additional unused slots - black glass pane
        int[] unusedSlots = { 13, 14, 15, 16, 22, 25, 31, 32, 33, 34 };
        for (int slot : unusedSlots) gui.setItem(slot, blackGlass);

        gui.setItem(CRAFT_BUTTON_SLOT, createArrowItem());
        gui.setItem(CLOSE_SLOT, createCloseButton());
        gui.setItem(RESULT_SLOT, createResultPlaceholder());

        player.openInventory(gui);
        openCraftingGUIs.add(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openCraftingGUIs.contains(player.getUniqueId())) return;
        // only handle our custom top inventory - Recipe Book has its own listener
        if (!event.getView().title().equals(TITLE)) return;
        Inventory inv = event.getView().getTopInventory();
        int slot = event.getRawSlot();
        // ignore bottom inventory clicks except shift-craft update
        if (event.getClickedInventory() != inv) {
            if (slot >= 54) {
                if (event.isShiftClick() && running) {
                    com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> {
                        if (running && openCraftingGUIs.contains(player.getUniqueId())) updateCraftingResult(inv);
                    });
                }
            }
            return;
        }
        if (slot >= 54) {
            if (event.isShiftClick() && running) {
                com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> {
                    if (running && openCraftingGUIs.contains(player.getUniqueId())) updateCraftingResult(inv);
                });
            }
            return;
        }

        boolean isCraftingSlot = false;
        for (int craftSlot : CRAFTING_SLOTS) if (slot == craftSlot) { isCraftingSlot = true; break; }
        if (isCraftingSlot) {
            if (running) com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> {
                if (running && openCraftingGUIs.contains(player.getUniqueId())) updateCraftingResult(inv);
            });
            return;
        }

        if (slot == RESULT_SLOT) {
            ItemStack result = inv.getItem(RESULT_SLOT);
            if (result != null && result.getType() != Material.AIR && result.getType() != Material.BARRIER) {
                if (event.isShiftClick()) craftAll(player, inv, result);
                else {
                    ItemStack cursor = player.getItemOnCursor();
                    ItemStack resultClone = result.clone();
                    if (cursor == null || cursor.getType() == Material.AIR) {
                        player.setItemOnCursor(resultClone);
                        inv.setItem(RESULT_SLOT, createResultPlaceholder());
                        consumeCraftingMaterials(inv);
                        scheduleUpdate(player, inv);
                    } else if (cursor.isSimilar(resultClone)) {
                        int max = cursor.getMaxStackSize();
                        int total = cursor.getAmount() + resultClone.getAmount();
                        if (total <= max) {
                            cursor.setAmount(total);
                            player.setItemOnCursor(cursor);
                            inv.setItem(RESULT_SLOT, createResultPlaceholder());
                            consumeCraftingMaterials(inv);
                            scheduleUpdate(player, inv);
                        } else {
                            int space = max - cursor.getAmount();
                            if (space > 0) {
                                ItemStack toInventory = resultClone.clone();
                                toInventory.setAmount(resultClone.getAmount() - space);
                                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toInventory);
                                if (leftover.isEmpty()) {
                                    cursor.setAmount(max);
                                    player.setItemOnCursor(cursor);
                                    inv.setItem(RESULT_SLOT, createResultPlaceholder());
                                    consumeCraftingMaterials(inv);
                                    scheduleUpdate(player, inv);
                                } else {
                                    for (ItemStack l : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), l);
                                    cursor.setAmount(max);
                                    player.setItemOnCursor(cursor);
                                    inv.setItem(RESULT_SLOT, createResultPlaceholder());
                                    consumeCraftingMaterials(inv);
                                    scheduleUpdate(player, inv);
                                }
                            } else {
                                giveOrDrop(player, resultClone);
                                inv.setItem(RESULT_SLOT, createResultPlaceholder());
                                consumeCraftingMaterials(inv);
                                scheduleUpdate(player, inv);
                            }
                        }
                    } else {
                        giveOrDrop(player, resultClone);
                        inv.setItem(RESULT_SLOT, createResultPlaceholder());
                        consumeCraftingMaterials(inv);
                        scheduleUpdate(player, inv);
                    }
                }
            }
            event.setCancelled(true);
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            event.setCancelled(true);
            return;
        }

        // Only these two slots may open Recipe Book - all other glass is inert (no recipe show)
        if (slot == CRAFTABLE_BOOK_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            if (running) com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> recipeBookGUI.openRecipeBook(player, true, true));
            return;
        }
        if (slot == RECIPE_BOOK_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            if (running) com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> recipeBookGUI.openRecipeBook(player, false, false));
            return;
        }

        // all other top-inventory decoration (glass panes, arrow) is inert - never goes to recipe show
        event.setCancelled(true);
    }

    private void craftAll(Player player, Inventory inv, ItemStack result) {
        int maxCrafts = getMaxCraftCount(inv);
        inv.setItem(RESULT_SLOT, createResultPlaceholder());
        for (int i = 0; i < maxCrafts; i++) {
            if (!canAddToInventory(player, result)) break;
            player.getInventory().addItem(result.clone());
            consumeCraftingMaterials(inv);
            ItemStack newResult = findMatchingRecipe(inv);
            if (newResult == null || newResult.getType() == Material.AIR) break;
        }
        updateCraftingResult(inv);
    }

    private int getMaxCraftCount(Inventory inv) {
        int min = Integer.MAX_VALUE;
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) min = Math.min(min, item.getAmount());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private boolean canAddToInventory(Player player, ItemStack item) {
        return player.getInventory().firstEmpty() != -1 || player.getInventory().contains(item.getType());
    }

    private ItemStack findMatchingRecipe(Inventory inv) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack item = inv.getItem(CRAFTING_SLOTS[i]);
            matrix[i] = item != null ? item.clone() : null;
        }
        ItemStack result = com.nyarutoru.nekoplugin.api.recipe.RecipeAPI.getInstance().findMatchingRecipe(matrix);
        if (result == null) result = Bukkit.craftItem(matrix, Bukkit.getWorlds().get(0));
        return result;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!openCraftingGUIs.remove(player.getUniqueId())) return;
        Inventory inv = event.getInventory();
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                for (ItemStack leftover : remaining.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void updateCraftingResult(Inventory inv) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack item = inv.getItem(CRAFTING_SLOTS[i]);
            matrix[i] = item != null ? item.clone() : null;
        }
        ItemStack result = com.nyarutoru.nekoplugin.api.recipe.RecipeAPI.getInstance().findMatchingRecipe(matrix);
        if (result == null) result = Bukkit.craftItem(matrix, Bukkit.getWorlds().get(0));
        boolean hasResult = result != null && result.getType() != Material.AIR;
        if (hasResult) inv.setItem(RESULT_SLOT, result);
        else inv.setItem(RESULT_SLOT, createResultPlaceholder());
        // no bottom indicator update - Recipe Book only handles craftable display
    }

    private ItemStack createResultPlaceholder() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("No Result").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            barrier.setItemMeta(meta);
        }
        return barrier;
    }

    private void consumeCraftingMaterials(Inventory inv) {
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else inv.setItem(slot, null);
            }
        }
    }

    private void scheduleUpdate(Player player, Inventory inv) {
        if (!running) return;
        com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> { if (running && openCraftingGUIs.contains(player.getUniqueId())) updateCraftingResult(inv); });
    }

    private void giveOrDrop(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack l : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), l);
    }

    private ItemStack createBlackGlassPane() { return com.nyarutoru.nekoplugin.api.gui.GUIUtils.createBlackGlass(); }
    private ItemStack createRedGlassPane() { return com.nyarutoru.nekoplugin.api.gui.GUIUtils.createRedGlass(); }
    private ItemStack createLimeGlassPane() { return com.nyarutoru.nekoplugin.api.gui.GUIUtils.createLimeGlass(); }

    private ItemStack createRecipeBookButton() {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📖 Recipe Book").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(Component.empty(), Component.text("Click to browse custom recipes").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            book.setItemMeta(meta);
        }
        return book;
    }

    private ItemStack createCraftableBookButton() {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("📖 Recipe Book §a(Show craftable)").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            // Use MiniMessage would need but keep simple Component
            meta.displayName(Component.text("📖 Craftable Recipes").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(
                    Component.empty(),
                    Component.text("Show items you can craft").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Vanilla + Custom").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("like vanilla book").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            book.setItemMeta(meta);
        }
        return book;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openCraftingGUIs.contains(player.getUniqueId())) return;
        boolean affectsCrafting = false, affectsDecoration = false;
        for (int slot : event.getRawSlots()) {
            if (slot >= 54) continue;
            boolean isCraftingSlot = false;
            for (int craftSlot : CRAFTING_SLOTS) if (slot == craftSlot) { isCraftingSlot = true; affectsCrafting = true; break; }
            if (!isCraftingSlot) affectsDecoration = true;
        }
        if (affectsDecoration) { event.setCancelled(true); return; }
        if (affectsCrafting && running) {
            Inventory inventory = event.getInventory();
            com.nyarutoru.nekoplugin.utils.SchedulerUtils.runAtEntity(player, () -> { if (running && openCraftingGUIs.contains(player.getUniqueId())) updateCraftingResult(inventory); });
        }
    }

    private ItemStack createArrowItem() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("→ Craft →").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    private ItemStack createCloseButton() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✖ Close").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            barrier.setItemMeta(meta);
        }
        return barrier;
    }
}
