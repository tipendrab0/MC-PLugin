package com.mha.plugin.stamina;

import com.mha.plugin.util.ConfigManager;
import com.mha.plugin.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player stamina throughout the server.
 * Tracks energy usage, regeneration, and exhaustion states.
 */
public final class StaminaManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, StaminaState> playerStamina;
    private final int maxStamina;
    private final int regenRate;
    private final int exhaustedThreshold;
    private final int warningThreshold;
    private final long regenDelay;
    private final Map<UUID, Long> lastUsedTime;

    public StaminaManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.playerStamina = new ConcurrentHashMap<>();
        this.lastUsedTime = new ConcurrentHashMap<>();

        this.maxStamina = config.getInt("stamina.max", 100);
        this.regenRate = config.getInt("stamina.regen-rate", 1);
        this.exhaustedThreshold = config.getInt("stamina.exhausted-threshold", 10);
        this.warningThreshold = config.getInt("stamina.warning-threshold", 25);
        this.regenDelay = config.getLong("stamina.regen-delay", 2000);

        startRegenerationTask();
    }

    /**
     * Create or get stamina state for a player.
     */
    public StaminaState getOrCreateStamina(final Player player) {
        return playerStamina.computeIfAbsent(player.getUniqueId(), uuid ->
                new StaminaState(uuid, maxStamina));
    }

    /**
     * Get current stamina for a player (0 if not found or null).
     */
    public int getStamina(final Player player) {
        if (player == null) {
            return 0;
        }
        final StaminaState state = playerStamina.get(player.getUniqueId());
        return state != null ? state.getCurrentStamina() : 0;
    }

    /**
     * Get max stamina value.
     */
    public int getMaxStamina() {
        return maxStamina;
    }

    /**
     * Check if player can afford stamina cost.
     */
    public boolean canAfford(final Player player, final int cost) {
        if (player == null) {
            return false;
        }
        return getStamina(player) >= cost;
    }

    /**
     * Consume stamina from a player.
     * @return true if successful, false if insufficient stamina or null player
     */
    public boolean consumeStamina(final Player player, final int amount) {
        if (player == null) {
            return false;
        }
        final StaminaState state = getOrCreateStamina(player);

        if (state.getCurrentStamina() < amount) {
            return false;
        }

        state.decrease(amount);
        lastUsedTime.put(player.getUniqueId(), System.currentTimeMillis());

        checkWarning(player);

        return true;
    }

    /**
     * Restore stamina to a player.
     */
    public void restoreStamina(final Player player, final int amount) {
        final StaminaState state = getOrCreateStamina(player);
        state.increase(amount);
    }

    /**
     * Set player's stamina to a specific value.
     */
    public void setStamina(final Player player, final int value) {
        final StaminaState state = getOrCreateStamina(player);
        state.setStamina(value);
    }

    /**
     * Reset player's stamina to max.
     */
    public void resetStamina(final Player player) {
        final StaminaState state = getOrCreateStamina(player);
        state.setStamina(maxStamina);
    }

    /**
     * Check if player is exhausted (below exhaustion threshold).
     */
    public boolean isExhausted(final Player player) {
        if (player == null) {
            return true; // Treat null as exhausted to prevent actions
        }
        return getStamina(player) <= exhaustedThreshold;
    }

    /**
     * Check if player has low stamina (below warning threshold).
     */
    public boolean hasLowStamina(final Player player) {
        if (player == null) {
            return true;
        }
        return getStamina(player) <= warningThreshold;
    }

    /**
     * Remove stamina data for a player (on disconnect).
     */
    public void removePlayer(final UUID playerId) {
        playerStamina.remove(playerId);
        lastUsedTime.remove(playerId);
    }

    /**
     * Get stamina percentage as an integer (0-100).
     */
    public int getStaminaPercent(final Player player) {
        return (int) ((getStamina(player) * 100.0) / maxStamina);
    }

    /**
     * Get the stamina state for display purposes.
     * @return FULL, NORMAL, LOW, or EXHAUSTED
     */
    public StaminaState.State getState(final Player player) {
        final int stamina = getStamina(player);
        final int percent = getStaminaPercent(player);

        if (stamina <= exhaustedThreshold) {
            return StaminaState.State.EXHAUSTED;
        } else if (percent < 40) {
            return StaminaState.State.LOW;
        } else if (percent < 80) {
            return StaminaState.State.NORMAL;
        } else {
            return StaminaState.State.FULL;
        }
    }

    private BukkitRunnable regenTask;

    /**
     * Start the regeneration task that runs every second.
     */
    private void startRegenerationTask() {
        regenTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (final Map.Entry<UUID, StaminaState> entry : playerStamina.entrySet()) {
                    final Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        continue;
                    }

                    // Skip creative/spectator mode
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }

                    // Check regen delay
                    final Long lastUsed = lastUsedTime.get(entry.getKey());
                    if (lastUsed != null && System.currentTimeMillis() - lastUsed < regenDelay) {
                        continue;
                    }

                    final StaminaState state = entry.getValue();
                    if (state.getCurrentStamina() < maxStamina) {
                        state.increase(regenRate);
                    }
                }
            }
        };
        regenTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Shutdown the stamina manager - cancel tasks and clear data.
     */
    public void shutdown() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        playerStamina.clear();
        lastUsedTime.clear();
    }

    /**
     * Check and send warning if stamina is low.
     */
    private void checkWarning(final Player player) {
        if (isExhausted(player)) {
            TextUtil.actionBar(player, config.getString("messages.prefix", "") + config.getString("messages.exhausted", "Exhausted!"));
        } else if (hasLowStamina(player)) {
            final String msg = config.getString("messages.low-stamina", "Warning: Low stamina")
                    .replace("%stamina%", String.valueOf(getStamina(player)));
            TextUtil.actionBar(player, msg);
        }
    }
}
