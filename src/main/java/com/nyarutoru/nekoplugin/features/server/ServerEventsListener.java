package com.nyarutoru.nekoplugin.features.server;

import com.nyarutoru.nekoplugin.NekoPlugin;
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
import java.util.concurrent.TimeUnit;

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

    // Instant mining requirements
    private static final int MIN_EFFICIENCY_LEVEL = 5;
    private static final int MIN_HASTE_AMPLIFIER = 1;

    // Lag notification constants
    private static final int MIN_PLAYERS_FOR_LAG_WARNING = 2;
    private static final double TPS_WARNING_THRESHOLD = 18.0;

    // Pitch detection for ladder placement
    private static final double DOWNWARD_PITCH_THRESHOLD = 0;

    // Deepslate blocks that can be instant-mined
    private static final Set<Material> DEEPSLATE_BLOCKS = Set.of(
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
    private static final Set<Material> GLASS_BLOCKS = Set.of(Material.GLASS, Material.GLASS_PANE,
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
    private boolean timerStarted = false;
    private final NekoPlugin plugin;

    public ServerEventsListener(NekoPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new MapExpansionTracker(plugin);
    }

    // ========== Player Join/Quit Messages ==========

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String processedMessage = "<green><bold>+</bold> <gray>" + event.getPlayer().getName() + " joined the server.";
        Component message = miniMessage.deserialize(processedMessage);
        event.joinMessage(message);
        
        // Start lag detection timer on first player join
        startLagDetectionTimer();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String processedMessage = "<red><bold>-</bold> <gray>" + event.getPlayer().getName() + " left the server.";
        Component message = miniMessage.deserialize(processedMessage);
        event.quitMessage(message);
    }

    // ========== Instant Break ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        // Check for Netherite Pickaxe
        if (tool.getType() != Material.NETHERITE_PICKAXE)
            return;

        // Glass: just needs Netherite Pickaxe - drop the glass
        if (GLASS_BLOCKS.contains(blockType)) {
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
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material blockType = event.getBlock().getType();

        // Check for Netherite Pickaxe
        if (tool.getType() != Material.NETHERITE_PICKAXE)
            return;

        // Only deepslate blocks
        if (!DEEPSLATE_BLOCKS.contains(blockType))
            return;

        // Check Efficiency 5
        int effLevel = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (effLevel < MIN_EFFICIENCY_LEVEL)
            return;

        // Check Haste 2
        var hasteEffect = player.getPotionEffect(PotionEffectType.HASTE);
        if (hasteEffect == null || hasteEffect.getAmplifier() < MIN_HASTE_AMPLIFIER)
            return; // Amplifier 1 = Haste 2

        // Instant break - set to insta-break mode
        event.setInstaBreak(true);
    }

    // ========== Ladder Auto-Placement ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onLadderInteract(PlayerInteractEvent event) {
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

        player.sendMessage(Component.text("✓ Anvil repaired!")
                .color(NamedTextColor.GREEN));
    }

    // ========== Map Expansion Lag Detection ==========

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        // Record chunk generation for tracking
        tracker.recordChunkGeneration(event.getChunk());
    }

    /**
     * Starts the lag detection timer that checks every 10 seconds.
     */
    private void startLagDetectionTimer() {
        if (!timerStarted) {
            timerStarted = true;
            // Run every 10 seconds (200 ticks)
            plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> checkAndBroadcastLag(),
                200L, // 10 seconds initial delay
                200L  // 10 seconds interval
            );
        }
    }

    /**
     * Checks lag conditions and broadcasts warning if needed.
     */
    private void checkAndBroadcastLag() {
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
        
        // Get time until next notification
        long timeUntilNext = TimeUnit.MILLISECONDS.toMinutes(tracker.getTimeUntilNextNotification());
        
        // Build broadcast message
        String broadcastMessage = 
            "⚠ Server Lag Detected - Map Expansion\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Current TPS: " + tpsFormatted + "\n" +
            "Players Exploring New Chunks:\n" +
            playerList + "\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Please pause exploration to reduce lag.\n" +
            "Next warning in 5 minutes.";
        
        // Broadcast to all players
        Bukkit.broadcast(Component.text(broadcastMessage).color(NamedTextColor.RED));
        
        // Send detailed message to OPs
        sendOpDetailedMessage(tpsFormatted, sortedPlayers.size(), contributingPlayers.values().stream().mapToInt(Integer::intValue).sum());
        
        // Log to console
        logToConsole(tpsFormatted, sortedPlayers, contributingPlayers.values().stream().mapToInt(Integer::intValue).sum());
        
        // Reset cooldown
        tracker.resetCooldown();
    }

    /**
     * Sends detailed debug information to OPs only.
     */
    private void sendOpDetailedMessage(String tps, int playerCount, int chunkCount) {
        String opMessage = 
            "[MAP LAG DEBUG] Details:\n" +
            "- TPS: " + tps + " (threshold: 18.0)\n" +
            "- Chunks generated: " + chunkCount + " (last 10s)\n" +
            "- Active explorers: " + playerCount + " players\n" +
            "- Cooldown resets in: 5:00";
        
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
