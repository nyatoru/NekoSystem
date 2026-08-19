package com.nyarutoru.nekoplugin.features.tool;

import com.nyarutoru.nekoplugin.api.tool.AbstractVeinMiner;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shears Harvest - mass-harvest connected leaves with shears.
 * Hard cap of 255 blocks per operation (vanilla leaf-block limit).
 */
public class ShearsHarvestListener extends AbstractVeinMiner {

    public static final String TOOL_NAME = "Shears Harvest";
    public static final int DEFAULT_MAX_BLOCKS = 255;

    private volatile int maxBlocks = DEFAULT_MAX_BLOCKS;

    private static final Set<Material> LEAVES = EnumSet.of(
            Material.OAK_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES,
            Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES,
            Material.CHERRY_LEAVES,
            Material.PALE_OAK_LEAVES);

    private final Predicate<Player> toolPredicate = this::isHoldingShears;

    public void registerSettings(SettingRegistry registry, AdminState state, String featureId) {
        SettingDescriptor<Integer> max = SettingDescriptor.integer(
                "shears-max-blocks", "Maximum blocks", DEFAULT_MAX_BLOCKS, 1, 1000,
                ApplySemantics.IMMEDIATE, this::setMaxBlocks);
        registry.register(featureId, max);
        applyStored(state, featureId, max);
    }

    public void setMaxBlocks(int value) { maxBlocks = value; }

    private <T> void applyStored(AdminState state, String featureId, SettingDescriptor<T> descriptor) {
        String stored = state.settingValue(featureId, descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    @Override
    protected String getToolName() {
        return TOOL_NAME;
    }

    @Override
    protected int getMaxBlocks() {
        return maxBlocks;
    }

    @Override
    protected int[][] getSearchOffsets() {
        return CARDINAL_OFFSETS;
    }

    @Override
    protected Set<Material> getTargetMaterials() {
        return LEAVES;
    }

    @Override
    protected Predicate<Player> getToolPredicate() {
        return toolPredicate;
    }

    private boolean isHoldingShears(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return ItemUtils.isShears(item);
    }
}
