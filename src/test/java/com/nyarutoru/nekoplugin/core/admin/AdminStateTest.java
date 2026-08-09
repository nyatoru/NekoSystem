package com.nyarutoru.nekoplugin.core.admin;

import com.nyarutoru.nekoplugin.core.settings.ApplySemantics;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminStateTest {
    @Test
    void unlistedFeaturesDefaultEnabledAndSnapshotsAreIndependent() {
        AdminState state = new AdminState();
        assertTrue(state.desiredEnabled("new-feature"));
        state.setDesiredEnabled("example", false);
        AdminState.Snapshot snapshot = state.snapshot();
        state.setDesiredEnabled("example", true);
        assertEquals(Map.of("example", false), snapshot.desiredFeatures());
    }

    @Test
    void settingKeysAreScopedByFeature() {
        AdminState state = new AdminState();
        state.setSettingValue("one", "limit", "3");
        state.setSettingValue("two", "limit", "4");
        assertEquals("3", state.settingValue("one", "limit"));
        assertEquals("4", state.settingValue("two", "limit"));
    }

    @Test
    void invalidSettingIsCanonicalizedToDefault() {
        AdminState state = new AdminState();
        state.setSettingValue("example", "limit", "not-a-number");
        SettingDescriptor<Integer> descriptor = SettingDescriptor.integer(
                "limit", "Limit", 7, 1, 10, ApplySemantics.IMMEDIATE, ignored -> { });

        assertEquals("7", state.canonicalizeSetting("example", descriptor));
        assertEquals("7", state.settingValue("example", "limit"));
    }

    @Test
    void persistedFeatureBooleanValidationRejectsCoercibleValues() {
        assertEquals(Boolean.TRUE, AdminConfigStore.readPersistedBoolean(Boolean.TRUE));
        assertEquals(Boolean.FALSE, AdminConfigStore.readPersistedBoolean(Boolean.FALSE));
        assertNull(AdminConfigStore.readPersistedBoolean("true"));
        assertNull(AdminConfigStore.readPersistedBoolean(1));
    }
}
