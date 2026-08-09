package com.nyarutoru.nekoplugin.core.admin;

import com.nyarutoru.nekoplugin.api.gui.AnvilTextInputGUI;
import com.nyarutoru.nekoplugin.api.gui.PreviewGUI;
import com.nyarutoru.nekoplugin.core.Feature;
import com.nyarutoru.nekoplugin.core.FeatureManager;
import com.nyarutoru.nekoplugin.core.settings.SettingDescriptor;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.core.settings.SettingType;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Generic paginated setting editor. */
public final class FeatureSettingsGUI extends PreviewGUI {
    private static final int PAGE_SIZE = 45;
    private final Feature feature;
    private final FeatureManager manager;
    private final AdminState state;
    private final AdminConfigStore store;
    private final SettingRegistry registry;
    private final int page;

    public FeatureSettingsGUI(Feature feature, FeatureManager manager, AdminState state,
                              AdminConfigStore store, SettingRegistry registry, int page) {
        super(54, Component.text(feature.getName() + " Settings", NamedTextColor.GOLD));
        this.feature = feature;
        this.manager = manager;
        this.state = state;
        this.store = store;
        this.registry = registry;
        this.page = page;
        refresh();
    }

    @Override
    public void open(Player player) { if (player.isOp()) super.open(player); }

    @Override
    public void refresh() {
        inventory.clear();
        clickHandlers.clear();
        List<SettingDescriptor<?>> settings = registry.get(feature.getId());
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, settings.size()); index++) {
            SettingDescriptor<?> descriptor = settings.get(index);
            addClickableSlot(index - start, settingItem(descriptor), event -> edit((Player) event.getWhoClicked(), descriptor));
        }
        if (settings.isEmpty()) setDisplayItem(22, createItem(Material.GRAY_DYE, "No settings registered"));
        setBackButton(49, event -> {
            Player player = (Player) event.getWhoClicked();
            if (player.isOp()) SchedulerUtils.runAtEntity(player, () -> {
                if (!player.isOp()) {
                    player.closeInventory();
                    return;
                }
                new FeatureListGUI(manager, state, store, registry).open(player);
            });
        });
        if (page > 0) addClickableSlot(45, createItem(Material.ARROW, "Previous"), event -> openPage((Player) event.getWhoClicked(), page - 1));
        if (start + PAGE_SIZE < settings.size()) addClickableSlot(53, createItem(Material.ARROW, "Next"), event -> openPage((Player) event.getWhoClicked(), page + 1));
        fillWithBlackGlass();
    }

    private void openPage(Player player, int targetPage) {
        if (player.isOp()) SchedulerUtils.runAtEntity(player, () -> {
            if (!player.isOp()) {
                player.closeInventory();
                return;
            }
            new FeatureSettingsGUI(feature, manager, state, store, registry, targetPage).open(player);
        });
    }

    private void edit(Player player, SettingDescriptor<?> descriptor) {
        if (!player.isOp()) { player.closeInventory(); return; }
        String current = currentValue(descriptor);
        if (descriptor.type() == SettingType.BOOLEAN) {
            saveParsed(player, descriptor, Boolean.toString(!Boolean.parseBoolean(current)));
            return;
        }
        new AnvilTextInputGUI(Component.text(descriptor.displayName()), current,
                text -> saveParsed(player, descriptor, text),
                () -> {
                    if (!player.isOp()) {
                        player.closeInventory();
                        return;
                    }
                    new FeatureSettingsGUI(feature, manager, state, store, registry, page).open(player);
                }).open(player);
    }

    private String currentValue(SettingDescriptor<?> descriptor) {
        return state.canonicalizeSetting(feature.getId(), descriptor);
    }

    private <T> void saveTyped(Player player, SettingDescriptor<T> descriptor, String input) {
        final T value;
        final String formatted;
        try {
            value = descriptor.parse(input);
            formatted = descriptor.format(value);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("Invalid value: " + exception.getMessage(), NamedTextColor.RED));
            return;
        }

        SchedulerUtils.runGlobalTask(() -> {
            if (!player.isOp()) return;
            try {
                descriptor.apply(value);
                if (!player.isOp()) return;
                state.setSettingValue(feature.getId(), descriptor.key(), formatted);
                if (!player.isOp()) return;
                store.requestSave();
                SchedulerUtils.runAtEntity(player, () -> {
                    if (!player.isOp()) {
                        player.closeInventory();
                        return;
                    }
                    refresh();
                });
            } catch (RuntimeException exception) {
                SchedulerUtils.runAtEntity(player, () -> {
                    if (player.isOp()) {
                        player.sendMessage(Component.text("Could not apply setting: " + exception.getMessage(), NamedTextColor.RED));
                    } else {
                        player.closeInventory();
                    }
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void saveParsed(Player player, SettingDescriptor<?> descriptor, String input) {
        saveTyped(player, (SettingDescriptor<Object>) descriptor, input);
    }

    private ItemStack settingItem(SettingDescriptor<?> descriptor) {
        ItemStack item = new ItemStack(descriptor.type() == SettingType.BOOLEAN ? Material.LEVER : Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(descriptor.displayName(), NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text("Value: " + currentValue(descriptor), NamedTextColor.WHITE),
                Component.text("Apply: " + descriptor.applySemantics().description(), NamedTextColor.GRAY),
                Component.text(descriptor.type() == SettingType.BOOLEAN ? "Click to toggle" : "Click to edit", NamedTextColor.AQUA)));
        item.setItemMeta(meta);
        return item;
    }
}
