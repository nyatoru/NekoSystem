package com.nyarutoru.nekoplugin.core;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractFeatureTest {
    @Test
    void disableCancelsOwnedTasksAndClearsEnabledDespiteCleanupFailure() {
        TestFeature feature = new TestFeature();
        TestHandle task = new TestHandle();
        feature.onEnable(null);
        feature.track(task);
        feature.failCleanup = true;

        assertThrows(IllegalStateException.class, feature::onDisable);
        assertTrue(task.cancelled);
        assertFalse(feature.isEnabled());
    }

    private static final class TestFeature extends AbstractFeature {
        private boolean failCleanup;

        private TestFeature() {
            super("test", "Test");
        }

        private void track(SchedulerUtils.TaskHandle task) {
            ownTask(task);
        }

        @Override
        protected void cleanup() {
            if (failCleanup) throw new IllegalStateException("cleanup");
        }
    }

    private static final class TestHandle implements SchedulerUtils.TaskHandle {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
