package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Tree Feller feature - chop entire trees with one swing.
 * Uses ActiveToolAPI for shift activation.
 * 
 * Leaf Breaking:
 * - BFS leaf detection finds ALL connected leaves (chain reaction)
 * - Leaves broken immediately with logs (no vanilla decay needed)
 * - FastLeafDecay removed - redundant with proper leaf detection
 */
public class TreeFellerFeature extends AbstractFeature {

    public TreeFellerFeature() {
        super("tree_feller", "Tree Feller");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new TreeFellerListener(plugin), plugin);
        super.onEnable(plugin);
    }
}
