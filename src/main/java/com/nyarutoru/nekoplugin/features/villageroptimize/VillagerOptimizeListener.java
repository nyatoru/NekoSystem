package com.nyarutoru.nekoplugin.features.villageroptimize;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.ComponentUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class VillagerOptimizeListener implements Listener {

    private static final String OPTIMIZATION_TYPE = "NAMETAG";

    private boolean protectDamage = true;
    private boolean protectKnockback = true;
    private boolean protectTargeting = true;
    private boolean restoreAiOnDisable = true;

    private final NamespacedKey optimizationTypeKey;
    private final NamespacedKey lastOptimizeKey;
    private final NamespacedKey lastLevelUpKey;
    private final NamespacedKey lastRestockKey;

    public VillagerOptimizeListener(NekoPlugin plugin) {
        optimizationTypeKey = new NamespacedKey(plugin, "villager_optimization_type");
        lastOptimizeKey = new NamespacedKey(plugin, "villager_last_optimize");
        lastLevelUpKey = new NamespacedKey(plugin, "villager_last_level_up");
        lastRestockKey = new NamespacedKey(plugin, "villager_last_restock");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNameTagUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item.getType() != org.bukkit.Material.NAME_TAG || !item.hasItemMeta()) {
            handleOptimizedInteraction(event, villager);
            return;
        }

        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) {
            handleOptimizedInteraction(event, villager);
            return;
        }

        String name = PlainTextComponentSerializer.plainText().serialize(displayName);
        if (VillagerOptimizePolicy.isOptimizeName(name)) {
            optimize(event, villager);
        } else if (isOptimized(villager)) {
            unoptimize(villager);
            event.getPlayer().sendActionBar(ComponentUtils.info("Villager optimization disabled"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (protectTargeting && event.getTarget() instanceof Villager villager && isOptimized(villager)) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (protectDamage && event.getEntity() instanceof Villager villager && isOptimized(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (protectKnockback && event.getEntity() instanceof Villager villager && isOptimized(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (event.getReason() == VillagerCareerChangeEvent.ChangeReason.LOSING_JOB
                && isOptimized(event.getEntity())) {
            unoptimize(event.getEntity());
        }
    }

    private void optimize(PlayerInteractEntityEvent event, Villager villager) {
        long now = System.currentTimeMillis();
        Long lastOptimize = villager.getPersistentDataContainer().get(lastOptimizeKey, PersistentDataType.LONG);
        if (lastOptimize != null && !VillagerOptimizePolicy.cooldownElapsed(
                now, lastOptimize, VillagerOptimizePolicy.optimizeCooldownMillis)) {
            event.getPlayer().sendActionBar(ComponentUtils.error("This villager was optimized recently"));
            return;
        }

        setOptimized(villager, now);
        event.getPlayer().sendActionBar(ComponentUtils.success("Villager optimized"));
    }

    private void handleOptimizedInteraction(PlayerInteractEntityEvent event, Villager villager) {
        if (!isOptimized(villager)) {
            return;
        }

        restockTrades(villager);
        levelProfession(villager);
    }

    private void restockTrades(Villager villager) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        long restockTime = VillagerOptimizePolicy.latestRestockTime(villager.getWorld().getFullTime());
        Long lastRestock = data.get(lastRestockKey, PersistentDataType.LONG);
        if (lastRestock != null && lastRestock >= restockTime) {
            return;
        }

        for (MerchantRecipe recipe : villager.getRecipes()) {
            VillagerReplenishTradeEvent event = new VillagerReplenishTradeEvent(villager, recipe);
            if (event.callEvent()) {
                event.getRecipe().setUses(0);
            }
        }
        data.set(lastRestockKey, PersistentDataType.LONG, restockTime);
    }

    private void levelProfession(Villager villager) {
        long now = System.currentTimeMillis();
        PersistentDataContainer data = villager.getPersistentDataContainer();
        Long lastLevelUp = data.get(lastLevelUpKey, PersistentDataType.LONG);
        if (lastLevelUp != null && !VillagerOptimizePolicy.cooldownElapsed(
                now, lastLevelUp, VillagerOptimizePolicy.levelCheckCooldownMillis)) {
            return;
        }

        int expectedLevel = VillagerOptimizePolicy.levelForExperience(villager.getVillagerExperience());
        if (villager.getVillagerLevel() < expectedLevel) {
            villager.setVillagerLevel(expectedLevel);
        }
        data.set(lastLevelUpKey, PersistentDataType.LONG, now);
    }

    private void setOptimized(Villager villager, long now) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        data.set(optimizationTypeKey, PersistentDataType.STRING, OPTIMIZATION_TYPE);
        data.set(lastOptimizeKey, PersistentDataType.LONG, now);
        villager.setAware(false);
    }

    private void unoptimize(Villager villager) {
        villager.getPersistentDataContainer().remove(optimizationTypeKey);
        villager.setAware(true);
        villager.setAI(true);
    }

    public void configure(boolean protectDamage, boolean protectKnockback, boolean protectTargeting,
                          boolean restoreAiOnDisable) {
        this.protectDamage = protectDamage;
        this.protectKnockback = protectKnockback;
        this.protectTargeting = protectTargeting;
        this.restoreAiOnDisable = restoreAiOnDisable;
    }

    public void restoreLoadedOptimizedVillagers() {
        if (!restoreAiOnDisable) return;
        org.bukkit.Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Villager.class).forEach(villager -> {
            if (isOptimized(villager)) SchedulerUtils.runAtEntity(villager, () -> restoreRuntimeState(villager));
        }));
    }

    private void restoreRuntimeState(Villager villager) {
        villager.setAware(true);
        villager.setAI(true);
    }

    private boolean isOptimized(Entity entity) {
        return entity.getPersistentDataContainer().has(optimizationTypeKey, PersistentDataType.STRING);
    }
}
