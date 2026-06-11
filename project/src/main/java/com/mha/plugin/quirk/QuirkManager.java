package com.mha.plugin.quirk;

import com.mha.plugin.quirk.impl.*;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Manages Quirk registration, assignment, and player-Quirk mapping.
 * Stamina system removed - pure cooldown timers now.
 */
public final class QuirkManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<QuirkType, Quirk> quirks;
    private final Map<UUID, QuirkType> playerQuirks;
    private final Set<UUID> awakeningInProgress;
    private final Map<UUID, Long> globalCooldowns; // Combo cooldown system

    public QuirkManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.quirks = new HashMap<>();
        this.playerQuirks = new HashMap<>();
        this.awakeningInProgress = new HashSet<>();
        this.globalCooldowns = new ConcurrentHashMap<>();

        registerQuirks();
        loadPlayerQuirks();
    }

    /**
     * Register all Quirks.
     */
    private void registerQuirks() {
        // COMMON Quirks
        registerQuirk(new PopOffQuirk(config));
        registerQuirk(new NavelLaserQuirk(config));
        registerQuirk(new EngineQuirk(config));
        registerQuirk(new FrogQuirk(config));

        // UNCOMMON Quirks
        registerQuirk(new ExplosionQuirk(config));
        registerQuirk(new ZeroGravityQuirk(config));
        registerQuirk(new TransformationQuirk(config));

        // RARE Quirks
        registerQuirk(new IceFireQuirk(config));
        registerQuirk(new HardeningQuirk(config));
        registerQuirk(new ElectrificationQuirk(config));
        registerQuirk(new WaveMotionQuirk(config));

        // EPIC Quirks
        registerQuirk(new CremationQuirk(config));
        registerQuirk(new CreationQuirk(config));
        registerQuirk(new PermeationQuirk(config));
        registerQuirk(new BloodcurdleQuirk(config));

        // LEGENDARY Quirks
        registerQuirk(new OneForAllQuirk(config));
        registerQuirk(new DecayQuirk(config));
        registerQuirk(new BlackwhipQuirk(config));
    }

    /**
     * Register a Quirk implementation.
     */
    public void registerQuirk(final Quirk quirk) {
        quirks.put(quirk.getType(), quirk);
        plugin.getLogger().info("Registered Quirk [" + quirk.getType().getRarity().name() + "] " + quirk.getName());
    }

    /**
     * Get a Quirk by its type.
     */
    public Quirk getQuirk(final QuirkType type) {
        return quirks.get(type);
    }

    /**
     * Get a Quirk by its string ID.
     */
    public Quirk getQuirk(final String id) {
        final QuirkType type = QuirkType.fromId(id);
        return type != QuirkType.NONE ? quirks.get(type) : null;
    }

    /**
     * Get all registered Quirks.
     */
    public Collection<Quirk> getAllQuirks() {
        return quirks.values();
    }

    /**
     * Get all available (enabled) Quirk types for random assignment.
     */
    public List<QuirkType> getAvailableQuirkTypes() {
        return quirks.values().stream()
                .filter(Quirk::isEnabled)
                .map(Quirk::getType)
                .filter(QuirkType::isPlayable)
                .collect(Collectors.toList());
    }

    /**
     * Pick a random Quirk type without assigning it (used by awakening ceremony).
     */
    public QuirkType pickRandomQuirkType() {
        final QuirkRarity rarity = QuirkRarity.getRandomWeighted();
        final List<QuirkType> quirksOfRarity = getAvailableQuirkTypes().stream()
                .filter(t -> t.getRarity() == rarity)
                .collect(Collectors.toList());

        if (!quirksOfRarity.isEmpty()) {
            return quirksOfRarity.get(ThreadLocalRandom.current().nextInt(quirksOfRarity.size()));
        }

        final List<QuirkType> available = getAvailableQuirkTypes();
        if (available.isEmpty()) {
            return QuirkType.NONE;
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    /**
     * Assign a random Quirk to a player (weighted by rarity).
     */
    public QuirkType assignRandomQuirk(final Player player) {
        final QuirkType chosen = pickRandomQuirkType();
        if (chosen == QuirkType.NONE) {
            return QuirkType.NONE;
        }
        return assignQuirk(player, chosen, false);
    }

    /**
     * Assign a Quirk to a player with messages.
     */
    public QuirkType assignQuirk(final Player player, final QuirkType type) {
        return assignQuirk(player, type, false);
    }

    /**
     * Assign a Quirk to a player.
     * @param silent If true, no chat messages (used by awakening ceremony which has its own display)
     */
    public QuirkType assignQuirk(final Player player, final QuirkType type, final boolean silent) {
        if (type == QuirkType.NONE) {
            removeQuirk(player);
            return QuirkType.NONE;
        }

        final Quirk quirk = quirks.get(type);
        if (quirk == null || !quirk.isEnabled()) {
            return QuirkType.NONE;
        }

        // Remove existing Quirk first
        removeQuirk(player);

        playerQuirks.put(player.getUniqueId(), type);
        config.setPlayerQuirk(player.getUniqueId(), type.getId());

        quirk.onAssign(player);

        // Only send message if not silent (ceremony has its own display)
        if (!silent) {
            final String rarityColor = getRarityColor(type.getRarity());
            player.sendTitle(rarityColor + "QUIRK ASSIGNED!", "§f" + type.getDisplayName(), 10, 40, 10);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }

        return type;
    }

    /**
     * Check if a player has a Quirk assigned.
     */
    public boolean hasQuirk(final Player player) {
        return playerQuirks.get(player.getUniqueId()) != null;
    }

    /**
     * Check if awakening is in progress for a player.
     */
    public boolean isAwakeningInProgress(final UUID playerId) {
        return awakeningInProgress.contains(playerId);
    }

    /**
     * Set awakening in progress status.
     */
    public void setAwakeningInProgress(final UUID playerId, final boolean inProgress) {
        if (inProgress) {
            awakeningInProgress.add(playerId);
        } else {
            awakeningInProgress.remove(playerId);
        }
    }

    /**
     * Get a player's assigned Quirk type.
     */
    public QuirkType getPlayerQuirkType(final Player player) {
        return playerQuirks.getOrDefault(player.getUniqueId(), QuirkType.NONE);
    }

    /**
     * Get a player's Quirk implementation.
     */
    public Quirk getPlayerQuirk(final Player player) {
        final QuirkType type = getPlayerQuirkType(player);
        return type != QuirkType.NONE ? quirks.get(type) : null;
    }

    /**
     * Remove a player's Quirk.
     */
    public void removeQuirk(final Player player) {
        final QuirkType current = playerQuirks.remove(player.getUniqueId());
        if (current != QuirkType.NONE && current != null) {
            final Quirk quirk = quirks.get(current);
            if (quirk != null) {
                quirk.onRemove(player);
            }
            config.removePlayerQuirk(player.getUniqueId());
        }
    }

    /**
     * Load player Quirk assignments from config.
     */
    private void loadPlayerQuirks() {
        final Map<UUID, String> saved = config.getPlayerQuirksMap();
        for (final Map.Entry<UUID, String> entry : saved.entrySet()) {
            final QuirkType type = QuirkType.fromId(entry.getValue());
            if (type != QuirkType.NONE) {
                playerQuirks.put(entry.getKey(), type);
            }
        }
        plugin.getLogger().info("Loaded " + saved.size() + " player Quirk assignments");
    }

    /**
     * Save all player Quirk assignments.
     */
    public void saveQuirkAssignments() {
        for (final Map.Entry<UUID, QuirkType> entry : playerQuirks.entrySet()) {
            config.setPlayerQuirk(entry.getKey(), entry.getValue().getId());
        }
        config.saveConfig();
    }

    /**
     * Clear player data on disconnect.
     */
    public void onPlayerQuit(final UUID playerId) {
        awakeningInProgress.remove(playerId);
    }

    /**
     * Cleanup all stored data.
     */
    public void shutdown() {
        // Special cleanup for Quirks with active tasks
        final Quirk zeroGravity = quirks.get(QuirkType.ZERO_GRAVITY);
        if (zeroGravity instanceof ZeroGravityQuirk zgQuirk) {
            zgQuirk.shutdown();
        }

        final Quirk hardening = quirks.get(QuirkType.HARDENING);
        if (hardening instanceof HardeningQuirk hQuirk) {
            hQuirk.shutdown();
        }

        final Quirk permeation = quirks.get(QuirkType.PERMEATION);
        if (permeation instanceof PermeationQuirk pQuirk) {
            pQuirk.shutdown();
        }

        for (final Quirk quirk : quirks.values()) {
            quirk.clearAllCooldowns();
        }
        quirks.clear();
        playerQuirks.clear();
        awakeningInProgress.clear();
    }

    /**
     * Get color for rarity.
     */
    private String getRarityColor(final QuirkRarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatColor.WHITE.toString();
            case UNCOMMON -> ChatColor.GREEN.toString();
            case RARE -> ChatColor.AQUA.toString();
            case EPIC -> ChatColor.LIGHT_PURPLE.toString();
            case LEGENDARY -> ChatColor.GOLD.toString();
        };
    }
}
