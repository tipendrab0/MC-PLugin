package com.mha.plugin.quirk;

import com.mha.plugin.quirk.impl.*;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Manages Quirk registration, assignment, and player-Quirk mapping.
 */
public final class QuirkManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final StaminaManager staminaManager;
    private final Map<QuirkType, Quirk> quirks;
    private final Map<UUID, QuirkType> playerQuirks;
    private final Set<UUID> awakeningInProgress;

    public QuirkManager(final JavaPlugin plugin, final ConfigManager config, final StaminaManager staminaManager) {
        this.plugin = plugin;
        this.config = config;
        this.staminaManager = staminaManager;
        this.quirks = new HashMap<>();
        this.playerQuirks = new HashMap<>();
        this.awakeningInProgress = new HashSet<>();

        registerQuirks();
        loadPlayerQuirks();
    }

    /**
     * Register all Quirks.
     */
    private void registerQuirks() {
        // COMMON Quirks
        registerQuirk(new PopOffQuirk(config, staminaManager));
        registerQuirk(new NavelLaserQuirk(config, staminaManager));
        registerQuirk(new EngineQuirk(config, staminaManager));
        registerQuirk(new FrogQuirk(config, staminaManager));

        // UNCOMMON Quirks
        registerQuirk(new ExplosionQuirk(config, staminaManager));
        registerQuirk(new ZeroGravityQuirk(config, staminaManager));
        registerQuirk(new TransformationQuirk(config, staminaManager));

        // RARE Quirks
        registerQuirk(new IceFireQuirk(config, staminaManager));
        registerQuirk(new HardeningQuirk(config, staminaManager));
        registerQuirk(new ElectrificationQuirk(config, staminaManager));
        registerQuirk(new WaveMotionQuirk(config, staminaManager));

        // EPIC Quirks
        registerQuirk(new CremationQuirk(config, staminaManager));
        registerQuirk(new CreationQuirk(config, staminaManager));
        registerQuirk(new PermeationQuirk(config, staminaManager));
        registerQuirk(new BloodcurdleQuirk(config, staminaManager));
        // LEGENDARY Quirks
        registerQuirk(new OneForAllQuirk(config, staminaManager));
        registerQuirk(new DecayQuirk(config, staminaManager));
        registerQuirk(new BlackwhipQuirk(config, staminaManager));
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
        return assignQuirk(player, chosen);
    }

    /**
     * Assign a Quirk to a player, announcing it in chat.
     */
    public QuirkType assignQuirk(final Player player, final QuirkType type) {
        return assignQuirk(player, type, true);
    }

    /**
     * Assign a Quirk to a player.
     *
     * @param announce when {@code true}, send the chat announcement. The awakening
     *                 ceremony already reveals the Quirk dramatically, so it assigns
     *                 silently to avoid duplicate "you got the Quirk" chat messages.
     */
    public QuirkType assignQuirk(final Player player, final QuirkType type, final boolean announce) {
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

        if (announce) {
            // Send rarity-themed message
            final String rarityColor = getRarityColor(type.getRarity());
            player.sendMessage(rarityColor + "You have been assigned the " + type.getDisplayName() + " Quirk!");
            player.sendMessage("§7Rarity: " + rarityColor + type.getRarity().getDisplayName() + " §7- " + type.getRarity().getDescription());
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
        staminaManager.removePlayer(playerId);
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
