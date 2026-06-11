package com.mha.plugin.quirk;

import com.mha.plugin.util.TextUtil;
import com.mha.plugin.util.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BossBar;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all Quirks.
 * Provides common functionality for cooldowns and ability activation.
 * Power scaling is applied based on Quirk rarity.
 *
 * Stamina system removed - now uses pure cooldown timers for cleaner gameplay.
 */
public abstract class Quirk {

    protected final QuirkType type;
    protected final ConfigManager config;
    protected final Map<UUID, Long> cooldownTimestamps;
    protected final Map<UUID, BossBar> cooldownBars;
    protected final int baseCooldown;

    /**
     * Create a Quirk instance.
     */
    protected Quirk(final QuirkType type, final ConfigManager config) {
        this.type = type;
        this.config = config;
        this.cooldownTimestamps = new ConcurrentHashMap<>();
        this.cooldownBars = new ConcurrentHashMap<>();
        this.baseCooldown = getCooldownFromConfig();
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
     * Check if the player can use this Quirk (cooldown only - no stamina).
     */
    public boolean canUse(final Player player) {
        if (!isEnabled()) {
            return false;
        }

        if (!hasPermission(player)) {
            return false;
        }

        final long remaining = getCooldownRemaining(player);
        if (remaining > 0) {
            // Show cooldown message via actionbar
            final double secondsLeft = remaining / 1000.0;
            TextUtil.actionBar(player, "§cAbility on cooldown! Wait " + String.format("%.1f", secondsLeft) + "s");
            return false;
        }

        return true;
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

        // Remove boss bar if exists
        final BossBar bar = cooldownBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
        }
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
     * Start the cooldown timer for a player with visual boss bar.
     */
    protected void startCooldown(final Player player) {
        cooldownTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

        final long cooldownMs = getCooldown();
        final long cooldownTicks = Math.max(1L, cooldownMs / 50L);
        final JavaPlugin plugin = JavaPlugin.getPlugin(com.mha.plugin.MHAPlugin.class);

        // Create boss bar for cooldown display
        final BossBar bar;
        if (config.getBoolean("settings.cooldown-display", true)) {
            bar = plugin.getServer().createBossBar(
                    "§c" + getType().getDisplayName() + " cooldown",
                    BarColor.BLUE,
                    BarStyle.SEGMENTED_10
            );
            bar.setProgress(1.0);
            bar.addPlayer(player);
            cooldownBars.put(player.getUniqueId(), bar);

            // Update boss bar during cooldown
            new BukkitRunnable() {
                long elapsed = 0;

                @Override
                public void run() {
                    elapsed += 50; // 50ms per tick
                    final double progress = 1.0 - ((double) elapsed / cooldownMs);

                    if (!player.isOnline() || progress <= 0) {
                        bar.removePlayer(player);
                        cooldownBars.remove(player.getUniqueId());
                        cancel();
                        return;
                    }

                    bar.setProgress(Math.max(0, progress));
                    bar.setTitle("§b" + getType().getDisplayName() + " §7- §e" + String.format("%.1f", (cooldownMs - elapsed) / 1000.0) + "s");
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } else {
            bar = null;
        }

        // Notify when ready
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    TextUtil.actionBar(player, "§a[+] " + getType().getDisplayName() + " is ready!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
        }.runTaskLater(plugin, cooldownTicks);
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

        final BossBar bar = cooldownBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    /**
     * Clear all cooldown data (for server shutdown).
     */
    public void clearAllCooldowns() {
        cooldownTimestamps.clear();

        for (final BossBar bar : cooldownBars.values()) {
            bar.removeAll();
        }
        cooldownBars.clear();
    }

    // Configuration helper methods

    protected int getCooldownFromConfig() {
        return config.getQuirkInt(type.getId(), "cooldown", 5000);
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