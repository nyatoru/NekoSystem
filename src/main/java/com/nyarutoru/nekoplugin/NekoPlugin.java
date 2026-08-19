package com.nyarutoru.nekoplugin;

import com.nyarutoru.nekoplugin.api.gui.GUIManager;
import com.nyarutoru.nekoplugin.api.recipe.RecipeAPI;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolAPI;
import com.nyarutoru.nekoplugin.api.tool.ActiveToolListener;
import com.nyarutoru.nekoplugin.core.DatabaseManager;
import com.nyarutoru.nekoplugin.core.FeatureManager;
import com.nyarutoru.nekoplugin.core.admin.AdminConfigStore;
import com.nyarutoru.nekoplugin.core.admin.AdminState;
import com.nyarutoru.nekoplugin.core.admin.NekoCommand;
import com.nyarutoru.nekoplugin.core.settings.SettingRegistry;
import com.nyarutoru.nekoplugin.features.carry.CarryFeature;
import com.nyarutoru.nekoplugin.features.drawer.DrawerFeature;
import com.nyarutoru.nekoplugin.features.graves.GravesFeature;
import com.nyarutoru.nekoplugin.features.hammer.HammerFeature;
import com.nyarutoru.nekoplugin.features.oreexcavation.OreExcavationFeature;
import com.nyarutoru.nekoplugin.features.player.PlayerFeature;
import com.nyarutoru.nekoplugin.features.server.ServerFeature;
import com.nyarutoru.nekoplugin.features.tool.SandExcavationFeature;
import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerFeature;
import com.nyarutoru.nekoplugin.features.curse.AquaCurseFeature;
import com.nyarutoru.nekoplugin.features.magnet.MagnetFeature;
import com.nyarutoru.nekoplugin.features.mending.MendingRepairFeature;
import com.nyarutoru.nekoplugin.features.villageroptimize.VillagerOptimizeFeature;
import com.nyarutoru.nekoplugin.features.woodcutting.WoodcuttingFeature;
import com.nyarutoru.nekoplugin.features.furnace.FurnaceFeature;
import com.nyarutoru.nekoplugin.features.elytraflight.ElytraFlightFeature;
import org.bukkit.plugin.java.JavaPlugin;

public class NekoPlugin extends JavaPlugin {

    private static NekoPlugin instance;
    private AdminConfigStore adminConfigStore;

    public static NekoPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Load operator choices before any feature is enabled.
        AdminState adminState = new AdminState();
        SettingRegistry settingRegistry = new SettingRegistry();
        adminConfigStore = new AdminConfigStore(this, adminState);
        adminConfigStore.load();

        DatabaseManager.getInstance().initialize(this);
        FeatureManager.getInstance().initialize(this);
        GUIManager.getInstance().initialize(this);

        // Register features
        CarryFeature carryFeature = new CarryFeature();
        DrawerFeature drawerFeature = new DrawerFeature();
        GravesFeature gravesFeature = new GravesFeature();
        OreExcavationFeature oreExcavationFeature = new OreExcavationFeature();
        SandExcavationFeature sandExcavationFeature = new SandExcavationFeature();
        HammerFeature hammerFeature = new HammerFeature();
        PlayerFeature playerFeature = new PlayerFeature();
        ServerFeature serverFeature = new ServerFeature();
        WoodcuttingFeature woodcuttingFeature = new WoodcuttingFeature();
        TreeFellerFeature treeFellerFeature = new TreeFellerFeature();
        VillagerOptimizeFeature villagerOptimizeFeature = new VillagerOptimizeFeature();
        AquaCurseFeature aquaCurseFeature = new AquaCurseFeature();
        MagnetFeature magnetFeature = new MagnetFeature();
        MendingRepairFeature mendingRepairFeature = new MendingRepairFeature();
        FurnaceFeature furnaceFeature = new FurnaceFeature();
        ElytraFlightFeature elytraFlightFeature = new ElytraFlightFeature();

        FeatureManager.getInstance().registerFeature(carryFeature);
        FeatureManager.getInstance().registerFeature(drawerFeature);
        FeatureManager.getInstance().registerFeature(gravesFeature);
        FeatureManager.getInstance().registerFeature(oreExcavationFeature);
        FeatureManager.getInstance().registerFeature(sandExcavationFeature);
        FeatureManager.getInstance().registerFeature(hammerFeature);
        FeatureManager.getInstance().registerFeature(playerFeature);
        FeatureManager.getInstance().registerFeature(serverFeature);
        FeatureManager.getInstance().registerFeature(woodcuttingFeature);
        FeatureManager.getInstance().registerFeature(treeFellerFeature);
        FeatureManager.getInstance().registerFeature(villagerOptimizeFeature);
        FeatureManager.getInstance().registerFeature(aquaCurseFeature);
        FeatureManager.getInstance().registerFeature(magnetFeature);
        FeatureManager.getInstance().registerFeature(mendingRepairFeature);
        FeatureManager.getInstance().registerFeature(furnaceFeature);
        FeatureManager.getInstance().registerFeature(elytraFlightFeature);

        carryFeature.registerSettings(settingRegistry, adminState);
        drawerFeature.registerSettings(settingRegistry, adminState);
        gravesFeature.registerSettings(settingRegistry, adminState);
        oreExcavationFeature.registerSettings(settingRegistry, adminState);
        sandExcavationFeature.registerSettings(settingRegistry, adminState);
        hammerFeature.registerSettings(settingRegistry, adminState);
        playerFeature.registerSettings(settingRegistry, adminState);
        serverFeature.registerSettings(settingRegistry, adminState);
        woodcuttingFeature.registerSettings(settingRegistry, adminState);
        treeFellerFeature.registerSettings(settingRegistry, adminState);
        villagerOptimizeFeature.registerSettings(settingRegistry, adminState);
        magnetFeature.registerSettings(settingRegistry, adminState);
        mendingRepairFeature.registerSettings(settingRegistry, adminState);
        furnaceFeature.registerSettings(settingRegistry, adminState);
        elytraFlightFeature.registerSettings(settingRegistry, adminState);

        // Register the single core command and honor persisted startup selection.
        registerCommand("neko", "Opens the Neko operator feature manager",
                new NekoCommand(FeatureManager.getInstance(), adminState, adminConfigStore, settingRegistry));
        FeatureManager.getInstance().enableDesired(adminState::desiredEnabled);

        // Register core listeners
        getServer().getPluginManager().registerEvents(new ActiveToolListener(), this);

        getLogger().info("NekoPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Disable all features and synchronously flush the last immutable snapshot.
        FeatureManager.getInstance().shutdown();
        ActiveToolAPI.getInstance().shutdown();
        RecipeAPI.getInstance().clear();
        if (adminConfigStore != null) {
            adminConfigStore.flush();
        }

        // Close database connection
        DatabaseManager.getInstance().shutdown();

        getLogger().info("NekoPlugin has been disabled!");
    }
}