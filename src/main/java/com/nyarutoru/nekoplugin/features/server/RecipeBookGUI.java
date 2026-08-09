package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.api.recipe.CustomRecipe;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recipe Book GUI for browsing recipes.
 * Slot 8 is the "show craftable" toggle like vanilla.
 * Supports both custom (RecipeAPI) and vanilla crafting recipes.
 * Only lives inside the custom crafting GUI - no hotbar item.
 */
public class RecipeBookGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 28;
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int CRAFTABLE_TOGGLE_SLOT = 8;
    private static final Component BOOK_TITLE = Component.text("✦ Recipe Book ✦")
            .color(NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.BOLD, true);

    private final Map<UUID, GUIState> playerStates = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private RecipePreviewGUI recipePreviewGUI;

    public RecipePreviewGUI getRecipePreviewGUI() { return recipePreviewGUI; }
    public void setRecipePreviewGUI(RecipePreviewGUI recipePreviewGUI) { this.recipePreviewGUI = recipePreviewGUI; }
    public void cleanup() { running = false; playerStates.clear(); }

    // ========== Entry wrapper ==========

    static class Entry {
        final ItemStack result;
        final CustomRecipe custom;
        final Recipe vanilla;
        final String category; // for lore
        final String id;

        Entry(CustomRecipe c) {
            this.custom = c;
            this.vanilla = null;
            this.result = c.getResult();
            this.category = c.getCategory();
            this.id = c.getId();
        }
        Entry(Recipe v, ItemStack res) {
            this.vanilla = v;
            this.custom = null;
            this.result = res;
            this.category = "vanilla";
            String k = "vanilla";
            try { k = v.toString(); } catch (Exception ignored) {}
            if (v instanceof ShapedRecipe sr) k = sr.getKey().toString();
            else if (v instanceof ShapelessRecipe sr) k = sr.getKey().toString();
            this.id = k;
        }
        boolean isCustom() { return custom != null; }
        String dedupKey() {
            String name = "";
            if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName());
            }
            return name + "_" + result.getType().name();
        }
    }

    // ========== Craftable checks ==========

    private boolean canCraftCustom(Player player, CustomRecipe recipe) {
        CustomRecipe.Ingredient[] ingredients = recipe.getIngredients();
        boolean has = false;
        for (CustomRecipe.Ingredient ing : ingredients) if (!ing.isEmpty()) { has = true; break; }
        if (!has) return false;
        ItemStack[] inv = player.getInventory().getContents();
        int[] rem = new int[inv.length];
        for (int i = 0; i < inv.length; i++) {
            ItemStack s = inv[i];
            rem[i] = (s != null && s.getType() != Material.AIR) ? s.getAmount() : 0;
        }
        for (CustomRecipe.Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            boolean matched = false;
            for (int i = 0; i < inv.length; i++) {
                if (rem[i] <= 0) continue;
                ItemStack s = inv[i];
                if (s == null) continue;
                if (ing.matches(s)) { rem[i]--; matched = true; break; }
            }
            if (!matched) return false;
        }
        return true;
    }

    private boolean canCraftVanilla(Player player, Recipe recipe) {
        List<RecipeChoice> required = new ArrayList<>();
        if (recipe instanceof ShapedRecipe sr) {
            String[] shape = sr.getShape();
            Map<Character, RecipeChoice> map = sr.getChoiceMap();
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c == ' ') continue;
                    RecipeChoice choice = map.get(c);
                    if (choice != null) required.add(choice);
                }
            }
        } else if (recipe instanceof ShapelessRecipe sr) {
            required.addAll(sr.getChoiceList());
        } else {
            return false;
        }
        if (required.isEmpty()) return false;
        ItemStack[] inv = player.getInventory().getContents();
        int[] rem = new int[inv.length];
        for (int i = 0; i < inv.length; i++) {
            ItemStack s = inv[i];
            rem[i] = (s != null && s.getType() != Material.AIR) ? s.getAmount() : 0;
        }
        for (RecipeChoice choice : required) {
            if (choice == null) continue;
            boolean matched = false;
            for (int i = 0; i < inv.length; i++) {
                if (rem[i] <= 0) continue;
                ItemStack s = inv[i];
                if (s == null) continue;
                try {
                    if (choice.test(s)) { rem[i]--; matched = true; break; }
                } catch (Exception ignored) {}
            }
            if (!matched) return false;
        }
        return true;
    }

    private boolean canCraft(Player player, Entry e) {
        if (e.isCustom()) return canCraftCustom(player, e.custom);
        else return canCraftVanilla(player, e.vanilla);
    }

    // ========== Collectors ==========

    private List<Entry> getCustomEntries() {
        List<CustomRecipe> all = RecipeAPI.getInstance().getAllRecipes();
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (CustomRecipe c : all) {
            Entry e = new Entry(c);
            String k = e.dedupKey();
            if (!unique.containsKey(k)) unique.put(k, e);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Entry> getVanillaEntries() {
        Map<String, Entry> unique = new LinkedHashMap<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            // only crafting recipes (shaped/shapeless) - ignore furnace/blasting etc for book
            if (!(r instanceof ShapedRecipe) && !(r instanceof ShapelessRecipe)) continue;
            ItemStack res;
            try { res = r.getResult(); } catch (Exception ex) { continue; }
            if (res == null || res.getType() == Material.AIR) continue;
            Entry e = new Entry(r, res);
            String k = e.dedupKey();
            // dedup by result name+type; keep first
            if (!unique.containsKey(k)) unique.put(k, e);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Entry> getAllEntries(boolean includeVanilla) {
        List<Entry> out = new ArrayList<>(getCustomEntries());
        if (includeVanilla) {
            // add vanilla not already covered by custom result key
            Set<String> existingKeys = new HashSet<>();
            for (Entry e : out) existingKeys.add(e.dedupKey());
            for (Entry ve : getVanillaEntries()) {
                if (!existingKeys.contains(ve.dedupKey())) out.add(ve);
            }
        }
        return out;
    }

    private List<Entry> getFilteredEntries(Player player, boolean craftableOnly, boolean includeVanilla) {
        List<Entry> all = getAllEntries(includeVanilla);
        if (!craftableOnly) return all;
        List<Entry> filtered = new ArrayList<>();
        for (Entry e : all) if (canCraft(player, e)) filtered.add(e);
        return filtered;
    }

    // ========== Open ==========

    /** All custom, not craftable only, no vanilla */
    public void openRecipeBook(Player player) { openRecipeBook(player, false, false); }

    /** Craftable toggle, vanilla inclusion depends on caller */
    public void openRecipeBook(Player player, boolean craftableOnly) {
        // keep vanilla inclusion as true for craftable view (slot 8), false otherwise? For backward compat: if caller passes true, include vanilla
        boolean includeVanilla = craftableOnly;
        openRecipeBook(player, craftableOnly, includeVanilla);
    }

    public void openRecipeBook(Player player, boolean craftableOnly, boolean includeVanilla) {
        if (!running) return;
        List<Entry> entries = getFilteredEntries(player, craftableOnly, includeVanilla);
        GUIState state = new GUIState(0, entries, craftableOnly, includeVanilla);
        playerStates.put(player.getUniqueId(), state);
        openRecipeListPage(player, state);
    }

    private void openRecipeListPage(Player player, GUIState state) {
        Inventory gui = Bukkit.createInventory(null, 54, BOOK_TITLE);
        ItemStack blackGlass = createGlassPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 9; i++) {
            if (i == 4 || i == CRAFTABLE_TOGGLE_SLOT) continue;
            gui.setItem(i, blackGlass);
        }
        for (int i = 45; i < 54; i++) gui.setItem(i, blackGlass);
        gui.setItem(9, grayGlass);
        gui.setItem(17, grayGlass);
        gui.setItem(18, grayGlass);
        gui.setItem(26, grayGlass);
        gui.setItem(27, grayGlass);
        gui.setItem(35, grayGlass);
        gui.setItem(36, grayGlass);
        gui.setItem(44, grayGlass);

        int totalPages = Math.max(1, (int) Math.ceil((double) state.entries.size() / ITEMS_PER_PAGE));
        gui.setItem(4, createPageIndicator(state.page + 1, totalPages));

        int totalAll = getAllEntries(state.includeVanilla).size();
        int craftableCount = getFilteredEntries(player, true, state.includeVanilla).size();
        gui.setItem(CRAFTABLE_TOGGLE_SLOT, createCraftableToggle(state.craftableOnly, craftableCount, totalAll, state.includeVanilla));

        int start = state.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, state.entries.size());
        for (int i = start; i < end; i++) {
            int slotIndex = i - start;
            if (slotIndex < ITEM_SLOTS.length) {
                Entry e = state.entries.get(i);
                ItemStack display = createEntryDisplayItem(e);
                if (!state.craftableOnly && canCraft(player, e)) display = addCraftableGlint(display, e);
                gui.setItem(ITEM_SLOTS[slotIndex], display);
            }
        }

        if (state.entries.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                if (state.craftableOnly) {
                    meta.displayName(Component.text("No craftable recipes").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            Component.text("Gather materials to unlock recipes").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("Click toggle to show all").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
                } else {
                    meta.displayName(Component.text(state.includeVanilla ? "No recipes found" : "No custom recipes").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                empty.setItemMeta(meta);
            }
            gui.setItem(22, empty);
        }

        if (state.page > 0) gui.setItem(48, createNavigationItem(Material.ARROW, "← Previous Page"));
        if (state.page < totalPages - 1) gui.setItem(50, createNavigationItem(Material.ARROW, "Next Page →"));
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
    }

    private ItemStack addCraftableGlint(ItemStack item, Entry e) {
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            else lore = new ArrayList<>(lore);
            lore.add(Component.text("✔ Craftable").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private void openRecipePreview(Player player, Entry entry, GUIState prevState) {
        playerStates.remove(player.getUniqueId());
        if (recipePreviewGUI != null) {
            if (entry.isCustom()) recipePreviewGUI.openPreview(player, entry.custom, prevState);
            else recipePreviewGUI.openVanillaPreview(player, entry.vanilla, prevState);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // strict: only handle Recipe Book GUI, never Crafting Table GUI (both use slot 23 arrow etc.)
        try { if (!event.getView().title().equals(BOOK_TITLE)) return; } catch (Exception ignored) { return; }
        GUIState state = playerStates.get(player.getUniqueId());
        if (state == null) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        handleRecipeListClick(player, state, slot);
    }

    private void handleRecipeListClick(Player player, GUIState state, int slot) {
        if (slot == CRAFTABLE_TOGGLE_SLOT) {
            boolean next = !state.craftableOnly;
            List<Entry> filtered = getFilteredEntries(player, next, state.includeVanilla);
            GUIState nextState = new GUIState(0, filtered, next, state.includeVanilla);
            playerStates.put(player.getUniqueId(), nextState);
            openRecipeListPage(player, nextState);
            return;
        }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 48 && state.page > 0) { state.page--; openRecipeListPage(player, state); return; }
        int totalPages = (int) Math.ceil((double) state.entries.size() / ITEMS_PER_PAGE);
        if (slot == 50 && state.page < totalPages - 1) { state.page++; openRecipeListPage(player, state); return; }
        for (int i = 0; i < ITEM_SLOTS.length; i++) if (ITEM_SLOTS[i] == slot) {
            int idx = state.page * ITEMS_PER_PAGE + i;
            if (idx < state.entries.size()) openRecipePreview(player, state.entries.get(idx), state);
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Don't clear state when player is switching pages / toggling craftable inside the book
        // (openRecipeListPage opens a new inventory with the same title synchronously;
        // the close event for the old view fires after open, and getOpenInventory() is already the new one)
        // If the new open inventory is still the book, keep the state for the next click.
        try {
            if (player.getOpenInventory().title().equals(BOOK_TITLE)) return;
        } catch (Exception ignored) {}
        // Also keep if the closed view wasn't the book (e.g. preview closed)
        try {
            if (!event.getView().title().equals(BOOK_TITLE)) return;
        } catch (Exception ignored) {}
        playerStates.remove(player.getUniqueId());
    }

    private ItemStack createCraftableToggle(boolean enabled, int craftableCount, int totalCount, boolean includeVanilla) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (enabled) {
                meta.displayName(Component.text("☑ Show craftable: ON").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
                meta.lore(List.of(
                        Component.text("Showing " + craftableCount + " craftable").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("of " + totalCount + " " + (includeVanilla ? "vanilla+custom" : "custom")).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to show all").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
                meta.setEnchantmentGlintOverride(true);
            } else {
                meta.displayName(Component.text("☐ Show craftable: OFF").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
                meta.lore(List.of(
                        Component.text(craftableCount + " craftable").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("of " + totalCount + " " + (includeVanilla ? "vanilla+custom" : "custom")).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to show craftable only").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createEntryDisplayItem(Entry e) {
        ItemStack item = e.result.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Click to view recipe").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Category: " + e.category).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (!e.isCustom()) lore.add(Component.text("Vanilla").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlassPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) { meta.displayName(Component.text(" ")); pane.setItemMeta(meta); }
        return pane;
    }
    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.text(name).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)); item.setItemMeta(meta); }
        return item;
    }
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.text("✖ Close").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true)); item.setItemMeta(meta); }
        return item;
    }
    private ItemStack createPageIndicator(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.text("Page " + currentPage + "/" + totalPages).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)); item.setItemMeta(meta); }
        return item;
    }

    static class GUIState {
        int page;
        List<Entry> entries;
        boolean craftableOnly;
        boolean includeVanilla;
        GUIState(int page, List<Entry> entries, boolean craftableOnly, boolean includeVanilla) {
            this.page = page;
            this.entries = entries;
            this.craftableOnly = craftableOnly;
            this.includeVanilla = includeVanilla;
        }
    }
}
