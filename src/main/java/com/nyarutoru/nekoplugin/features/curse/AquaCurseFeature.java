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
    private static final long TICK_PERIOD_TICKS = 10L;
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
            // Paper 1.19+ - covers water + bubble column in one check
            if (player.isInWaterOrBubbleColumn()) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (player.isSwimming()) return true;
        } catch (Throwable ignored) {
        }
        try {
            org.bukkit.block.Block feet = player.getLocation().getBlock();
            if (feet.isLiquid()) return true;
            Material feetType = feet.getType();
            if (feetType == Material.WATER || feetType == Material.BUBBLE_COLUMN) return true;
            if (feet.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return true;
            org.bukkit.block.Block eye = player.getEyeLocation().getBlock();
            if (eye.isLiquid()) return true;
            Material eyeType = eye.getType();
            if (eyeType == Material.WATER || eyeType == Material.BUBBLE_COLUMN) return true;
            if (eye.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl2 && wl2.isWaterlogged()) return true;
        } catch (Throwable ignored) {
        }
        return false;
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

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!cursed.contains(player.getUniqueId())) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        // Instant correction when starting to mine: ensure Haste/AquaAffinity present before break speed is calculated
        SchedulerUtils.runAtEntity(player, () -> {
            if (!cursed.contains(player.getUniqueId())) return;
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
            if (isInWater(player)) {
                // Remove fatigue immediately so first block doesn't feel slow
                if (player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
                    player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                }
                if (!player.hasPotionEffect(PotionEffectType.HASTE)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 300, 1, false, false, true));
                }
                if (!player.hasPotionEffect(PotionEffectType.CONDUIT_POWER)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 300, 0, false, false, true));
                }
                applyAquaAffinity(player);
            }
        });
    }

    private static void applyWaterEffects(Player player) {
        // Heart of the Sea while in water -> Conduit Power + Dolphin's Grace + Haste, clear mining fatigue
        // 300 ticks (15s) + refresh every 20 ticks keeps remaining >200 ticks so icon never flashes (<10s flashes)
        // Haste II counters vanilla underwater mining penalty (5x slower without Aqua Affinity) + floating penalty
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 300, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 300, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 300, 1, false, false, true));
        applyAquaAffinity(player);
    }

    private static void applyLandEffects(Player player) {
        // Slows mining when not in water
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffectType.HASTE);
        removeAquaAffinity(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 1, false, false, true));
    }

    private static void clearCurseEffects(Player player) {
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        // legacy clean-up if HASTE was given before
        player.removePotionEffect(PotionEffectType.HASTE);
        removeAquaAffinity(player);
    }

    private static final java.util.UUID SUBMERGED_MODIFIER_UUID =
            java.util.UUID.fromString("a7c2f1a0-5b3e-4e2a-9c1d-0f4a8e9b6c2d");

    private static void applyAquaAffinity(Player player) {
        // Try to negate vanilla submerged penalty via attribute (Paper 1.21.4+).
        // Falls back to HASTE already applied above if attribute is unavailable.
        try {
            // Reflect to avoid hard compile dependency on attribute name which varies across mappings
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attribute = null;
            for (String name : new String[]{"SUBMERGED_MINING_SPEED", "PLAYER_SUBMERGED_MINING_SPEED", "GENERIC_SUBMERGED_MINING_SPEED"}) {
                try {
                    @SuppressWarnings("unchecked")
                    Object v = Enum.valueOf((Class<Enum>) attributeClass, name);
                    attribute = v;
                    break;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (attribute == null) return;

            java.lang.reflect.Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);
            Object instance = getAttribute.invoke(player, attribute);
            if (instance == null) return;

            Class<?> instanceClass = instance.getClass();
            // check existing modifier by UUID/key to avoid duplicates
            try {
                java.lang.reflect.Method getModifiers = instanceClass.getMethod("getModifiers");
                @SuppressWarnings("unchecked")
                java.util.Collection<?> mods = (java.util.Collection<?>) getModifiers.invoke(instance);
                for (Object mod : mods) {
                    try {
                        java.lang.reflect.Method getUniqueId = mod.getClass().getMethod("getUniqueId");
                        java.util.UUID uid = (java.util.UUID) getUniqueId.invoke(mod);
                        if (SUBMERGED_MODIFIER_UUID.equals(uid)) return; // already applied
                    } catch (Throwable ignored) {
                    }
                    try {
                        // Paper modern uses NamespacedKey; check key string contains aqua_curse
                        java.lang.reflect.Method getKey = mod.getClass().getMethod("getKey");
                        Object key = getKey.invoke(mod);
                        if (key != null && key.toString().contains("aqua_curse")) return;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }

            // Build modifier: try modern (NamespacedKey, amount, operation) then legacy (UUID, name, amount, operation)
            Class<?> modifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
            Class<?> operationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
            Object operation = Enum.valueOf((Class<Enum>) operationClass, "ADD_NUMBER");
            // some mappings use ADD_SCALAR; prefer ADD_NUMBER
            Object modifier = null;
            try {
                Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
                java.lang.reflect.Method minecraft = keyClass.getMethod("minecraft", String.class);
                // try with 3-arg constructor (key, amount, operation) variant
                Object key = minecraft.invoke(null, "aqua_curse_submerged");
                try {
                    java.lang.reflect.Constructor<?> ctor = modifierClass.getConstructor(keyClass, double.class, operationClass);
                    modifier = ctor.newInstance(key, 5.0, operation);
                } catch (NoSuchMethodException e) {
                    // try (NamespacedKey, amount, operation, EquipmentSlotGroup) variant? ignore
                    throw e;
                }
            } catch (Throwable ignored) {
                // legacy fallback: UUID, name, amount, operation
                try {
                    java.lang.reflect.Constructor<?> ctor = modifierClass.getConstructor(java.util.UUID.class, String.class, double.class, operationClass);
                    modifier = ctor.newInstance(SUBMERGED_MODIFIER_UUID, "aqua_curse_submerged", 5.0, operation);
                } catch (Throwable ignored2) {
                }
            }
            if (modifier == null) return;
            java.lang.reflect.Method addModifier = instanceClass.getMethod("addTransientModifier", modifierClass);
            addModifier.invoke(instance, modifier);
        } catch (Throwable ignored) {
            // attribute not available on this server version -> HASTE alone mitigates
        }
    }

    private static void removeAquaAffinity(Player player) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attribute = null;
            for (String name : new String[]{"SUBMERGED_MINING_SPEED", "PLAYER_SUBMERGED_MINING_SPEED", "GENERIC_SUBMERGED_MINING_SPEED"}) {
                try {
                    @SuppressWarnings("unchecked")
                    Object v = Enum.valueOf((Class<Enum>) attributeClass, name);
                    attribute = v;
                    break;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (attribute == null) return;
            java.lang.reflect.Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);
            Object instance = getAttribute.invoke(player, attribute);
            if (instance == null) return;
            Class<?> instanceClass = instance.getClass();
            java.lang.reflect.Method getModifiers = instanceClass.getMethod("getModifiers");
            @SuppressWarnings("unchecked")
            java.util.Collection<?> mods = (java.util.Collection<?>) getModifiers.invoke(instance);
            Object target = null;
            for (Object mod : mods) {
                try {
                    java.lang.reflect.Method getUniqueId = mod.getClass().getMethod("getUniqueId");
                    java.util.UUID uid = (java.util.UUID) getUniqueId.invoke(mod);
                    if (SUBMERGED_MODIFIER_UUID.equals(uid)) { target = mod; break; }
                } catch (Throwable ignored) {
                }
                try {
                    java.lang.reflect.Method getKey = mod.getClass().getMethod("getKey");
                    Object key = getKey.invoke(mod);
                    if (key != null && key.toString().contains("aqua_curse")) { target = mod; break; }
                } catch (Throwable ignored) {
                }
            }
            if (target != null) {
                java.lang.reflect.Method removeModifier = instanceClass.getMethod("removeModifier", Class.forName("org.bukkit.attribute.AttributeModifier"));
                removeModifier.invoke(instance, target);
            }
        } catch (Throwable ignored) {
        }
    }
}
