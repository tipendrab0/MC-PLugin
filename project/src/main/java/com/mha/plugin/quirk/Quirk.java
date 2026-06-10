package com.mha.plugin.quirk;

import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all Quirks.
 * Provides common functionality for cooldowns, stamina costs, and ability activation.
 * Power scaling is applied based on Quirk rarity.
 */
public abstract class Quirk {

    protected final QuirkType type;
    protected final ConfigManager config;
    protected final StaminaManager staminaManager;
    protected final Map<UUID, Long> cooldownTimestamps;
    protected final int baseCooldown;
    protected final int baseStaminaCost;

    /**
     * Create a Quirk instance.
     */
    protected Quirk(final QuirkType type, final ConfigManager config, final StaminaManager staminaManager) {
        this.type = type;
        this.config = config;
        this.staminaManager = staminaManager;
        this.cooldownTimestamps = new ConcurrentHashMap<>();
        this.baseCooldown = getCooldownFromConfig();
        this.baseStaminaCost = getStaminaCostFromConfig();
    }

    /**
     * Get the Quirk's type identifier.
     */
    public QuirkType getType() {
        return type;
    }

    /**
     * Get the Quirk's display name.
     */
    public String getName() {
        return type.getDisplayName();
    }

    /**
     * Get the Quirk's description.
     */
    public String getDescription() {
        return type.getDescription();
    }

    /**
     * Get the Quirk's rarity.
     */
    public QuirkRarity getRarity() {
        return type.getRarity();
    }

    /**
     * Check if this Quirk is enabled in the configuration.
     */
    public boolean isEnabled() {
        return config.getBoolean("quirks." + type.getId() + ".enabled", true);
    }

    /**
     * Check if the player can use this Quirk (cooldown + stamina).
     */
    public boolean canUse(final Player player) {
        if (!isEnabled()) {
            return false;
        }

        if (staminaManager.isExhausted(player)) {
            return false;
        }

        if (!hasPermission(player)) {
            return false;
        }

        return getCooldownRemaining(player) <= 0;
    }

    /**
     * Check if the player has permission to use this Quirk.
     */
    public boolean hasPermission(final Player player) {
        return player.hasPermission("mha.quirk." + type.getId());
    }

    /**
     * Execute the Quirk ability.
     * @param player The player using the Quirk
     * @return true if activation was successful
     */
    public abstract boolean activate(final Player player);

    /**
     * Stop any ongoing effects from this Quirk.
     * Override for Quirks with persistent effects.
     */
    public void deactivate(final Player player) {
        // Default: no persistent effects
    }

    /**
     * Called when this Quirk is assigned to a player.
     */
    public void onAssign(final Player player) {
        // Default: no setup needed
    }

    /**
     * Called when this Quirk is removed from a player.
     */
    public void onRemove(final Player player) {
        deactivate(player);
        cooldownTimestamps.remove(player.getUniqueId());
    }

    /**
     * Get remaining cooldown in milliseconds.
     */
    public long getCooldownRemaining(final Player player) {
        final Long lastUsed = cooldownTimestamps.get(player.getUniqueId());
        if (lastUsed == null) {
            return 0;
        }
        final long elapsed = System.currentTimeMillis() - lastUsed;
        final long remaining = getCooldown() - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Get cooldown duration in milliseconds with rarity scaling applied.
     */
    public int getCooldown() {
        final int configCooldown = config.getQuirkInt(type.getId(), "cooldown", baseCooldown);
        // Rare quirks have shorter cooldowns
        return (int) (configCooldown * getRarity().getCooldownMultiplier() / 100L);
    }

    /**
     * Start the cooldown timer for a player.
     */
    protected void startCooldown(final Player player) {
        cooldownTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

        final long cooldownTicks = Math.max(1L, getCooldown() / 50L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    TextUtil.actionBar(player, "§a[+] " + getType().getDisplayName() + " is ready!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
        }.runTaskLater(org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mha.plugin.MHAPlugin.class), cooldownTicks);
    }

    /**
     * Consume stamina from the player with rarity scaling applied.
     * @return true if stamina was successfully consumed
     */
    protected boolean consumeStamina(final Player player) {
        final int cost = getStaminaCost();
        return staminaManager.consumeStamina(player, cost);
    }

    /**
     * Get stamina cost for this Quirk with rarity scaling applied.
     * Rarer quirks cost less stamina.
     */
    public int getStaminaCost() {
        final int configCost = config.getQuirkInt(type.getId(), "stamina-cost", baseStaminaCost);
        return (int) (configCost * getRarity().getStaminaMultiplier());
    }

    /**
     * Get power multiplier from rarity.
     * Rarer quirks deal more damage.
     */
    protected double getPowerMultiplier() {
        return getRarity().getPowerMultiplier();
    }

    /**
     * Apply power scaling to a base damage value.
     */
    protected double scaleDamage(final double baseDamage) {
        return baseDamage * getPowerMultiplier();
    }

    /**
     * Reset cooldown for a player.
     */
    public void resetCooldown(final Player player) {
        cooldownTimestamps.remove(player.getUniqueId());
    }

    /**
     * Clear all cooldown data (for server shutdown).
     */
    public void clearAllCooldowns() {
        cooldownTimestamps.clear();
    }

    // Configuration helper methods

    protected int getCooldownFromConfig() {
        return config.getQuirkInt(type.getId(), "cooldown", 5000);
    }

    protected int getStaminaCostFromConfig() {
        return config.getQuirkInt(type.getId(), "stamina-cost", 10);
    }

    protected int getConfigInt(final String key, final int defaultValue) {
        return config.getQuirkInt(type.getId(), key, defaultValue);
    }

    protected double getConfigDouble(final String key, final double defaultValue) {
        return config.getQuirkDouble(type.getId(), key, defaultValue);
    }

    protected boolean getConfigBoolean(final String key, final boolean defaultValue) {
        return config.getQuirkBoolean(type.getId(), key, defaultValue);
    }
}