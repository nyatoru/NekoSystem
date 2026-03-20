package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Handles grave-related events including death, grave interaction, and griefing prevention.
 */
public class GraveListener implements Listener {

    private final NekoPlugin plugin;
    private final GraveManager graveManager;

    public GraveListener(NekoPlugin plugin, GraveManager graveManager) {
        this.plugin = plugin;
        this.graveManager = graveManager;
    }

    /**
     * Creates a grave when a player dies.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        
        // Check if graves are enabled in this world
        if (!isGravesEnabledInWorld(world)) {
            return;
        }
        
        // Collect items to store in grave
        List<ItemStack> itemsToStore = new ArrayList<>();
        for (ItemStack item : event.getDrops()) {
            if (item != null && !item.getType().isAir()) {
                itemsToStore.add(item.clone());
            }
        }
        
        // Clear drops (items will be stored in grave)
        event.getDrops().clear();
        
        // Create the grave
        Location deathLocation = player.getLocation();
        Grave grave = graveManager.createGrave(player, deathLocation, itemsToStore);
        
        if (grave != null) {
            // Send death message with coordinates
            sendDeathMessage(player, grave);
            
            // Spawn cosmetics
            if (GraveConfig.SPAWN_PARTICLES_ON_CREATE) {
                spawnGraveParticles(grave.getGraveLocation());
            }
            if (GraveConfig.PLAY_SOUND_ON_CREATE) {
                playGraveSound(grave.getGraveLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.0f);
            }
            
            plugin.getLogger().info("Created grave for " + player.getName() + " at " + 
                formatLocation(grave.getGraveLocation()));
        }
    }

    /**
     * Handles grave interaction (right-click to retrieve items).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PLAYER_HEAD) {
            return;
        }
        
        Location graveLocation = block.getLocation();
        Grave grave = graveManager.getGrave(graveLocation);
        
        if (grave == null) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player can access this grave
        if (!graveManager.canAccessGrave(player, grave)) {
            player.sendMessage(Component.text("You cannot access this grave!")
                .color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }
        
        // Open grave GUI or retrieve items
        openGraveGUI(player, grave);
        event.setCancelled(true);
    }

    /**
     * Prevents griefing of graves.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!GraveConfig.INDESTRUCTIBLE_GRAVES) {
            return;
        }
        
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD) {
            return;
        }
        
        Location graveLocation = block.getLocation();
        Grave grave = graveManager.getGrave(graveLocation);
        
        if (grave == null) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Allow grave owner or OP to break
        if (graveManager.canAccessGrave(player, grave)) {
            return;
        }
        
        // Prevent griefing
        player.sendMessage(Component.text("You cannot break this grave!")
            .color(NamedTextColor.RED));
        event.setCancelled(true);
    }

    /**
     * Opens a GUI for players to retrieve items from grave.
     */
    private void openGraveGUI(Player player, Grave grave) {
        // Mark grave as accessed
        grave.markAccessed();
        
        // Give items to player
        World world = player.getWorld();
        int itemsGiven = 0;
        
        for (ItemStack item : grave.getItems()) {
            if (item != null && !item.getType().isAir()) {
                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                if (remaining.isEmpty()) {
                    itemsGiven++;
                }
            }
        }
        
        // Drop remaining items if inventory is full
        if (itemsGiven < grave.getItemCount()) {
            player.sendMessage(Component.text("Your inventory is full! Some items dropped on the ground.")
                .color(NamedTextColor.YELLOW));
        }
        
        // Remove grave after retrieval
        graveManager.removeGrave(grave, false);
        
        // Send confirmation message
        player.sendMessage(Component.text("You retrieved your items from your grave.")
            .color(NamedTextColor.GREEN));
        
        // Spawn cosmetics
        if (GraveConfig.SPAWN_PARTICLES_ON_RETRIEVE) {
            spawnGraveParticles(grave.getGraveLocation());
        }
        if (GraveConfig.PLAY_SOUND_ON_RETRIEVE) {
            playGraveSound(grave.getGraveLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
        
        plugin.getLogger().fine(player.getName() + " retrieved grave with " + itemsGiven + " items");
    }

    /**
     * Sends death message with grave coordinates.
     */
    private void sendDeathMessage(Player player, Grave grave) {
        if (GraveConfig.BROADCAST_DEATH_MESSAGES) {
            // Broadcast to all players
            Component deathMsg = Component.text(player.getName() + " died and was buried at ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(formatLocation(grave.getGraveLocation()))
                    .color(NamedTextColor.GREEN));
            plugin.getServer().broadcast(deathMsg);
        } else if (GraveConfig.SHOW_DEATH_COORDINATES) {
            // Send only to deceased player
            Component deathMsg = Component.text("You died and were buried at ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(formatLocation(grave.getGraveLocation()))
                    .color(NamedTextColor.GREEN))
                .append(Component.text("\nRight-click your grave to retrieve items.")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(deathMsg);
        }
    }

    /**
     * Spawns grave creation particles.
     */
    private void spawnGraveParticles(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        
        world.spawnParticle(
            Particle.SOUL,
            location.clone().add(0.5, 0.5, 0.5),
            20,
            0.5, 0.5, 0.5,
            0.02
        );
    }

    /**
     * Plays a sound at a location.
     */
    private void playGraveSound(Location location, Sound sound, float volume, float pitch) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        
        world.playSound(location, sound, volume, pitch);
    }

    /**
     * Formats a location as a string.
     */
    private String formatLocation(Location location) {
        return String.format("%s (%d, %d, %d)",
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }

    /**
     * Checks if graves are enabled in a world.
     */
    private boolean isGravesEnabledInWorld(World world) {
        if (!GraveConfig.GRAVES_IN_ALL_WORLDS) {
            return false;
        }
        
        String worldName = world.getName();
        for (String disabledWorld : GraveConfig.GRAVE_DISABLED_WORLDS) {
            if (worldName.equals(disabledWorld)) {
                return false;
            }
        }
        
        return true;
    }
}
