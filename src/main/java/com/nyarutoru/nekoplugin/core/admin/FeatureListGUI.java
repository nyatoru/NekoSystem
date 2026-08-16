package com.nyarutoru.nekoplugin.core.admin;

import com.nyarutoru.nekoplugin.api.gui.PreviewGUI;
import com.nyarutoru.nekoplugin.core.Feature;
import com.nyarutoru.nekoplugin.core.FeatureManager;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Operator feature overview. */
public final class FeatureListGUI extends PreviewGUI {
    private final FeatureManager manager;
    private final AdminState state;
    private final AdminConfigStore store;
    private final SettingRegistry settings;

    public FeatureListGUI(FeatureManager manager, AdminState state, AdminConfigStore store, SettingRegistry settings) {
        super(54, Component.text("Neko Features", NamedTextColor.GOLD));
        this.manager = manager;
        this.state = state;
        this.store = store;
        this.settings = settings;
        refresh();
    }

    @Override
    public void open(Player player) {
        if (!player.isOp()) return;
        super.open(player);
    }

    @Override
    public void refresh() {
        inventory.clear();
        clickHandlers.clear();
        int slot = 0;
        for (Feature feature : manager.getAllFeatures().values()) {
            if (slot >= 45) break;
            addClickableSlot(slot++, featureItem(feature), event -> {
                Player player = (Player) event.getWhoClicked();
                if (!player.isOp()) { player.closeInventory(); return; }
                if (event.getClick() == ClickType.RIGHT) {
                    SchedulerUtils.runAtPlayer(player, () -> {
                        if (!player.isOp()) {
                            player.closeInventory();
                            return;
                        }
                        if ("aquacurse".equals(feature.getId()) && feature instanceof com.nyarutoru.nekoplugin.features.curse.AquaCurseFeature aqua) {
                            new com.nyarutoru.nekoplugin.features.curse.AquaCurseAdminGUI(aqua, manager, state, store, settings, 0).open(player);
                        } else {
                            new FeatureSettingsGUI(feature, manager, state, store, settings, 0).open(player);
                        }
                    });
                    return;
                }
                boolean desired = !feature.isEnabled();
                SchedulerUtils.runGlobalTask(() -> {
                    if (!player.isOp()) return;
                    FeatureManager.TransitionResult result = manager.setEnabled(feature.getId(), desired);
                    SchedulerUtils.runAtPlayer(player, () -> {
                        if (!player.isOp()) {
                            player.closeInventory();
                            return;
                        }
                        if (result.success()) {
                            state.setDesiredEnabled(feature.getId(), desired);
                            store.requestSave();
                        } else {
                            player.sendMessage(Component.text("Feature transition failed: " + result.message(), NamedTextColor.RED));
                        }
                        refresh();
                    });
                });
            });
        }
        setCloseButton(49);
        fillWithBlackGlass();
    }

    private ItemStack featureItem(Feature feature) {
        boolean enabled = feature.isEnabled();
        ItemStack item = new ItemStack(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(feature.getName(), enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ID: " + feature.getId(), NamedTextColor.GRAY));
        lore.add(Component.text("Actual: " + (enabled ? "enabled" : "disabled"), NamedTextColor.WHITE));
        lore.add(Component.text("Left click: toggle", NamedTextColor.YELLOW));
        if ("aquacurse".equals(feature.getId())) lore.add(Component.text("Right click: players (water curse)", NamedTextColor.AQUA));
        else if (settings.get(feature.getId()).isEmpty()) lore.add(Component.text("Right click: no settings registered", NamedTextColor.DARK_GRAY));
        else lore.add(Component.text("Right click: settings", NamedTextColor.AQUA));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
