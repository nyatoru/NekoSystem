package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class GraveDisplayManager {
    private static final double DISPLAY_HEIGHT = 1.6;
    private static final double SEARCH_RADIUS = 2.0;
    private static final long UPDATE_INTERVAL_TICKS = 20L;

    private final GraveManager graveManager;
    private final NamespacedKey displayKey;
    private final NamespacedKey graveIdKey;
    private final Map<UUID, TextDisplay> displays = new ConcurrentHashMap<>();
    private final NekoPlugin plugin;
    private BukkitTask paperTask;
    private ScheduledTask foliaTask;

    GraveDisplayManager(NekoPlugin plugin, GraveManager graveManager) {
        this.plugin = plugin;
        this.graveManager = graveManager;
        displayKey = new NamespacedKey(plugin, "grave_display");
        graveIdKey = new NamespacedKey(plugin, "grave_id");
    }

    void start() {
        if (SchedulerUtils.isFolia()) {
            foliaTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> updateDisplays(), UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
        } else {
            paperTask = SchedulerUtils.runGlobalTimer(this::updateDisplays, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
        }
    }

    void stop() {
        SchedulerUtils.cancelTask(paperTask);
        if (foliaTask != null) foliaTask.cancel();
        displays.clear();
    }

    void reconcile(Grave grave, Location markerLocation) {
        if (grave.getState() != Grave.State.ACTIVE) return;
        Location displayLocation = displayLocation(markerLocation);
        Collection<TextDisplay> nearby = markerLocation.getWorld().getNearbyEntitiesByType(
            TextDisplay.class, displayLocation, SEARCH_RADIUS, this::isGraveDisplay);
        TextDisplay retained = null;
        for (TextDisplay display : nearby) {
            UUID displayGraveId = graveId(display);
            if (displayGraveId == null) {
                display.remove();
                continue;
            }
            if (!grave.getId().equals(displayGraveId)) continue;
            if (retained != null) {
                display.remove();
                continue;
            }
            retained = display;
        }
        if (retained == null) {
            retained = markerLocation.getWorld().spawn(displayLocation, TextDisplay.class,
                display -> configure(display, grave));
        } else {
            configure(retained, grave);
            if (retained.getLocation().distanceSquared(displayLocation) > 0.01) retained.teleport(displayLocation);
        }
        displays.put(grave.getId(), retained);
    }

    void reconcileLoaded(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (!(entity instanceof TextDisplay display) || !isGraveDisplay(display)) continue;
            UUID graveId = graveId(display);
            Grave grave = graveId == null ? null : graveManager.get(graveId);
            if (grave == null || grave.getState() != Grave.State.ACTIVE) {
                display.remove();
                continue;
            }
            Location markerLocation = grave.getGravePosition().resolve(display.getServer());
            if (markerLocation == null || markerLocation.getWorld() != display.getWorld()
                || display.getLocation().distanceSquared(displayLocation(markerLocation)) > SEARCH_RADIUS * SEARCH_RADIUS) {
                display.remove();
                continue;
            }
            TextDisplay existing = displays.putIfAbsent(graveId, display);
            if (existing != null && existing != display && existing.isValid()) {
                display.remove();
                continue;
            }
            configure(display, grave);
        }
    }

    void unloaded(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof TextDisplay display) {
                UUID graveId = graveId(display);
                if (graveId != null) displays.remove(graveId, display);
            }
        }
    }

    void remove(Grave grave, Location markerLocation) {
        TextDisplay tracked = displays.remove(grave.getId());
        if (tracked != null && tracked.isValid()) tracked.remove();
        Location displayLocation = displayLocation(markerLocation);
        markerLocation.getWorld().getNearbyEntitiesByType(TextDisplay.class, displayLocation, SEARCH_RADIUS,
            display -> grave.getId().equals(graveId(display))).forEach(Entity::remove);
    }

    private void updateDisplays() {
        long now = System.currentTimeMillis();
        displays.forEach((graveId, display) -> {
            Grave grave = graveManager.get(graveId);
            if (grave == null || grave.getState() != Grave.State.ACTIVE || !display.isValid()) {
                displays.remove(graveId, display);
                return;
            }
            SchedulerUtils.runAtEntity(display, () -> display.text(text(grave, now)));
        });
    }

    private void configure(TextDisplay display, Grave grave) {
        display.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(graveIdKey, PersistentDataType.STRING, grave.getId().toString());
        display.text(text(grave, System.currentTimeMillis()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setDefaultBackground(true);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setLineWidth(200);
        display.setViewRange(32.0F);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(true);
    }

    private boolean isGraveDisplay(TextDisplay display) {
        return display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE);
    }

    private UUID graveId(TextDisplay display) {
        String value = display.getPersistentDataContainer().get(graveIdKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Component text(Grave grave, long now) {
        return Component.text("Grave of " + grave.getOwnerName(), NamedTextColor.GOLD)
            .append(Component.newline())
            .append(Component.text(grave.getStackCount() + " item stacks", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Expires in " + formatRemaining(grave.getRemainingMillis(now)), NamedTextColor.YELLOW));
    }

    static String formatRemaining(long milliseconds) {
        long totalSeconds = Math.max(0L, (milliseconds + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }

    private static Location displayLocation(Location markerLocation) {
        return markerLocation.clone().add(0.5, DISPLAY_HEIGHT, 0.5);
    }
}
