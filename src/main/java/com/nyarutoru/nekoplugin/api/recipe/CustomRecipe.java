package com.nyarutoru.nekoplugin.api.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.function.Predicate;

/**
 * Represents a custom crafting recipe.
 */
public class CustomRecipe {

    private final String id;
    private final String category;
    private final ItemStack result;
    private final RecipeShape shape;
    private final Ingredient[] ingredients;

    private CustomRecipe(String id, String category, ItemStack result, RecipeShape shape, Ingredient[] ingredients) {
        this.id = id;
        this.category = category;
        this.result = result;
        this.shape = shape;
        this.ingredients = ingredients;
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public ItemStack getResult() {
        return result;
    }

    public RecipeShape getShape() {
        return shape;
    }

    /**
     * Check if the given grid matches this recipe.
     */
    public boolean matches(ItemStack[] grid) {
        if (grid == null || grid.length != 9)
            return false;

        if (shape == RecipeShape.SHAPED) {
            return matchesShaped(grid);
        } else {
            return matchesShapeless(grid);
        }
    }

    private boolean matchesShaped(ItemStack[] grid) {
        for (int i = 0; i < 9; i++) {
            if (!ingredients[i].matches(grid[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesShapeless(ItemStack[] grid) {
        boolean[] ingredientMatched = new boolean[9];
        boolean[] gridUsed = new boolean[9];

        for (int i = 0; i < 9; i++) {
            if (ingredients[i].isEmpty())
                continue;

            boolean found = false;
            for (int j = 0; j < 9; j++) {
                if (gridUsed[j])
                    continue;
                if (ingredients[i].matches(grid[j])) {
                    gridUsed[j] = true;
                    ingredientMatched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }

        // Check for extra items in grid
        for (int i = 0; i < 9; i++) {
            if (!gridUsed[i] && grid[i] != null && grid[i].getType() != Material.AIR) {
                return false;
            }
        }

        return true;
    }

    // ========== Builder ==========

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String category = "custom";
        private ItemStack result;
        private RecipeShape shape = RecipeShape.SHAPED;
        private final Ingredient[] ingredients = new Ingredient[9];

        public Builder(String id) {
            this.id = id;
            for (int i = 0; i < 9; i++) {
                ingredients[i] = Ingredient.EMPTY;
            }
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder result(ItemStack result) {
            this.result = result;
            return this;
        }

        public Builder shaped() {
            this.shape = RecipeShape.SHAPED;
            return this;
        }

        public Builder shapeless() {
            this.shape = RecipeShape.SHAPELESS;
            return this;
        }

        /**
         * Set shaped pattern (3 strings of 3 chars each).
         * Use ' ' for empty slot.
         */
        public Builder pattern(String row1, String row2, String row3,
                java.util.Map<Character, Ingredient> ingredientMap) {
            String[] rows = { row1, row2, row3 };
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    char c = rows[row].charAt(col);
                    int index = row * 3 + col;
                    if (c == ' ') {
                        ingredients[index] = Ingredient.EMPTY;
                    } else {
                        ingredients[index] = ingredientMap.getOrDefault(c, Ingredient.EMPTY);
                    }
                }
            }
            return this;
        }

        /**
         * Set ingredient at specific slot (0-8).
         */
        public Builder ingredient(int slot, Ingredient ingredient) {
            if (slot >= 0 && slot < 9) {
                ingredients[slot] = ingredient;
            }
            return this;
        }

        public CustomRecipe build() {
            if (result == null) {
                throw new IllegalStateException("Recipe result cannot be null");
            }
            return new CustomRecipe(id, category, result, shape, ingredients.clone());
        }
    }

    public enum RecipeShape {
        SHAPED,
        SHAPELESS
    }

    // ========== Ingredient ==========

    public static class Ingredient {
        public static final Ingredient EMPTY = new Ingredient(null, null, null);

        private final Material material;
        private final NamespacedKey customKey;
        private final String customValue;

        private Ingredient(Material material, NamespacedKey customKey, String customValue) {
            this.material = material;
            this.customKey = customKey;
            this.customValue = customValue;
        }

        public static Ingredient of(Material material) {
            return new Ingredient(material, null, null);
        }

        public static Ingredient ofCustomItem(Material material, NamespacedKey key, String value) {
            return new Ingredient(material, key, value);
        }

        public boolean isEmpty() {
            return material == null;
        }

        public boolean matches(ItemStack item) {
            if (isEmpty()) {
                return item == null || item.getType() == Material.AIR;
            }

            if (item == null || item.getType() == Material.AIR) {
                return false;
            }

            if (item.getType() != material) {
                return false;
            }

            // Check custom data if specified
            if (customKey != null && customValue != null) {
                if (!item.hasItemMeta())
                    return false;
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                String value = pdc.get(customKey, PersistentDataType.STRING);
                return customValue.equals(value);
            }

            return true;
        }
    }
}
