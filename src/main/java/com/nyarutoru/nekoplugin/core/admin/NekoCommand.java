package com.nyarutoru.nekoplugin.core.admin;

import com.nyarutoru.nekoplugin.core.FeatureManager;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Strict operator-only entry point for the administrative GUI. */
public final class NekoCommand implements BasicCommand {
    private final FeatureManager manager;
    private final AdminState state;
    private final AdminConfigStore store;
    private final SettingRegistry settings;

    public NekoCommand(FeatureManager manager, AdminState state, AdminConfigStore store, SettingRegistry settings) {
        this.manager = manager;
        this.state = state;
        this.store = store;
        this.settings = settings;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(Component.text("Only server operators in-game may use /neko.", NamedTextColor.RED));
            return;
        }
        new FeatureListGUI(manager, state, store, settings).open(player);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender instanceof Player player && player.isOp();
    }
}
