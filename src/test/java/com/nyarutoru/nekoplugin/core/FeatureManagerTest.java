package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class FeatureManagerTest {
    private FeatureManager manager;

    @BeforeEach
    void setUp() {
        manager = new FeatureManager(Logger.getAnonymousLogger());
    }

    @Test
    void transitionsAreGuardedAndReported() {
        TestFeature feature = new TestFeature("alpha", new ArrayList<>());
        manager.registerFeature(feature);
        assertEquals(FeatureManager.TransitionStatus.CHANGED, manager.enable("alpha").status());
        assertEquals(FeatureManager.TransitionStatus.ALREADY_IN_STATE, manager.enable("alpha").status());
        assertEquals(1, feature.enableCalls);
        assertEquals(FeatureManager.TransitionStatus.CHANGED, manager.disable("alpha").status());
        assertEquals(1, feature.disableCalls);
        assertEquals(FeatureManager.TransitionStatus.NOT_FOUND, manager.enable("missing").status());
    }

    @Test
    void desiredStartupUsesRegistrationOrderAndShutdownReversesIt() {
        List<String> calls = new ArrayList<>();
        manager.registerFeature(new TestFeature("first", calls));
        manager.registerFeature(new TestFeature("second", calls));
        manager.registerFeature(new TestFeature("third", calls));
        manager.enableDesired(id -> !id.equals("second"));
        assertEquals(List.of("enable:first", "enable:third"), calls);
        manager.disableAll();
        assertEquals(List.of("enable:first", "enable:third", "disable:third", "disable:first"), calls);
    }

    @Test
    void failedEnableIsRolledBackAndDoesNotStopLaterFeatures() {
        List<String> calls = new ArrayList<>();
        TestFeature broken = new TestFeature("broken", calls);
        broken.enableFailure = new AssertionError("boom");
        TestFeature healthy = new TestFeature("healthy", calls);
        manager.registerFeature(broken);
        manager.registerFeature(healthy);

        manager.enableAll();

        assertEquals(List.of("enable:broken", "disable:broken", "enable:healthy"), calls);
        assertFalse(broken.isEnabled());
        assertTrue(healthy.isEnabled());
    }

    @Test
    void shutdownDisablesAndDropsRegistrations() {
        TestFeature feature = new TestFeature("alpha", new ArrayList<>());
        manager.registerFeature(feature);
        manager.enable("alpha");
        manager.shutdown();
        assertFalse(feature.isEnabled());
        assertEquals(0, manager.getFeatureCount());
    }

    private static final class TestFeature implements Feature {
        private final String id;
        private final List<String> calls;
        private boolean enabled;
        private int enableCalls;
        private int disableCalls;
        private Error enableFailure;

        private TestFeature(String id, List<String> calls) { this.id = id; this.calls = calls; }
        public String getId() { return id; }
        public String getName() { return id; }
        public void onEnable(NekoPlugin plugin) {
            enableCalls++;
            calls.add("enable:" + id);
            if (enableFailure != null) throw enableFailure;
            enabled = true;
        }
        public void onDisable() { disableCalls++; enabled = false; calls.add("disable:" + id); }
        public boolean isEnabled() { return enabled; }
    }
}
