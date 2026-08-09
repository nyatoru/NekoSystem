package com.nyarutoru.nekoplugin.features.curse;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Reverse-drowning curse: cursed players must stay in water.
 * On land for >25s they suffocate like drowning in reverse.
 */
public final class AquaCurseFeature extends AbstractFeature implements Listener {

    private static final String FILE_NAME = "aqua-curse.yml";
    private static final int GRACE_SECONDS = 25;
    private static final int GRACE_TICKS = GRACE_SECONDS * 20;
    private static final long TICK_PERIOD_TICKS = 20L;
    private static final double DAMAGE = 2.0;

    private final Set<UUID> cursed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> outTicks = new ConcurrentHashMap<>();
    private File file;

    public AquaCurseFeature() {
        super("aquacurse", "Aqua Curse");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        file = new File(plugin.getDataFolder(), FILE_NAME);
        load();
        ownTask(SchedulerUtils.runGlobalTimerTask(this::tick, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS));
        registerListener(this, plugin);
        super.onEnable(plugin);
    }

    @Override
    protected void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (cursed.contains(player.getUniqueId())) {
                clearCurseEffects(player);
                if (player.getRemainingAir() != player.getMaximumAir()) {
                    player.setRemainingAir(player.getMaximumAir());
                }
            }
        }
        outTicks.clear();
        save();
    }

    public boolean isCursed(UUID id) {
        return cursed.contains(id);
    }

    public Set<UUID> getCursedCopy() {
        return Set.copyOf(cursed);
    }

    public int getOutTicks(UUID id) {
        return outTicks.getOrDefault(id, 0);
    }

    public void setCursed(UUID id, boolean value) {
        if (value) {
            cursed.add(id);
        } else {
            cursed.remove(id);
            outTicks.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                SchedulerUtils.runAtEntity(online, () -> {
                    if (online.isOnline()) {
                        online.setRemainingAir(online.getMaximumAir());
                        clearCurseEffects(online);
                    }
                });
            }
        }
        save();
    }

    private void load() {
        if (file == null || !file.exists()) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            List<String> list = yaml.getStringList("cursed");
            for (String raw : list) {
                try {
                    cursed.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception exception) {
            NekoPlugin.getInstance().getLogger().log(Level.WARNING, "Could not load aqua curse data", exception);
        }
    }

    private void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("cursed", cursed.stream().map(UUID::toString).toList());
            yaml.save(file);
        } catch (IOException exception) {
            NekoPlugin plugin = NekoPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Could not save aqua curse data", exception);
            }
        }
    }

    private void tick() {
        if (cursed.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!cursed.contains(id)) continue;
            SchedulerUtils.runAtEntity(player, () -> handlePlayer(player));
        }
    }

    private void handlePlayer(Player player) {
        UUID id = player.getUniqueId();
        if (!cursed.contains(id)) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            outTicks.remove(id);
            if (player.getRemainingAir() != player.getMaximumAir()) {
                player.setRemainingAir(player.getMaximumAir());
            }
            clearCurseEffects(player);
            return;
        }

        if (isInWater(player)) {
            Integer previous = outTicks.remove(id);
            if (previous != null && previous > 0) {
                player.setRemainingAir(player.getMaximumAir());
            } else if (player.getRemainingAir() != player.getMaximumAir()) {
                player.setRemainingAir(player.getMaximumAir());
            }
            applyWaterEffects(player);
            return;
        }

        applyLandEffects(player);
        int ticks = outTicks.getOrDefault(id, 0) + (int) TICK_PERIOD_TICKS;
        outTicks.put(id, ticks);

        if (ticks >= GRACE_TICKS) {
            player.setRemainingAir(0);
            player.damage(DAMAGE);
        } else {
            int max = player.getMaximumAir();
            int remaining = max - (int) ((long) max * ticks / GRACE_TICKS);
            remaining = Math.max(0, Math.min(max, remaining));
            player.setRemainingAir(remaining);
        }
    }

    private static boolean isInWater(Player player) {
        try {
            if (player.isInWater()) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (player.isSwimming()) return true;
        } catch (Throwable ignored) {
        }
        Material feet = player.getLocation().getBlock().getType();
        if (feet == Material.WATER || feet == Material.BUBBLE_COLUMN) return true;
        Material eye = player.getEyeLocation().getBlock().getType();
        return eye == Material.WATER || eye == Material.BUBBLE_COLUMN;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!cursed.contains(player.getUniqueId())) return;
        outTicks.remove(player.getUniqueId());
        SchedulerUtils.runAtEntity(player, () -> {
            if (cursed.contains(player.getUniqueId())) {
                player.setRemainingAir(player.getMaximumAir());
                // effects will be applied on next tick based on water check
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        outTicks.remove(event.getPlayer().getUniqueId());
        // clear effects proactively (player entity will despawn, but keep clean)
        clearCurseEffects(event.getPlayer());
    }

    private static void applyWaterEffects(Player player) {
        // Heart of the Sea while in water -> Conduit Power + Dolphin's Grace, clear mining fatigue
        // 300 ticks (15s) + refresh every 20 ticks keeps remaining >200 ticks so icon never flashes (<10s flashes)
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 300, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 300, 0, false, false, true));
    }

    private static void applyLandEffects(Player player) {
        // Slows mining when not in water
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 1, false, false, true));
    }

    private static void clearCurseEffects(Player player) {
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        // legacy clean-up if HASTE was given before
        player.removePotionEffect(PotionEffectType.HASTE);
    }
}
