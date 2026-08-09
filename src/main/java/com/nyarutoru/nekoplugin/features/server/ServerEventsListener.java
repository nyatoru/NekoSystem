package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import com.nyarutoru.nekoplugin.utils.ServerPerformanceUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidated Server Events Listener.
 * Handles:
 * - Player join/quit messages
 * - Deepslate instant break with Netherite Pickaxe + Efficiency 5 + Haste
 * - Glass instant break with Netherite Pickaxe
 * - Ladder auto-placement up/down
 * - Anvil repair with Iron Block
 * - Lag notifications on chunk generation
 * - Map expansion lag detection and broadcast
 */
public class ServerEventsListener implements Listener {

    // Defaults for user-facing behavior. Values are configured by ServerFeature.
    private static final int DEFAULT_MIN_EFFICIENCY_LEVEL = 5;
    private static final int DEFAULT_MIN_HASTE_AMPLIFIER = 1;
    private static final int DEFAULT_MIN_PLAYERS_FOR_LAG_WARNING = 3;
    private static final double DEFAULT_TPS_WARNING_THRESHOLD = 18.0;
    private static final String DEFAULT_JOIN_MESSAGE = "<green><bold>+</bold> <gray>{player} joined the server.";
    private static final String DEFAULT_QUIT_MESSAGE = "<red><bold>-</bold> <gray>{player} left the server.";
    private static final String DEFAULT_ANVIL_MESSAGE = "✓ Anvil repaired!";

    private int minEfficiencyLevel = DEFAULT_MIN_EFFICIENCY_LEVEL;
    private int minHasteAmplifier = DEFAULT_MIN_HASTE_AMPLIFIER;
    private boolean instantBreakEnabled = true;
    private boolean ladderEnabled = true;
    private boolean anvilRepairEnabled = true;
    private boolean joinMessagesEnabled = true;
    private boolean quitMessagesEnabled = true;
    private boolean lagNotificationsEnabled = true;
    private boolean lagBroadcastEnabled = true;
    private boolean lagOpDetailsEnabled = true;
    private boolean lagConsoleLoggingEnabled = true;
    private int minPlayersForLagWarning = DEFAULT_MIN_PLAYERS_FOR_LAG_WARNING;
    private double tpsWarningThreshold = DEFAULT_TPS_WARNING_THRESHOLD;
    private Set<Material> deepslateBlocks = DEFAULT_DEEPSLATE_BLOCKS;
    private Set<Material> glassBlocks = DEFAULT_GLASS_BLOCKS;
    private String joinMessage = DEFAULT_JOIN_MESSAGE;
    private String quitMessage = DEFAULT_QUIT_MESSAGE;
    private String anvilMessage = DEFAULT_ANVIL_MESSAGE;

    // Pitch detection for ladder placement
    private static final double DOWNWARD_PITCH_THRESHOLD = 0;

    // Deepslate blocks that can be instant-mined
    static final Set<Material> DEFAULT_DEEPSLATE_BLOCKS = Set.of(
            Material.DEEPSLATE,
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES,
            Material.COBBLED_DEEPSLATE,
            Material.POLISHED_DEEPSLATE,
            Material.CHISELED_DEEPSLATE,
            Material.CRACKED_DEEPSLATE_BRICKS,
            Material.CRACKED_DEEPSLATE_TILES,
            Material.DEEPSLATE_BRICK_SLAB,
            Material.DEEPSLATE_TILE_SLAB,
            Material.COBBLED_DEEPSLATE_SLAB,
            Material.POLISHED_DEEPSLATE_SLAB,
            Material.DEEPSLATE_BRICK_STAIRS,
            Material.DEEPSLATE_TILE_STAIRS,
            Material.COBBLED_DEEPSLATE_STAIRS,
            Material.POLISHED_DEEPSLATE_STAIRS,
            Material.DEEPSLATE_BRICK_WALL,
            Material.DEEPSLATE_TILE_WALL,
            Material.COBBLED_DEEPSLATE_WALL,
            Material.POLISHED_DEEPSLATE_WALL);

    // Glass blocks that can be instant-mined
    static final Set<Material> DEFAULT_GLASS_BLOCKS = Set.of(Material.GLASS, Material.GLASS_PANE,
            Material.WHITE_STAINED_GLASS, Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS, Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS, Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS, Material.CYAN_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS_PANE, Material.BROWN_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS, Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS, Material.RED_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS_PANE, Material.TINTED_GLASS);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MapExpansionTracker tracker;
    private SchedulerUtils.TaskHandle lagTask;
    private boolean timerStarted = false;
    private long lagCheckIntervalSeconds = 10;
    private volatile boolean running;
    private final NekoPlugin plugin;

