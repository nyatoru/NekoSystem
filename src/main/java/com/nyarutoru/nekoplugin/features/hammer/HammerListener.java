package com.nyarutoru.nekoplugin.features.hammer;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles Hammer 3x3 mining, anvil restriction, and mining speed reduction.
 * Optimized with BlockPos for better performance.
 */
public class HammerListener implements Listener {

    // Mining speed modifier for hammers (35% reduction = multiply by 0.65)
    private final NamespacedKey HAMMER_SPEED_MODIFIER_KEY;
    private static final double DEFAULT_MINING_SPEED_REDUCTION = 0.35;
    private volatile double miningSpeedReduction = DEFAULT_MINING_SPEED_REDUCTION;

    // Static 3x3 offset patterns
    private static final int[][] OFFSETS_HORIZONTAL = {
            { -1, 0, -1 }, { 0, 0, -1 }, { 1, 0, -1 },
            { -1, 0, 0 }, { 0, 0, 0 }, { 1, 0, 0 },
            { -1, 0, 1 }, { 0, 0, 1 }, { 1, 0, 1 }
    };

    private static final int[][] OFFSETS_VERTICAL_NS = {
            { -1, -1, 0 }, { 0, -1, 0 }, { 1, -1, 0 },
            { -1, 0, 0 }, { 0, 0, 0 }, { 1, 0, 0 },
            { -1, 1, 0 }, { 0, 1, 0 }, { 1, 1, 0 }
    };

    private static final int[][] OFFSETS_VERTICAL_EW = {
            { 0, -1, -1 }, { 0, -1, 0 }, { 0, -1, 1 },
            { 0, 0, -1 }, { 0, 0, 0 }, { 0, 0, 1 },
            { 0, 1, -1 }, { 0, 1, 0 }, { 0, 1, 1 }
    };

    // Blocks that can be mined with a pickaxe
    // Using EnumSet for optimal performance (100x faster than HashSet for Material lookups)
    private static final Set<Material> MINEABLE = EnumSet.of(
            // Stone types
            Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.POLISHED_GRANITE, Material.POLISHED_DIORITE, Material.POLISHED_ANDESITE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
            Material.CALCITE, Material.TUFF, Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE,
            Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
            Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES,
            Material.CHISELED_DEEPSLATE, Material.INFESTED_STONE,
            // Netherrack and basalt
            Material.NETHERRACK, Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
            Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.CHISELED_POLISHED_BLACKSTONE,
            Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Material.GILDED_BLACKSTONE,
            // End stone
            Material.END_STONE, Material.END_STONE_BRICKS,
            // Sandstone
            Material.SANDSTONE, Material.CHISELED_SANDSTONE, Material.CUT_SANDSTONE, Material.SMOOTH_SANDSTONE,
            Material.RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, Material.CUT_RED_SANDSTONE,
            Material.SMOOTH_RED_SANDSTONE,
            // Terracotta
            Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA,
            Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA,
            // Concrete
            Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
            Material.BLACK_CONCRETE,
            // Ores
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS,
            // Raw ore blocks
            Material.RAW_IRON_BLOCK, Material.RAW_COPPER_BLOCK, Material.RAW_GOLD_BLOCK,
            // Ice
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            // Prismarine
            Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
            // Obsidian
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            // Bricks
            Material.BRICKS, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS,
            Material.CHISELED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS,
            // Quartz
            Material.QUARTZ_BLOCK, Material.CHISELED_QUARTZ_BLOCK, Material.QUARTZ_BRICKS,
            Material.QUARTZ_PILLAR, Material.SMOOTH_QUARTZ,
            // Purpur
            Material.PURPUR_BLOCK, Material.PURPUR_PILLAR,
            // Amethyst
            Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST,
            // Misc
            Material.GLOWSTONE, Material.MAGMA_BLOCK, Material.BONE_BLOCK,
            Material.LODESTONE, Material.RESPAWN_ANCHOR);
    private volatile Set<Material> mineableMaterials = Set.copyOf(MINEABLE);

    private final NekoPlugin plugin;
    private final AtomicLong generation = new AtomicLong();
    private final Set<SchedulerUtils.TaskHandle> ownedTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    // Track blocks being broken to prevent recursion using BlockPos
    private final Set<BlockPos> breakingBlocks = ConcurrentHashMap.newKeySet();

