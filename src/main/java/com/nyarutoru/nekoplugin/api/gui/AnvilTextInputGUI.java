package com.nyarutoru.nekoplugin.api.gui;

import com.nyarutoru.nekoplugin.utils.ItemUtils;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Virtual Paper anvil text input managed by {@link GUIManager}. */
public final class AnvilTextInputGUI {
    private final Component title;
    private final String initialValue;
    private final Consumer<String> submit;
    private final Runnable returnAction;
    private final AtomicBoolean completed = new AtomicBoolean();

    public AnvilTextInputGUI(Component title, String initialValue, Consumer<String> submit, Runnable returnAction) {
        this.title = Objects.requireNonNull(title, "title");
        this.initialValue = Objects.requireNonNull(initialValue, "initialValue");
        this.submit = Objects.requireNonNull(submit, "submit");
        this.returnAction = Objects.requireNonNull(returnAction, "returnAction");
    }

    public void open(Player player) {
        if (!player.isOp()) return;
        AnvilView view = MenuType.ANVIL.create(player, title);
        view.setItem(0, ItemUtils.createDisplayItem(Material.PAPER, initialValue));
        view.setRepairCost(0);
        GUIManager.getInstance().registerAnvil(player, this, view);
        player.openInventory(view);
    }

    void click(Player player, AnvilView view, int rawSlot) {
        if (rawSlot != 2 || !player.isOp() || !completed.compareAndSet(false, true)) return;
        String text = view.getRenameText();
        player.closeInventory();
        if (text != null && player.isOp()) submit.accept(text);
        SchedulerUtils.runAtPlayer(player, () -> {
            if (!player.isOp()) {
                player.closeInventory();
                return;
            }
            returnAction.run();
        });
    }

    void closed(Player player, AnvilView view) {
        if (!completed.compareAndSet(false, true)) return;
        SchedulerUtils.runAtPlayer(player, () -> {
            if (!player.isOp()) {
                player.closeInventory();
                return;
            }
            returnAction.run();
        });
    }
}