    public ServerEventsListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new MapExpansionTracker(plugin);
    }

    public void configure(int minEfficiencyLevel, int minHasteAmplifier, boolean instantBreakEnabled,
                          boolean ladderEnabled, boolean anvilRepairEnabled, boolean joinMessagesEnabled,
                          boolean quitMessagesEnabled, boolean lagNotificationsEnabled, boolean lagBroadcastEnabled,
                          boolean lagOpDetailsEnabled, boolean lagConsoleLoggingEnabled, int minPlayersForLagWarning,
                          double tpsWarningThreshold, String joinMessage, String quitMessage, String anvilMessage) {
        setMinEfficiencyLevel(minEfficiencyLevel);
        setMinHasteAmplifier(minHasteAmplifier);
        this.instantBreakEnabled = instantBreakEnabled;
        this.ladderEnabled = ladderEnabled;
        this.anvilRepairEnabled = anvilRepairEnabled;
        this.joinMessagesEnabled = joinMessagesEnabled;
        this.quitMessagesEnabled = quitMessagesEnabled;
        this.lagNotificationsEnabled = lagNotificationsEnabled;
        this.lagBroadcastEnabled = lagBroadcastEnabled;
        this.lagOpDetailsEnabled = lagOpDetailsEnabled;
        this.lagConsoleLoggingEnabled = lagConsoleLoggingEnabled;
        setMinPlayersForLagWarning(minPlayersForLagWarning);
        setTpsWarningThreshold(tpsWarningThreshold);
        setJoinMessage(joinMessage);
        setQuitMessage(quitMessage);
        setAnvilMessage(anvilMessage);
    }

    public void setMinEfficiencyLevel(int value) {
        if (value < 1 || value > 10) throw new IllegalArgumentException("Efficiency must be between 1 and 10");
        minEfficiencyLevel = value;
    }

    public void setMinHasteAmplifier(int value) {
        if (value < 0 || value > 10) throw new IllegalArgumentException("Haste amplifier must be between 0 and 10");
        minHasteAmplifier = value;
    }

    public void setMinPlayersForLagWarning(int value) {
        if (value < 1 || value > 100) throw new IllegalArgumentException("Minimum players must be between 1 and 100");
        minPlayersForLagWarning = value;
        tracker.setMinPlayersForWarning(value);
    }

    public void setTpsWarningThreshold(double value) {
        if (!Double.isFinite(value) || value < 1.0 || value > 20.0) throw new IllegalArgumentException("TPS threshold must be between 1 and 20");
        tpsWarningThreshold = value;
        tracker.setTpsWarningThreshold(value);
    }

    public void setJoinMessage(String value) { joinMessage = validateMessage(value, "Join message"); }
    public void setQuitMessage(String value) { quitMessage = validateMessage(value, "Quit message"); }
    public void setAnvilMessage(String value) { anvilMessage = validateMessage(value, "Anvil message"); }
    public void setInstantBreakEnabled(boolean value) { instantBreakEnabled = value; }
    public void setLadderEnabled(boolean value) { ladderEnabled = value; }
    public void setAnvilRepairEnabled(boolean value) { anvilRepairEnabled = value; }
    public void setJoinMessagesEnabled(boolean value) { joinMessagesEnabled = value; }
    public void setQuitMessagesEnabled(boolean value) { quitMessagesEnabled = value; }
    public void setLagNotificationsEnabled(boolean value) { lagNotificationsEnabled = value; }
    public void setLagBroadcastEnabled(boolean value) { lagBroadcastEnabled = value; }
    public void setLagOpDetailsEnabled(boolean value) { lagOpDetailsEnabled = value; }
    public void setLagConsoleLoggingEnabled(boolean value) { lagConsoleLoggingEnabled = value; }
    public void setLagCheckIntervalSeconds(long seconds) {
        if (seconds < 1 || seconds > 60) throw new IllegalArgumentException("Lag check interval must be between 1 and 60 seconds");
        lagCheckIntervalSeconds = seconds;
        if (running && timerStarted) {
            SchedulerUtils.cancelTask(lagTask);
            lagTask = SchedulerUtils.runGlobalTimerTask(this::checkAndBroadcastLag, seconds * 20, seconds * 20);
        }
    }

    public void setDeepslateBlocks(List<Material> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("At least one deepslate material is required");
        if (!DEFAULT_DEEPSLATE_BLOCKS.containsAll(values)) throw new IllegalArgumentException("Unsupported deepslate material");
        deepslateBlocks = Set.copyOf(values);
    }

    public void setGlassBlocks(List<Material> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("At least one glass material is required");
        if (!DEFAULT_GLASS_BLOCKS.containsAll(values)) throw new IllegalArgumentException("Unsupported glass material");
        glassBlocks = Set.copyOf(values);
    }

    private String validateMessage(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256) throw new IllegalArgumentException(name + " must be 1-256 characters");
        return value;
    }

    public MapExpansionTracker getTracker() { return tracker; }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
        SchedulerUtils.cancelTask(lagTask);
        lagTask = null;
        timerStarted = false;
        tracker.stop();
    }

    // ========== Player Join/Quit Messages ==========

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!running) return;
        if (!joinMessagesEnabled) {
            event.joinMessage(null);
        } else {
            Component message = miniMessage.deserialize(joinMessage.replace("{player}", event.getPlayer().getName()));
            event.joinMessage(message);
        }

        // Start lag detection independently of message visibility.
        startLagDetectionTimer();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!running) return;
        if (!quitMessagesEnabled) {
            event.quitMessage(null);
            return;
        }
        Component message = miniMessage.deserialize(quitMessage.replace("{player}", event.getPlayer().getName()));
        event.quitMessage(message);
    }

    // ========== Instant Break ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!instantBreakEnabled) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        // Check for Netherite Pickaxe
        if (tool.getType() != Material.NETHERITE_PICKAXE)
            return;

        // Glass: just needs Netherite Pickaxe - drop the glass
        if (glassBlocks.contains(blockType)) {
            if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                event.setDropItems(false);
                event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(blockType));
            }
        }

        // Deepslate: needs Efficiency 5 + Haste 2
        // This event fires after the block is broken, so no action needed here
        // The instant break effect is handled in BlockDamageEvent
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!instantBreakEnabled) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        // Check for Netherite Pickaxe
        if (tool.getType() != Material.NETHERITE_PICKAXE)
            return;

        // Only deepslate blocks
        if (!deepslateBlocks.contains(blockType))
            return;

        // Check Efficiency 5
        int effLevel = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (effLevel < minEfficiencyLevel)
            return;

        // Check Haste 2
        var hasteEffect = player.getPotionEffect(PotionEffectType.HASTE);
        if (hasteEffect == null || hasteEffect.getAmplifier() < minHasteAmplifier)
            return; // Amplifier 1 = Haste 2

        // Instant break - set to insta-break mode
        event.setInstaBreak(true);
    }

    // ========== Ladder Auto-Placement ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onLadderInteract(PlayerInteractEvent event) {
        if (!ladderEnabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;

        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.LADDER)
            return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // Must be holding ladders
        if (held.getType() != Material.LADDER)
            return;

        // Determine direction based on player pitch
        BlockFace direction = player.getLocation().getPitch() > DOWNWARD_PITCH_THRESHOLD ? BlockFace.DOWN
                : BlockFace.UP;
        Block target = clicked.getRelative(direction);

        // Find the end of the ladder chain
        while (target.getType() == Material.LADDER) {
            target = target.getRelative(direction);
        }

        // Check if we can place a ladder
        if (target.getType() != Material.AIR)
            return;

        // Get ladder facing from the original clicked ladder
        if (!(clicked.getBlockData() instanceof Directional ladderData))
            return;
        BlockFace facing = ladderData.getFacing();

        // Check if there's a solid block behind where we want to place
        Block behind = target.getRelative(facing.getOppositeFace());
        if (!behind.getType().isSolid())
            return;

        // Place the ladder
        event.setCancelled(true);
        target.setType(Material.LADDER);
        Directional newLadderData = (Directional) target.getBlockData();
        newLadderData.setFacing(facing);
        target.setBlockData(newLadderData);

        // Consume item
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    // ========== Anvil Repair ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilRepair(PlayerInteractEvent event) {
        if (!anvilRepairEnabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;

        Block clicked = event.getClickedBlock();
        Material anvilType = clicked.getType();

        // Check if it's a damaged anvil
        if (anvilType != Material.CHIPPED_ANVIL && anvilType != Material.DAMAGED_ANVIL)
            return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // Must be holding Iron Block
        if (held.getType() != Material.IRON_BLOCK)
            return;

        // Repair the anvil
        event.setCancelled(true);

        Material repairedType = (anvilType == Material.DAMAGED_ANVIL)
                ? Material.CHIPPED_ANVIL
                : Material.ANVIL;

        clicked.setType(repairedType);

        // Consume iron block
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }

        player.sendMessage(miniMessage.deserialize(anvilMessage).colorIfAbsent(NamedTextColor.GREEN));
    }

    // ========== Map Expansion Lag Detection ==========

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (!running) return;
        // Record chunk generation for tracking
        tracker.recordChunkGeneration(event.getChunk());
    }

    /**
     * Starts the lag detection timer that checks every 10 seconds.
     */
    private void startLagDetectionTimer() {
        if (!running || timerStarted) return;
        timerStarted = true;
        long intervalTicks = lagCheckIntervalSeconds * 20L;
        lagTask = SchedulerUtils.runGlobalTimerTask(this::checkAndBroadcastLag, intervalTicks, intervalTicks);
    }

    /**
     * Checks lag conditions and broadcasts warning if needed.
     */
    private void checkAndBroadcastLag() {
        if (!running || !lagNotificationsEnabled) return;
        // Get current TPS
        double tps = ServerPerformanceUtils.getTPS();
        
        // Get online player count
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        
        // Check if we should notify
        if (!tracker.shouldNotify(tps, onlinePlayers)) {
            return;
        }
        
        // Get contributing players
        Map<UUID, Integer> contributingPlayers = tracker.getContributingPlayers();
        
        if (contributingPlayers.isEmpty()) {
            return;
        }
        
        // Sort players by chunk count (descending)
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(contributingPlayers.entrySet());
        sortedPlayers.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        
        // Build player list message
        StringBuilder playerListBuilder = new StringBuilder();
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : "Unknown";
            int chunkCount = entry.getValue();
            playerListBuilder.append("  • ").append(playerName).append(" (").append(chunkCount).append(" chunks)\n");
        }
        String playerList = playerListBuilder.toString().trim();
        
        // Format TPS with 1 decimal place
        DecimalFormat df = new DecimalFormat("#.#");
        String tpsFormatted = df.format(tps);
        
        // Build broadcast message
        String broadcastMessage =
            "⚠ Server Lag Detected - Map Expansion\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Current TPS: " + tpsFormatted + "\n" +
            "Players Exploring New Chunks:\n" +
            playerList + "\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Please pause exploration to reduce lag.\n" +
            "Next warning in " + tracker.getCooldownMinutes() + " minutes.";
        
        if (lagBroadcastEnabled) {
            Bukkit.broadcast(Component.text(broadcastMessage).color(NamedTextColor.RED));
        }

        if (lagOpDetailsEnabled) {
            sendOpDetailedMessage(tpsFormatted, sortedPlayers.size(), contributingPlayers.values().stream().mapToInt(Integer::intValue).sum());
        }

        if (lagConsoleLoggingEnabled) {
            logToConsole(tpsFormatted, sortedPlayers, contributingPlayers.values().stream().mapToInt(Integer::intValue).sum());
        }
        // Reset cooldown
        tracker.resetCooldown();
    }

    /**
     * Sends detailed debug information to OPs only.
     */
    private void sendOpDetailedMessage(String tps, int playerCount, int chunkCount) {
        String opMessage =
            "[MAP LAG DEBUG] Details:\n" +
            "- TPS: " + tps + " (threshold: " + tracker.getTpsWarningThreshold() + ")\n" +
            "- Chunks generated: " + chunkCount + " (last " + tracker.getTimeWindowSeconds() + "s)\n" +
            "- Active explorers: " + playerCount + " players\n" +
            "- Cooldown: " + tracker.getCooldownMinutes() + ":00";
        
        Component opComponent = Component.text(opMessage).color(NamedTextColor.GOLD);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(opComponent);
            }
        }
    }

    /**
     * Logs lag event to server console.
     */
    private void logToConsole(String tps, List<Map.Entry<UUID, Integer>> sortedPlayers, int chunkCount) {
        StringBuilder playerNames = new StringBuilder();
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (i > 0) playerNames.append(", ");
            Player player = Bukkit.getPlayer(sortedPlayers.get(i).getKey());
            playerNames.append(player != null ? player.getName() : "Unknown");
        }
        
        plugin.getLogger().info(String.format(
            "Map expansion lag detected: TPS %s, %d chunks, %d players (%s)",
            tps, chunkCount, sortedPlayers.size(), playerNames.toString()
        ));
    }
}
