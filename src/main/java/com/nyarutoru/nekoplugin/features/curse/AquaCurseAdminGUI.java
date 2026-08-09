package com.nyarutoru.nekoplugin.features.curse;

import com.nyarutoru.nekoplugin.api.gui.AnvilTextInputGUI;
import com.nyarutoru.nekoplugin.api.gui.PreviewGUI;
import com.nyarutoru.nekoplugin.core.FeatureManager;
import com.nyarutoru.nekoplugin.core.admin.AdminConfigStore;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.admin.FeatureListGUI;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

/**
 * Admin GUI for aqua curse — lists online + cursed players, click to toggle.
 * Reachable via /neko > Aqua Curse (right-click).
 */
public final class AquaCurseAdminGUI extends PreviewGUI {

    private static final int PAGE_SIZE = 45;

    private final AquaCurseFeature feature;
    private final FeatureManager manager;
    private final AdminState state;
    private final AdminConfigStore store;
    private final SettingRegistry registry;
    private final int page;

    public AquaCurseAdminGUI(AquaCurseFeature feature, FeatureManager manager, AdminState state,
                             AdminConfigStore store, SettingRegistry registry, int page) {
        super(54, Component.text("Aqua Curse — Players", NamedTextColor.AQUA));
        this.feature = feature;
        this.manager = manager;
        this.state = state;
        this.store = store;
        this.registry = registry;
        this.page = Math.max(0, page);
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

        Set<UUID> cursed = feature.getCursedCopy();
        Map<UUID, String> names = new HashMap<>();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();

        // online first
        for (Player p : Bukkit.getOnlinePlayers()) {
            ids.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
        // add offline cursed not already present
        for (UUID id : cursed) {
            if (ids.add(id)) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                String n = off.getName();
                names.put(id, n != null ? n : id.toString().substring(0, 8));
            }
        }

        List<UUID> sorted = new ArrayList<>(ids);
        sorted.sort((a, b) -> {
            boolean ca = cursed.contains(a);
            boolean cb = cursed.contains(b);
            if (ca != cb) return ca ? -1 : 1;
            String na = names.getOrDefault(a, a.toString());
            String nb = names.getOrDefault(b, b.toString());
            return na.compareToIgnoreCase(nb);
        });

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());

        for (int i = start; i < end; i++) {
            UUID id = sorted.get(i);
            int slot = i - start;
            boolean isCursed = cursed.contains(id);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            String name = names.getOrDefault(id, offline.getName() != null ? offline.getName() : id.toString());
            ItemStack head = createHead(offline, name, isCursed, feature.getOutTicks(id));
            addClickableSlot(slot, head, event -> {
                Player clicker = (Player) event.getWhoClicked();
                if (!clicker.isOp()) { clicker.closeInventory(); return; }
                boolean next = !feature.isCursed(id);
                feature.setCursed(id, next);
                if (next) {
                    clicker.sendMessage(Component.text("Cursed " + name + " — must stay in water (25s on land = drown).", NamedTextColor.GREEN));
                    Player target = Bukkit.getPlayer(id);
                    if (target != null) {
                        target.sendMessage(Component.text("You have been cursed! Stay in water or you will suffocate after 25 seconds on land.", NamedTextColor.RED));
                    }
                } else {
                    clicker.sendMessage(Component.text("Removed curse from " + name + ".", NamedTextColor.GREEN));
                    Player target = Bukkit.getPlayer(id);
                    if (target != null) {
                        target.sendMessage(Component.text("Your water curse has been lifted.", NamedTextColor.GREEN));
                    }
                }
                refresh();
            });
        }

