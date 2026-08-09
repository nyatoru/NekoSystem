package com.nyarutoru.nekoplugin.core.settings;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingDescriptorTest {
    @Test
    void parsesBooleanAliasesAndRejectsUnknownText() {
        SettingDescriptor<Boolean> setting = SettingDescriptor.bool("enabled", "Enabled", false, ApplySemantics.IMMEDIATE, null);
        assertTrue(setting.parse("on"));
        assertFalse(setting.parse("FALSE"));
        assertThrows(IllegalArgumentException.class, () -> setting.parse("maybe"));
    }

    @Test
    void boundedNumbersRejectOutOfRangeAndNonFiniteValues() {
        SettingDescriptor<Integer> integer = SettingDescriptor.integer("count", "Count", 2, 1, 3, ApplySemantics.FUTURE_ONLY, null);
        assertEquals(3, integer.parse("3"));
        assertThrows(IllegalArgumentException.class, () -> integer.parse("4"));
        SettingDescriptor<Double> decimal = SettingDescriptor.doubleValue("ratio", "Ratio", 0.5, 0, 1, ApplySemantics.RESCHEDULE, null);
        assertThrows(IllegalArgumentException.class, () -> decimal.parse("NaN"));
    }

    @Test
    void parsesCommaSeparatedPlatformLists() {
        SettingDescriptor<List<Material>> setting = SettingDescriptor.materials("blocks", "Blocks", List.of(), ApplySemantics.FEATURE_RESTART, null);
        assertEquals(List.of(Material.STONE, Material.DIRT), setting.parse("stone, dirt"));
        assertEquals("STONE,DIRT", setting.format(List.of(Material.STONE, Material.DIRT)));
    }
}
