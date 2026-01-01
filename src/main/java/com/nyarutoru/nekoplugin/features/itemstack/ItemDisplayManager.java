package com.nyarutoru.nekoplugin.features.itemstack;

import com.nyarutoru.nekoplugin.features.itemstack.data.StackedItemEntity;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages visual displays for stacked items.
 */
public class ItemDisplayManager {

    private static final int DISPLAY_RADIUS = 64; // Only show within 64 blocks
    private static final long UPDATE_INTERVAL_TICKS = 20L; // Update every second

    private final Map<UUID, TextDisplay> displays = new ConcurrentHashMap<>();

    /**
     * Start the display update task.
     */
    public void start() {
        SchedulerUtils.runGlobalTimer(this::updateAllDisplays, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    /**
     * Create a display for a stacked item.
     */
    public void createDisplay(StackedItemEntity stack) {
        if (stack.getEntityReference() == null || stack.getEntityReference().isDead()) {
            return;
        }

        Location loc = stack.getLocation().clone().add(0, 0.5, 0); // Above item

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, textDisplay -> {
            textDisplay.text(createDisplayText(stack));
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(false);
            textDisplay.setViewRange(DISPLAY_RADIUS / 16f);
        });

        displays.put(stack.getId(), display);
    }

    /**
     * Update display for a stack.
     */
    public void updateDisplay(StackedItemEntity stack) {
        TextDisplay display = displays.get(stack.getId());

        if (display != null && !display.isDead()) {
            display.text(createDisplayText(stack));

            // Update location if stack moved
            if (stack.getEntityReference() != null) {
                Location newLoc = stack.getLocation().clone().add(0, 0.5, 0);
                display.teleport(newLoc);
            }
        } else {
            // Display missing or dead, recreate
            createDisplay(stack);
        }
    }

    /**
     * Remove display for a stack.
     */
    public void removeDisplay(UUID stackId) {
        TextDisplay display = displays.remove(stackId);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    /**
     * Update all displays (visibility checks).
     */
    private void updateAllDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Map.Entry<UUID, TextDisplay> entry : displays.entrySet()) {
                TextDisplay display = entry.getValue();

                if (display.isDead()) {
                    continue;
                }

                Location displayLoc = display.getLocation();

                // Distance check
                if (player.getWorld() != displayLoc.getWorld() ||
                        player.getLocation().distance(displayLoc) > DISPLAY_RADIUS) {
                    continue;
                }

                // Line-of-sight check
                boolean canSee = hasLineOfSight(player, displayLoc);

                // Update visibility for this player
                if (canSee) {
                    player.showEntity(Bukkit.getPluginManager().getPlugins()[0], display);
                } else {
                    player.hideEntity(Bukkit.getPluginManager().getPlugins()[0], display);
                }
            }
        }
    }

    /**
     * Check if player has line of sight to the location.
     */
    private boolean hasLineOfSight(Player player, Location target) {
        Location eyeLoc = player.getEyeLocation();

        // Raytrace from player eyes to display
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eyeLoc,
                target.toVector().subtract(eyeLoc.toVector()).normalize(),
                eyeLoc.distance(target),
                org.bukkit.FluidCollisionMode.NEVER,
                true);

        // If raytrace hits a block, player cannot see it
        return result == null || result.getHitBlock() == null;
    }

    /**
     * Create display text component.
     */
    private Component createDisplayText(StackedItemEntity stack) {
        String itemName = formatMaterialName(stack.getItemType().name());
        String countText = formatCount(stack.getStackSize());

        return Component.text(itemName)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" x" + countText)
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Format material name to be human-readable.
     */
    private String formatMaterialName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }

        return result.toString();
    }

    /**
     * Format count with comma separators for large numbers.
     */
    private String formatCount(int count) {
        return String.format("%,d", count);
    }

    /**
     * Cleanup all displays.
     */
    public void shutdown() {
        for (TextDisplay display : displays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        displays.clear();
    }
}
