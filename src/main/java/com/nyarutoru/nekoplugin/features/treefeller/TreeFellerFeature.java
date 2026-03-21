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
 * 
 * Commands:
 * - /treefeller debug on|off|status - Debug mode for OPs
 */
public class TreeFellerFeature extends AbstractFeature {
    
    private TreeFellerListener treeFellerListener;
    private TreeFellerCommands treeFellerCommands;

    public TreeFellerFeature() {
        super("tree_feller", "Tree Feller");
    }

    @Override
    public void onEnable(NekoPlugin plugin) {
        treeFellerListener = new TreeFellerListener(plugin);
        registerListener(treeFellerListener, plugin);
        
        treeFellerCommands = new TreeFellerCommands(plugin, treeFellerListener);
        treeFellerCommands.register();
        
        super.onEnable(plugin);
    }

    @Override
    public void onDisable() {
        if (treeFellerCommands != null) {
            treeFellerCommands.unregister();
        }
        super.onDisable();
    }
}
