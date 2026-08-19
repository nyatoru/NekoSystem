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
    private static final int CATALOG_SLOT = 0;
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
            this.category = vanillaCategoryFor(res.getType());
            String k = "vanilla";
            try { k = v.toString(); } catch (Exception ignored) {}
            if (v instanceof ShapedRecipe sr) k = sr.getKey().toString();
            else if (v instanceof ShapelessRecipe sr) k = sr.getKey().toString();
            this.id = k;
        }
        private static String vanillaCategoryFor(Material mat) {
            String n = mat.name();
            if (n.endsWith("_SWORD")) return "sword";
            if (n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE")
                    || n.equals("ARROW") || n.equals("SPECTRAL_ARROW") || n.equals("TIPPED_ARROW")) return "combat";
            if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                    || n.endsWith("_HOE") || n.endsWith("_SPEAR")) return "tool";
            if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                    || n.endsWith("_BOOTS")) return "armor";
            if (n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("TURTLE_HELMET")
                    || n.endsWith("_HORSE_ARMOR") || n.endsWith("_HARNESS") || n.endsWith("_NAUTILUS_ARMOR")) return "armor";
            if (n.equals("SHEARS") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL")
                    || n.equals("BRUSH") || n.equals("SPYGLASS") || n.equals("COMPASS")
                    || n.equals("CLOCK") || n.equals("RECOVERY_COMPASS")) return "tool";
            if (mat.isEdible()) return "food";
            if (n.contains("REDSTONE") || n.equals("PISTON") || n.equals("STICKY_PISTON")
                    || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("HOPPER")
                    || n.equals("OBSERVER") || n.equals("COMPARATOR") || n.equals("REPEATER")
                    || n.equals("DAYLIGHT_DETECTOR") || n.equals("TRIPWIRE_HOOK") || n.equals("LEVER")
                    || n.contains("PRESSURE_PLATE") || n.equals("TARGET") || n.equals("LECTERN")
                    || n.equals("TRAPPED_CHEST") || n.equals("CRAFTER")) return "redstone";
            if (mat.isBlock()) return "building";
            return "misc";
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

    boolean canCraftCustom(Player player, CustomRecipe recipe) {
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

    boolean canCraftVanilla(Player player, Recipe recipe) {
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

    private List<String> getAvailableCategories(boolean includeVanilla) {
        List<Entry> all = getAllEntries(includeVanilla);
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry e : all) set.add(e.category);
        return new ArrayList<>(set);
    }

    private List<Entry> getFilteredEntries(Player player, boolean craftableOnly, boolean includeVanilla) {
        return getFilteredEntries(player, craftableOnly, includeVanilla, null);
    }

    private List<Entry> getFilteredEntries(Player player, boolean craftableOnly, boolean includeVanilla, String catalogCategory) {
        List<Entry> all = getAllEntries(includeVanilla);
        if (catalogCategory != null && !catalogCategory.isEmpty() && !catalogCategory.equalsIgnoreCase("all")) {
            List<Entry> byCat = new ArrayList<>();
            for (Entry e : all) if (e.category.equalsIgnoreCase(catalogCategory)) byCat.add(e);
            all = byCat;
        }
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
        openRecipeBook(player, craftableOnly, includeVanilla, null);
    }

    public void openRecipeBook(Player player, boolean craftableOnly, boolean includeVanilla, String catalogCategory) {
        if (!running) return;
        List<Entry> entries = getFilteredEntries(player, craftableOnly, includeVanilla, catalogCategory);
        GUIState state = new GUIState(0, entries, craftableOnly, includeVanilla, catalogCategory);
        playerStates.put(player.getUniqueId(), state);
        openRecipeListPage(player, state);
    }

    /** Re-open preserving page (used when returning from preview) - keeps page, recomputes entries */
    public void openRecipeBook(Player player, GUIState prevState) {
        if (!running || prevState == null) { openRecipeBook(player); return; }
        List<Entry> entries = getFilteredEntries(player, prevState.craftableOnly, prevState.includeVanilla, prevState.catalogCategory);
        int page = prevState.page;
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        GUIState state = new GUIState(page, entries, prevState.craftableOnly, prevState.includeVanilla, prevState.catalogCategory);
        playerStates.put(player.getUniqueId(), state);
        openRecipeListPage(player, state);
    }

    private void openRecipeListPage(Player player, GUIState state) {
        Inventory gui = Bukkit.createInventory(null, 54, BOOK_TITLE);
        ItemStack blackGlass = createGlassPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 9; i++) {
            if (i == 4 || i == CRAFTABLE_TOGGLE_SLOT || i == CATALOG_SLOT) continue;
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

        int totalAll = getFilteredEntries(player, false, state.includeVanilla, state.catalogCategory).size();
        int totalUnfiltered = getAllEntries(state.includeVanilla).size();
        int craftableCount = getFilteredEntries(player, true, state.includeVanilla, state.catalogCategory).size();
        // show catalog-aware counts, but also hint at global if filtered
        gui.setItem(CATALOG_SLOT, createCatalogButton(state, totalAll, totalUnfiltered, craftableCount));
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
                    List<Component> lore = new ArrayList<>();
                    if (state.catalogCategory != null) lore.add(Component.text("In catalog: " + state.catalogCategory).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Gather materials to unlock recipes").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Click toggle to show all").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    if (state.catalogCategory != null) lore.add(Component.text("Or click catalog to change").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                } else {
                    String label = state.catalogCategory != null ? "No recipes in " + state.catalogCategory : (state.includeVanilla ? "No recipes found" : "No custom recipes");
                    meta.displayName(Component.text(label).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    if (state.catalogCategory != null) meta.lore(List.of(Component.text("Click catalog to change").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
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
        if (slot == CATALOG_SLOT) {
            List<String> cats = getAvailableCategories(state.includeVanilla);
            if (cats.isEmpty()) return;
            String cur = state.catalogCategory;
            String next;
            if (cur == null) next = cats.get(0);
            else {
                int idx = -1;
                for (int i = 0; i < cats.size(); i++) if (cats.get(i).equalsIgnoreCase(cur)) { idx = i; break; }
                if (idx == -1 || idx == cats.size() - 1) next = null;
                else next = cats.get(idx + 1);
            }
            List<Entry> filtered = getFilteredEntries(player, state.craftableOnly, state.includeVanilla, next);
            GUIState nextState = new GUIState(0, filtered, state.craftableOnly, state.includeVanilla, next);
            playerStates.put(player.getUniqueId(), nextState);
            openRecipeListPage(player, nextState);
            return;
        }
        if (slot == CRAFTABLE_TOGGLE_SLOT) {
            boolean next = !state.craftableOnly;
            List<Entry> filtered = getFilteredEntries(player, next, state.includeVanilla, state.catalogCategory);
            GUIState nextState = new GUIState(0, filtered, next, state.includeVanilla, state.catalogCategory);
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

    private ItemStack createCatalogButton(GUIState state, int filteredTotal, int globalTotal, int craftableCount) {
        List<String> cats = getAvailableCategories(state.includeVanilla);
        String cur = state.catalogCategory;
        String displayCat = cur == null ? "All" : cur;
        Material mat;
        if (cur == null) mat = Material.BOOKSHELF;
        else if ("vanilla".equalsIgnoreCase(cur)) mat = Material.CRAFTING_TABLE;
        else if ("drawer".equalsIgnoreCase(cur)) mat = Material.BARREL;
        else if ("hammer".equalsIgnoreCase(cur)) mat = Material.ANVIL;
        else if ("server".equalsIgnoreCase(cur)) mat = Material.FURNACE;
        else if ("sword".equalsIgnoreCase(cur)) mat = Material.IRON_SWORD;
        else if ("combat".equalsIgnoreCase(cur)) mat = Material.BOW;
        else if ("tool".equalsIgnoreCase(cur)) mat = Material.IRON_PICKAXE;
        else if ("armor".equalsIgnoreCase(cur)) mat = Material.IRON_CHESTPLATE;
        else if ("food".equalsIgnoreCase(cur)) mat = Material.BREAD;
        else if ("redstone".equalsIgnoreCase(cur)) mat = Material.REDSTONE;
        else if ("building".equalsIgnoreCase(cur)) mat = Material.BRICKS;
        else if ("misc".equalsIgnoreCase(cur)) mat = Material.CHEST;
        else mat = Material.KNOWLEDGE_BOOK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("\uD83D\uDCDA Catalog: " + displayCat).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Showing " + filteredTotal + " recipes").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (cur != null) lore.add(Component.text("of " + globalTotal + " total").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            else lore.add(Component.text(globalTotal + " total • " + craftableCount + " craftable").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (!cats.isEmpty()) {
                lore.add(Component.text("Categories: " + String.join(", ", cats)).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("Click to cycle catalog").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(cur == null ? "Next: " + cats.get(0) : "Next: " + nextCategoryLabel(cats, cur)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("No categories").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            if (cur != null) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String nextCategoryLabel(List<String> cats, String cur) {
        if (cats.isEmpty()) return "All";
        if (cur == null) return cats.get(0);
        int idx = -1;
        for (int i = 0; i < cats.size(); i++) if (cats.get(i).equalsIgnoreCase(cur)) { idx = i; break; }
        if (idx == -1 || idx == cats.size() - 1) return "All";
        return cats.get(idx + 1);
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
        String catalogCategory;
        GUIState(int page, List<Entry> entries, boolean craftableOnly, boolean includeVanilla) {
            this(page, entries, craftableOnly, includeVanilla, null);
        }
        GUIState(int page, List<Entry> entries, boolean craftableOnly, boolean includeVanilla, String catalogCategory) {
            this.page = page;
            this.entries = entries;
            this.craftableOnly = craftableOnly;
            this.includeVanilla = includeVanilla;
            this.catalogCategory = catalogCategory;
        }
    }
}
