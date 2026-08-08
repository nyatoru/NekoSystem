package com.nyarutoru.nekoplugin.features.treefeller.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastLeafDecayTest {

    @Test
    void decaysUnsupportedNaturalLeaves() {
        assertTrue(FastLeafDecay.shouldDecay(false, 7));
    }

    @Test
    void preservesLeavesConnectedToLogs() {
        assertFalse(FastLeafDecay.shouldDecay(false, 6));
    }

    @Test
    void preservesPersistentLeaves() {
        assertFalse(FastLeafDecay.shouldDecay(true, 7));
    }
}
