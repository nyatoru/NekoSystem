package com.nyarutoru.nekoplugin.features.mending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepairCostTest {

    @Test
    void repairsTwoDurabilityPerXpUpToFullRepair() {
        assertEquals(new RepairCost(5, 10), RepairCost.compute(10, 100));
    }

    @Test
    void roundsXpCostUpForOddDamage() {
        assertEquals(new RepairCost(2, 4), RepairCost.compute(3, 100));
    }

    @Test
    void capsCostAtAvailableXpAndRepairsPartially() {
        assertEquals(new RepairCost(3, 6), RepairCost.compute(10, 3));
    }

    @Test
    void noOpWhenNothingToRepairOrNoXp() {
        assertEquals(new RepairCost(0, 0), RepairCost.compute(0, 100));
        assertEquals(new RepairCost(0, 0), RepairCost.compute(10, 0));
        assertEquals(new RepairCost(0, 0), RepairCost.compute(0, 0));
    }
}
