package com.mha.plugin;

import com.mha.plugin.awakening.QuirkAwakener;
import com.mha.plugin.command.MHACommand;
import com.mha.plugin.destruction.DestructionManager;
import com.mha.plugin.listener.*;
import com.mha.plugin.qte.QTEManager;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.synergy.SynergyManager;
import com.mha.plugin.util.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MHAPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private StaminaManager staminaManager;
    private QuirkManager quirkManager;
    private QTEManager qteManager;
    private DestructionManager destructionManager;
    private SynergyManager synergyManager;
    private ReputationManager reputationManager;
    private QuirkAwakener quirkAwakener;
    private Economy econ = null;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        staminaManager = new StaminaManager(this, configManager);
        quirkManager = new QuirkManager(this, configManager, staminaManager);
        qteManager = new QTEManager(this, configManager);
        destructionManager = new DestructionManager(this, configManager);
        synergyManager = new SynergyManager(this, quirkManager, configManager);
        reputationManager = new ReputationManager(this, configManager);
        quirkAwakener = new QuirkAwakener(this, quirkManager, configManager);
        quirkAwakener.loadAwakenedPlayers();

        if (!setupEconomy()) {
            getLogger().warning("Vault not found! Bounties will drop diamonds instead of money.");
        }

        registerListeners();
        registerCommand("mha", new MHACommand(this));

        final int saveInterval = configManager.getInt("settings.save-interval", 300) * 20;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            quirkManager.saveQuirkAssignments();
            reputationManager.saveAllReputation();
        }, saveInterval, saveInterval);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (quirkManager != null) {
            quirkManager.saveQuirkAssignments();
            quirkManager.shutdown();
        }
        if (reputationManager != null) reputationManager.saveAllReputation();
        if (staminaManager != null) staminaManager.shutdown();
        if (qteManager != null) qteManager.shutdown();
        if (destructionManager != null) destructionManager.shutdown();
        if (quirkAwakener != null) quirkAwakener.shutdown();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public ReputationManager getReputationManager() { return reputationManager; }
    public QuirkManager getQuirkManager() { return quirkManager; }
    public QTEManager getQteManager() { return qteManager; }
    public DestructionManager getDestructionManager() { return destructionManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public StaminaManager getStaminaManager() { return staminaManager; }
    public QuirkAwakener getQuirkAwakener() { return quirkAwakener; }
    public Economy getEconomy() { return econ; }

    private void registerListeners() {
        registerListener(new PlayerQuitListener(this));
        registerListener(new QuirkActivationListener(quirkManager, staminaManager, configManager));
        registerListener(new QuirkPassiveListener(this, quirkManager));
        registerListener(new PlayerJoinListener(this, quirkManager, staminaManager, configManager, quirkAwakener, reputationManager));
        registerListener(new SupportGearListener(this));
        registerListener(new BountyListener(this));
        registerListener(new AlignmentGuiListener(this, reputationManager, configManager));

        // Register quirk-specific listeners that implement Listener interface
        quirkManager.getAllQuirks().forEach(quirk -> {
            if (quirk instanceof Listener listener) {
                registerListener(listener);
            }
        });
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand(String name, MHACommand handler) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }
}