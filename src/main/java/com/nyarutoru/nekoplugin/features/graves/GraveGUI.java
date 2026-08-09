package com.nyarutoru.nekoplugin.features.graves;

import com.nyarutoru.nekoplugin.api.gui.BaseGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class GraveGUI extends BaseGUI {
    private static final int PAGE_SIZE = 45;
    private final GraveManager manager;
    private final Grave grave;
    private final Player player;
    private int page;

    GraveGUI(GraveManager manager, Grave grave, Player player) {
        super(54, Component.text("Grave of " + grave.getOwnerName(), NamedTextColor.DARK_GRAY));
        this.manager = manager;
        this.grave = grave;
        this.player = player;
        refresh();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        super.onClick(event);
    }

    @Override
    public void refresh() {
        inventory.clear();
        clickHandlers.clear();
        List<ItemStack> items = grave.getItems();
        int start = page * PAGE_SIZE;
        if (start >= items.size() && page > 0) {
            page--;
            start = page * PAGE_SIZE;
        }
        for (int slot = 0; slot < PAGE_SIZE && start + slot < items.size(); slot++) {
            int index = start + slot;
            setItem(slot, items.get(index), event -> {
                if (grave.hasPendingClaim()) return;
                manager.claimItem(grave, index, player, success -> {
                    if (grave.getState() != Grave.State.ACTIVE || grave.isEmpty()) player.closeInventory();
                    else refresh();
                });
                refresh();
            });
        }
        if (page > 0) setItem(45, createItem(Material.ARROW, "Previous page"), event -> { page--; refresh(); });
        setItem(48, createItem(Material.CHEST, "Get all items", List.of("Requires enough inventory space")), event -> {
            if (grave.hasPendingClaim()) return;
            boolean started = manager.claimAll(grave, player, success -> {
                if (success || grave.getState() != Grave.State.ACTIVE || grave.isEmpty()) {
                    player.closeInventory();
                } else {
                    player.sendMessage(Component.text("Could not retrieve all items. Check your inventory space.", NamedTextColor.RED));
                    refresh();
                }
            });
            if (!started) player.sendMessage(Component.text("Not enough inventory space for all grave items.", NamedTextColor.RED));
            else refresh();
        });
        if (start + PAGE_SIZE < items.size()) setItem(53, createItem(Material.ARROW, "Next page"), event -> { page++; refresh(); });
        setItem(49, createCloseButton(), event -> player.closeInventory());
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Override
    public void onClose(Player closingPlayer) { manager.releaseViewer(grave); }
}
