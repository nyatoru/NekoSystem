package com.nyarutoru.nekoplugin.features.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sneak while holding obsidian to see the Overworld/Nether coordinate conversion. */
public final class NetherCoordListener implements Listener {

    private static final int SCALE = 8;

    private int shiftCount = 10;
    private final Map<UUID, Integer> sneaks = new ConcurrentHashMap<>();

    public void setShiftCount(int shiftCount) {
        this.shiftCount = shiftCount;
    }

    void resetSneaks() {
        sneaks.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.OBSIDIAN) {
            sneaks.remove(player.getUniqueId());
            return;
        }
        int count = sneaks.merge(player.getUniqueId(), 1, Integer::sum);
        if (count < shiftCount) return;
        sneaks.remove(player.getUniqueId());
        showConversion(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sneaks.remove(event.getPlayer().getUniqueId());
    }

    private void showConversion(Player player) {
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            send(player, String.format("Overworld: %d, %d, %d  (from Nether %d, %d, %d)",
                    overworldX(x), y, overworldZ(z), x, y, z), NamedTextColor.GREEN);
        } else if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
            send(player, String.format("Nether: %d, %d, %d  (from Overworld %d, %d, %d)",
                    netherX(x), y, netherZ(z), x, y, z), NamedTextColor.GREEN);
        } else {
            send(player, "Coordinate conversion only works in the Overworld or the Nether.", NamedTextColor.RED);
        }
    }

    static int netherX(int x) {
        return x / SCALE;
    }

    static int netherZ(int z) {
        return z / SCALE;
    }

    static int overworldX(int x) {
        return x * SCALE;
    }

    static int overworldZ(int z) {
        return z * SCALE;
    }

    private static void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }
}