    public HammerListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.HAMMER_SPEED_MODIFIER_KEY = new NamespacedKey(plugin, "hammer_mining_speed");
    }

    void start() {
        if (running) return;
        for (SchedulerUtils.TaskHandle task : ownedTasks) {
            SchedulerUtils.cancelTask(task);
        }
        ownedTasks.clear();
        running = true;
        long expectedGeneration = generation.incrementAndGet();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            schedulePlayer(player, expectedGeneration, () -> updateMiningSpeedModifier(player,
                    player.getInventory().getItemInMainHand()));
        }
    }

    void stop() {
        running = false;
        long cleanupGeneration = generation.incrementAndGet();
        for (SchedulerUtils.TaskHandle task : ownedTasks) {
            SchedulerUtils.cancelTask(task);
        }
        ownedTasks.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            scheduleOwned(player, cleanupGeneration, () -> removeMiningSpeedModifier(player), true);
        }
        breakingBlocks.clear();
    }

    public void registerSettings(SettingRegistry registry, AdminState state) {
        SettingDescriptor<Double> reduction = SettingDescriptor.doubleValue(
                "mining-speed-reduction", "Mining speed reduction", DEFAULT_MINING_SPEED_REDUCTION,
                0.0, 0.95, ApplySemantics.IMMEDIATE, this::setMiningSpeedReduction);
        SettingDescriptor<List<Material>> materials = SettingDescriptor.materials(
                "mineable-materials", "Mineable materials", List.copyOf(MINEABLE),
                ApplySemantics.IMMEDIATE, this::setMineableMaterials);
        registry.register("hammer", reduction);
        registry.register("hammer", materials);
        applyStored(state, reduction);
        applyStored(state, materials);
    }

    public double getMiningSpeedReduction() { return miningSpeedReduction; }

    public void setMiningSpeedReduction(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 0.95) {
            throw new IllegalArgumentException("Reduction must be between 0 and 0.95");
        }
        miningSpeedReduction = value;
        long expectedGeneration = generation.get();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            schedulePlayer(player, expectedGeneration, () -> updateMiningSpeedModifier(player,
                    player.getInventory().getItemInMainHand()));
        }
    }

    public void setMineableMaterials(List<Material> materials) {
        if (materials.isEmpty()) throw new IllegalArgumentException("At least one material is required");
        mineableMaterials = Set.copyOf(materials);
    }

    private <T> void applyStored(AdminState state, SettingDescriptor<T> descriptor) {
        String stored = state.settingValue("hammer", descriptor.key());
        try {
            descriptor.apply(stored == null ? descriptor.defaultValue() : descriptor.parse(stored));
        } catch (IllegalArgumentException ignored) {
            descriptor.apply(descriptor.defaultValue());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled())
            return;

        Player player = event.getPlayer();
        if (player.isSneaking())
            return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!HammerRecipes.isHammer(tool))
            return;

        Block center = event.getBlock();
        BlockPos centerPos = BlockPos.from(center.getLocation());

        // Prevent recursion
        if (breakingBlocks.contains(centerPos))
            return;

        // Check if block is mineable
        if (!isMineable(center.getType()))
            return;

        // Get the face the player is looking at to determine 3x3 plane
        BlockFace face = getTargetBlockFace(player);

        breakingBlocks.add(centerPos);

        try {
            mine3x3(player, center, face, tool);
        } finally {
            breakingBlocks.remove(centerPos);
        }
    }

    private void mine3x3(Player player, Block center, BlockFace face, ItemStack hammer) {
        int[][] offsets = get3x3Offsets(face);
        BlockPos centerPos = BlockPos.from(center.getLocation());
        World world = center.getWorld();

        if (world == null) {
            return;
        }

        boolean hasSilkTouch = hammer.containsEnchantment(Enchantment.SILK_TOUCH);

        if (!ItemUtils.isUnbreakable(hammer) && ItemUtils.wouldBreakFromDamage(hammer, 1)) {
            return;
        }

        if (SchedulerUtils.isFolia()) {
            // Folia: each adjacent block break must be on its region thread.
            // Collect targets best-effort, then schedule per-location.
            java.util.List<BlockPos> scheduled = new java.util.ArrayList<>();
            for (int[] offset : offsets) {
                if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0)
                    continue;
                BlockPos targetPos = centerPos.add(offset[0], offset[1], offset[2]);
                if (breakingBlocks.contains(targetPos))
                    continue;
                // best-effort pre-check to avoid scheduling air; cross-region reads are caught
                try {
                    Block pre = targetPos.getBlock(world);
                    if (pre == null) continue;
                    Material preType;
                    try { preType = pre.getType(); } catch (Throwable ex) { continue; }
                    if (preType == Material.AIR || !isMineable(preType)) continue;
                } catch (Throwable ex) {
                    // cross-region read failed; still schedule and re-check on target thread
                }
                scheduled.add(targetPos);
                ItemStack hammerCopy = hammer.clone();
                SchedulerUtils.runAtLocation(targetPos.toLocation(world), () -> {
                    if (breakingBlocks.contains(targetPos)) return;
                    Block target;
                    try { target = targetPos.getBlock(world); } catch (Throwable ex) { return; }
                    if (target == null) return;
                    Material type;
                    try { type = target.getType(); } catch (Throwable ex) { return; }
                    if (type == Material.AIR || !isMineable(type)) return;
                    breakingBlocks.add(targetPos);
                    try {
                        if (hasSilkTouch) target.breakNaturally(hammerCopy, true);
                        else target.breakNaturally(hammerCopy);
                    } catch (Throwable ignored) {
                    } finally {
                        breakingBlocks.remove(targetPos);
                    }
                });
            }
            if (!scheduled.isEmpty()) {
                ItemUtils.applyDurabilityDamage(hammer, 1);
            }
            return;
        }

        boolean brokeAnyBlock = false;

        for (int[] offset : offsets) {
            if (offset[0] == 0 && offset[1] == 0 && offset[2] == 0)
                continue;

            BlockPos targetPos = centerPos.add(offset[0], offset[1], offset[2]);

            if (breakingBlocks.contains(targetPos))
                continue;

            Block target = targetPos.getBlock(world);
            Material type = target.getType();

            if (type == Material.AIR || !isMineable(type))
                continue;

            breakingBlocks.add(targetPos);
            try {
                if (hasSilkTouch) {
                    target.breakNaturally(hammer, true);
                } else {
                    target.breakNaturally(hammer);
                }
                brokeAnyBlock = true;
            } finally {
                breakingBlocks.remove(targetPos);
            }
        }

        if (brokeAnyBlock) {
            ItemUtils.applyDurabilityDamage(hammer, 1);
        }
    }

    private int[][] get3x3Offsets(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> OFFSETS_HORIZONTAL;
            case NORTH, SOUTH -> OFFSETS_VERTICAL_NS;
            case EAST, WEST -> OFFSETS_VERTICAL_EW;
            default -> new int[][] { { 0, 0, 0 } };
        };
    }

    private BlockFace getTargetBlockFace(Player player) {
        float pitch = player.getLocation().getPitch();
        float yaw = player.getLocation().getYaw();

        // Looking up/down
        if (pitch < -45)
            return BlockFace.UP;
        if (pitch > 45)
            return BlockFace.DOWN;

        // Normalize yaw
        yaw = (yaw % 360 + 360) % 360;

        // Cardinal directions
        if (yaw >= 315 || yaw < 45)
            return BlockFace.SOUTH;
        if (yaw >= 45 && yaw < 135)
            return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225)
            return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private boolean isMineable(Material material) {
        return mineableMaterials.contains(material);
    }

    /**
     * Prevent combining two hammers in anvil.
     * Allow enchanting with enchanted books and renaming.
     */
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);

        // Only block if BOTH items are hammers (combining hammers)
        if (HammerRecipes.isHammer(first) && HammerRecipes.isHammer(second)) {
            event.setResult(null);
        }
    }

    // ========== Mining Speed Reduction ==========

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        updateMiningSpeedModifier(player, newItem);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack newMainHand = event.getOffHandItem();
        updateMiningSpeedModifier(player, newMainHand);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        updateMiningSpeedModifier(player, mainHand);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scheduleOwned(player, generation.get(), () -> removeMiningSpeedModifier(player), false);
    }

    private void updateMiningSpeedModifier(Player player, ItemStack item) {
        if (HammerRecipes.isHammer(item)) {
            HammerRecipes.ensureHammerModel(item);
            applyMiningSpeedModifier(player);
        } else {
            removeMiningSpeedModifier(player);
        }
    }

    private Attribute getBlockBreakSpeedAttribute() {
        Registry<Attribute> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
        NamespacedKey key = NamespacedKey.minecraft("player.block_break_speed");
        return registry.get(key);
    }

    private void applyMiningSpeedModifier(Player player) {
        Attribute blockBreakSpeed = getBlockBreakSpeedAttribute();
        if (blockBreakSpeed == null)
            return;

        AttributeInstance attribute = player.getAttribute(blockBreakSpeed);
        if (attribute == null)
            return;

        AttributeModifier existingModifier = attribute.getModifier(HAMMER_SPEED_MODIFIER_KEY);
        if (existingModifier != null) attribute.removeModifier(existingModifier);

        // Create and apply modifier
        AttributeModifier modifier = new AttributeModifier(
                HAMMER_SPEED_MODIFIER_KEY,
                -miningSpeedReduction,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addModifier(modifier);
    }

    private void removeMiningSpeedModifier(Player player) {
        Attribute blockBreakSpeed = getBlockBreakSpeedAttribute();
        if (blockBreakSpeed == null)
            return;

        AttributeInstance attribute = player.getAttribute(blockBreakSpeed);
        if (attribute == null)
            return;

        AttributeModifier existingModifier = attribute.getModifier(HAMMER_SPEED_MODIFIER_KEY);
        if (existingModifier != null) {
            attribute.removeModifier(existingModifier);
        }
    }

    private void schedulePlayer(Player player, long expectedGeneration, Runnable action) {
        scheduleOwned(player, expectedGeneration, action, false);
    }

    private void scheduleOwned(Player player, long expectedGeneration, Runnable action, boolean cleanup) {
        SchedulerUtils.TaskHandle[] holder = new SchedulerUtils.TaskHandle[1];
        holder[0] = SchedulerUtils.runAtPlayerTask(player, () -> {
            ownedTasks.remove(holder[0]);
            if ((cleanup && !running && generation.get() == expectedGeneration)
                    || (!cleanup && running && generation.get() == expectedGeneration)) {
                action.run();
            }
        });
        ownedTasks.add(holder[0]);
    }

    void clearRecursionState() {
        breakingBlocks.clear();
    }
}