        if (sorted.isEmpty()) {
            ItemStack info = new ItemStack(Material.GRAY_DYE);
            var meta = info.getItemMeta();
            meta.displayName(Component.text("No players", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("No online or cursed players.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            info.setItemMeta(meta);
            setDisplayItem(22, info);
        }

        // pagination
        if (page > 0) {
            addClickableSlot(45, button(Material.ARROW, "Previous"), e -> openPage((Player) e.getWhoClicked(), page - 1));
        }
        if (end < sorted.size()) {
            addClickableSlot(53, button(Material.ARROW, "Next"), e -> openPage((Player) e.getWhoClicked(), page + 1));
        }

        // add offline player via anvil
        addClickableSlot(48, buttonWithLore(Material.NAME_TAG, "Add / find player", List.of(
                Component.text("Type exact player name", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Toggle curse for offline too", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        )), e -> {
            Player p = (Player) e.getWhoClicked();
            if (!p.isOp()) return;
            new AnvilTextInputGUI(Component.text("Player name"), "", text -> {
                String input = text == null ? "" : text.trim();
                if (input.isEmpty() || input.length() > 16) {
                    p.sendMessage(Component.text("Invalid name.", NamedTextColor.RED));
                    SchedulerUtils.runAtEntity(p, () -> { if (p.isOp()) new AquaCurseAdminGUI(feature, manager, state, store, registry, page).open(p); });
                    return;
                }
                Player exact = Bukkit.getPlayerExact(input);
                UUID targetId;
                String targetName;
                if (exact != null) {
                    targetId = exact.getUniqueId();
                    targetName = exact.getName();
                } else {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(input);
                    if (!off.hasPlayedBefore() && !off.isOnline()) {
                        p.sendMessage(Component.text("Player '" + input + "' has never joined — ask them to join once, or curse an online player.", NamedTextColor.RED));
                        SchedulerUtils.runAtEntity(p, () -> { if (p.isOp()) new AquaCurseAdminGUI(feature, manager, state, store, registry, page).open(p); });
                        return;
                    }
                    targetId = off.getUniqueId();
                    targetName = off.getName() != null ? off.getName() : input;
                }
                boolean next = !feature.isCursed(targetId);
                feature.setCursed(targetId, next);
                p.sendMessage(Component.text((next ? "Cursed " : "Uncursed ") + targetName, NamedTextColor.GREEN));
                SchedulerUtils.runAtEntity(p, () -> { if (p.isOp()) new AquaCurseAdminGUI(feature, manager, state, store, registry, page).open(p); });
            }, () -> {
                if (p.isOp()) new AquaCurseAdminGUI(feature, manager, state, store, registry, page).open(p);
            }).open(p);
        });

        // refresh
        addClickableSlot(47, button(Material.PAPER, "Refresh"), e -> refresh());

        // back
        setBackButton(49, e -> {
            Player p = (Player) e.getWhoClicked();
            if (p.isOp()) SchedulerUtils.runAtEntity(p, () -> {
                if (!p.isOp()) { p.closeInventory(); return; }
                new FeatureListGUI(manager, state, store, registry).open(p);
            });
        });

        fillWithBlackGlass();
    }

    private void openPage(Player player, int target) {
        if (!player.isOp()) return;
        SchedulerUtils.runAtEntity(player, () -> {
            if (!player.isOp()) { player.closeInventory(); return; }
            new AquaCurseAdminGUI(feature, manager, state, store, registry, target).open(player);
        });
    }

    private static ItemStack createHead(OfflinePlayer offline, String name, boolean cursed, int outTicks) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            try { meta.setOwningPlayer(offline); } catch (Throwable ignored) {}
            meta.displayName(Component.text(name, cursed ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, cursed));
            List<Component> lore = new ArrayList<>();
            if (cursed) {
                lore.add(Component.text("Cursed — must stay in water", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("On land: " + (outTicks / 20) + "s / 25s", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to remove curse", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Not cursed", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to curse", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack button(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buttonWithLore(Material mat, String name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> formatted = new ArrayList<>();
        for (Component c : lore) formatted.add(c.decoration(TextDecoration.ITALIC, false));
        meta.lore(formatted);
        item.setItemMeta(meta);
        return item;
    }
}
