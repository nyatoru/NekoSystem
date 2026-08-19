package com.nyarutoru.nekoplugin.features.furnace;

import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks placed Upgrade Furnaces and accelerates their smelting speed.
 * Only mutates furnace state from the owning region thread.
 */
public class FurnaceManager {
    private static final int PERIOD_TICKS = 5;

    private static final FurnaceManager INSTANCE = new FurnaceManager();

    private final ConcurrentHashMap<Location, Integer> furnaces = new ConcurrentHashMap<>();
    private SchedulerUtils.TaskHandle task;

    public static FurnaceManager getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (task != null)
            return;
        task = SchedulerUtils.runGlobalTimerTask(this::tick, 1, PERIOD_TICKS);
    }

    public void stop() {
        SchedulerUtils.cancelTask(task);
        task = null;
        furnaces.clear();
    }

    public void track(Location location, int level) {
        furnaces.put(location, level);
    }

    public void untrack(Location location) {
        furnaces.remove(location);
    }

    private void tick() {
        for (Location location : furnaces.keySet()) {
            int level = furnaces.get(location);
            SchedulerUtils.runAtLocation(location, () -> accelerate(location, level));
        }
    }

    private void accelerate(Location location, int level) {
        if (location.getWorld() == null)
            return;
        Block block = location.getBlock();
        if (!(block.getState() instanceof Furnace furnace)) {
            furnaces.remove(location);
            return;
        }

        String tierName = furnace.getPersistentDataContainer().get(FurnaceRecipes.getTierKey(), PersistentDataType.STRING);
        FurnaceTier tier = tierName == null ? null : FurnaceTier.getByName(tierName);
        if (tier == null || tier.getLevel() != level) {
            furnaces.remove(location);
            return;
        }

        int extra = PERIOD_TICKS * (tier.getSpeedMultiplier() - 1);
        if (extra <= 0 || furnace.getBurnTime() <= 0)
            return;

        ItemStack smelt = furnace.getInventory().getSmelting();
        if (!isSmeltable(smelt))
            return;

        furnace.setCookTime((short) (furnace.getCookTime() + extra));
        furnace.setBurnTime((short) Math.max(0, furnace.getBurnTime() - extra));
        furnace.update();
    }

    private static boolean isSmeltable(ItemStack item) {
        if (item == null || item.getType().isAir())
            return false;
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof FurnaceRecipe furnaceRecipe && furnaceRecipe.getInput().isSimilar(item)) {
                return true;
            }
        }
        return false;
    }
}
