package com.mha.plugin.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages configuration files and player data persistence.
 * Handles loading, saving, and accessing configuration values.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load or create the configuration file.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    /**
     * Get the underlying Bukkit configuration.
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Reload configuration from disk.
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.reloadConfig();
    }

    /**
     * Save configuration to disk.
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (final IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config.yml", e);
        }
    }

    /**
     * Get a string value from the config with a default fallback.
     */
    public String getString(final String path, final String defaultValue) {
        return config.getString(path, defaultValue);
    }

    /**
     * Get an integer value from the config with a default fallback.
     */
    public int getInt(final String path, final int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * Get a double value from the config with a default fallback.
     */
    public double getDouble(final String path, final double defaultValue) {
        return config.getDouble(path, defaultValue);
    }

    /**
     * Get a long value from the config with a default fallback.
     */
    public long getLong(final String path, final long defaultValue) {
        return config.getLong(path, defaultValue);
    }

    /**
     * Get a boolean value from the config with a default fallback.
     */
    public boolean getBoolean(final String path, final boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    /**
     * Get a configuration section at the specified path.
     */
    public ConfigurationSection getSection(final String path) {
        return config.getConfigurationSection(path);
    }

    /**
     * Set a value in the configuration.
     */
    public void set(final String path, final Object value) {
        config.set(path, value);
    }

    /**
     * Get all player Quirk assignments.
     * @return Map of UUID strings to Quirk type names
     */
    public Map<UUID, String> getPlayerQuirksMap() {
        final Map<UUID, String> result = new HashMap<>();
        final ConfigurationSection section = config.getConfigurationSection("players");

        if (section == null) {
            return result;
        }

        for (final String key : section.getKeys(false)) {
            try {
                final UUID uuid = UUID.fromString(key);
                final String quirkType = section.getString(key);
                if (quirkType != null) {
                    result.put(uuid, quirkType);
                }
            } catch (final IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in config: " + key);
            }
        }

        return result;
    }

    /**
     * Set a player's Quirk assignment in the config.
     */
    public void setPlayerQuirk(final UUID playerId, final String quirkType) {
        config.set("players." + playerId.toString(), quirkType);
        saveConfig();
    }

    /**
     * Remove a player's Quirk from the config.
     */
    public void removePlayerQuirk(final UUID playerId) {
        config.set("players." + playerId.toString(), null);
        saveConfig();
    }

    /**
     * Get a Quirk-specific configuration value.
     */
    public int getQuirkInt(final String quirkId, final String key, final int defaultValue) {
        return getInt("quirks." + quirkId + "." + key, defaultValue);
    }

    /**
     * Get a Quirk-specific configuration value.
     */
    public double getQuirkDouble(final String quirkId, final String key, final double defaultValue) {
        return getDouble("quirks." + quirkId + "." + key, defaultValue);
    }

    /**
     * Get a Quirk-specific configuration value.
     */
    public boolean getQuirkBoolean(final String quirkId, final String key, final boolean defaultValue) {
        return getBoolean("quirks." + quirkId + "." + key, defaultValue);
    }

    /**
     * Get a Quirk-specific nested configuration value (for ice-fire quirk).
     */
    public int getQuirkNestedInt(final String quirkId, final String subSection, final String key, final int defaultValue) {
        return getInt("quirks." + quirkId + "." + subSection + "." + key, defaultValue);
    }

    /**
     * Get a Quirk-specific nested configuration value (for ice-fire quirk).
     */
    public double getQuirkNestedDouble(final String quirkId, final String subSection, final String key, final double defaultValue) {
        return getDouble("quirks." + quirkId + "." + subSection + "." + key, defaultValue);
    }
}
