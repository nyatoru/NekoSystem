package com.nyarutoru.nekoplugin.features.mending;

/**
 * Pure repair economics for the Mending Repair feature.
 * Matches vanilla Mending rate: 1 XP point repairs 2 durability.
 */
public record RepairCost(int xpCost, int repairDamage) {

    public static RepairCost compute(int damage, int availableXp) {
        return compute(damage, availableXp, 2);
    }

    public static RepairCost compute(int damage, int availableXp, int rate) {
        if (damage <= 0 || availableXp <= 0 || rate <= 0) return new RepairCost(0, 0);
        int xpCost = Math.min((damage + rate - 1) / rate, availableXp);
        return new RepairCost(xpCost, xpCost * rate);
    }
}
