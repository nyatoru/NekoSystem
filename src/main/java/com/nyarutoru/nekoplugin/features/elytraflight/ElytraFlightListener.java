package com.nyarutoru.nekoplugin.features.elytraflight;

import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sneak while holding an ender pearl with an elytra equipped to toggle fueled flight:
 * 1 ender pearl per pearlSeconds of gliding, elytra wears durabilityMultiplier times faster.
 */
public final class ElytraFlightListener implements Listener {

    private final Map<UUID, Integer> sneaks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> flightSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, SchedulerUtils.TaskHandle> tasks = new ConcurrentHashMap<>();

    private int pearlSeconds = 6;
    private int durabilityMultiplier = 3;
    private int shiftCount = 10;

    public void setPearlSeconds(int pearlSeconds) {
        this.pearlSeconds = pearlSeconds;
    }

    public void setDurabilityMultiplier(int durabilityMultiplier) {
        this.durabilityMultiplier = durabilityMultiplier;
    }

    public void setShiftCount(int shiftCount) {
        this.shiftCount = shiftCount;
    }

    void shutdown() {
        tasks.values().forEach(SchedulerUtils::cancelTask);
        tasks.clear();
        sneaks.clear();
        flightSeconds.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isHoldingPearl(player) || !isWearingElytra(player)) {
            sneaks.remove(player.getUniqueId());
            return;
        }
        int count = sneaks.merge(player.getUniqueId(), 1, Integer::sum);
        if (count < shiftCount) return;
        sneaks.remove(player.getUniqueId());
        toggle(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        deactivate(event.getPlayer());
        sneaks.remove(event.getPlayer().getUniqueId());
    }

    private void toggle(Player player) {
        UUID id = player.getUniqueId();
        if (tasks.containsKey(id)) {
            deactivate(player);
            return;
        }
        tasks.put(id, SchedulerUtils.runAtPlayerTimerTask(player, () -> tick(player), 20, 20));
        send(player, "Fueled elytra flight enabled: 1 ender pearl per " + pearlSeconds
                + "s, elytra wears " + durabilityMultiplier + "x faster.", NamedTextColor.GREEN);
    }

    private void deactivate(Player player) {
        UUID id = player.getUniqueId();
        SchedulerUtils.TaskHandle task = tasks.remove(id);
        SchedulerUtils.cancelTask(task);
        flightSeconds.remove(id);
        if (task != null) {
            send(player, "Fueled elytra flight disabled.", NamedTextColor.GRAY);
        }
    }

    private void tick(Player player) {
        if (!tasks.containsKey(player.getUniqueId())) return;
        if (!isWearingElytra(player)) {
            deactivate(player);
            send(player, "Elytra removed - fueled flight disabled.", NamedTextColor.RED);
            return;
        }
        if (!player.isGliding()) return;
        int seconds = flightSeconds.merge(player.getUniqueId(), 1, Integer::sum);
        if (seconds >= pearlSeconds) {
            if (!consumePearl(player)) {
                deactivate(player);
                send(player, "Out of ender pearls - fueled flight disabled.", NamedTextColor.RED);
                return;
            }
            flightSeconds.put(player.getUniqueId(), 0);
        }
        drainElytra(player);
    }

    private boolean consumePearl(Player player) {
        ItemStack pearl = new ItemStack(Material.ENDER_PEARL, 1);
        if (!player.getInventory().containsAtLeast(pearl, 1)) return false;
        player.getInventory().removeItem(pearl);
        return true;
    }

    private void drainElytra(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return;
        // Vanilla already drains 1 durability per second of gliding; add the rest.
        ItemUtils.applyDurabilityDamage(chest, durabilityMultiplier - 1);
        if (ItemUtils.getRemainingDurability(chest) <= 0) {
            player.getInventory().setChestplate(null);
            deactivate(player);
            send(player, "Your elytra broke!", NamedTextColor.RED);
        }
    }

    private static boolean isHoldingPearl(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.ENDER_PEARL;
    }

    private static boolean isWearingElytra(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && chest.getType() == Material.ELYTRA;
    }

    private static void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }
}
