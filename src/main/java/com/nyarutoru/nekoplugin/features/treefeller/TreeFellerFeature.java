package com.nyarutoru.nekoplugin.features.treefeller;

import com.nyarutoru.nekoplugin.NekoPlugin;
import com.nyarutoru.nekoplugin.core.AbstractFeature;

/**
 * Tree Feller feature - chop entire trees with one swing.
 * Uses ActiveToolAPI for shift activation.
 */
public class TreeFellerFeature extends AbstractFeature {

    public TreeFellerFeature() {
        super("tree_feller", "Tree Feller");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        registerListener(new TreeFellerListener(plugin), plugin);
        registerListener(new FastLeafDecayListener(), plugin);
        super.onEnable(plugin);
    }
}
