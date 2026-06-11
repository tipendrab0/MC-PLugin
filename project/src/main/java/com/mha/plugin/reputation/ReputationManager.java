package com.mha.plugin.reputation;

import com.mha.plugin.util.ConfigManager;
import com.mha.plugin.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Hero Society reputation mechanics.
 * Tracks Hero Points and Villain Points for moral alignment.
 */
public final class ReputationManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, ReputationState> playerReputation;
    private final Map<UUID, Long> killCooldowns;
    private ReputationRankCalculator rankCalculator;
    private static final int KILL_COOLDOWN_MS = 1000;
    private static final int AUTO_SAVE_INTERVAL = 300 * 20;

    public ReputationManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.playerReputation = new ConcurrentHashMap<>();
        this.killCooldowns = new ConcurrentHashMap<>();
        reloadThresholds();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllReputation();
            }
        }.runTaskTimer(plugin, AUTO_SAVE_INTERVAL, AUTO_SAVE_INTERVAL);

        plugin.getLogger().info("Reputation system initialized");
    }

    public void reloadThresholds() {
        this.rankCalculator = new ReputationRankCalculator(
                config.getInt("reputation.hero.thresholds.hero", 50),
                config.getInt("reputation.hero.thresholds.pro-hero", 200),
                config.getInt("reputation.villain.thresholds.villain", -30),
                config.getInt("reputation.villain.thresholds.supervillain", -100)
        );
    }

    public void addHeroPoints(final Player player, final int points) {
        final ReputationState state = getOrCreateState(player);
        state.addHeroPoints(points);
        checkRankChange(player, state);
        updatePlayerDisplay(player, state);
    }

    public void addVillainPoints(final Player player, final int points) {
        final ReputationState state = getOrCreateState(player);
        state.addVillainPoints(points);
        checkRankChange(player, state);
        updatePlayerDisplay(player, state);
    }

    public int getReputationScore(final Player player) {
        final ReputationState state = playerReputation.get(player.getUniqueId());
        return state != null ? state.getTotalScore() : 0;
    }

    public ReputationRank getRank(final Player player) {
        return rankCalculator.getRank(getReputationScore(player));
    }

    public boolean canUseUltimateQuirk(final Player player) {
        if (player == null) {
            return false;
        }
        return rankCalculator.canUseUltimate(getReputationScore(player));
    }

    private ReputationState getOrCreateState(final Player player) {
        return playerReputation.computeIfAbsent(player.getUniqueId(), uuid -> new ReputationState(uuid));
    }

    private void checkRankChange(final Player player, final ReputationState state) {
        final ReputationRank newRank = getRank(player);
        final ReputationRank oldRank = state.getLastRank();

        if (newRank != oldRank && oldRank != ReputationRank.UNKNOWN) {
            if (newRank.isHeroic()) {
                player.sendMessage("§6§l★ PROMOTION! §eYou are now a " + newRank.getDisplayName() + "§e!");
                player.sendTitle("§6★ Hero Rank Up!", newRank.getDisplayColor() + newRank.getDisplayName(), 10, 60, 10);
            } else if (newRank.isVillainous()) {
                player.sendMessage("§4§l⚠ WARNING! §cYou have fallen to " + newRank.getDisplayName() + "§c!");
                player.sendTitle("§4⚠ Rank Lost!", "§c" + newRank.getDisplayName(), 10, 60, 10);
            }
        }

        state.setLastRank(newRank);
    }

    private void updatePlayerDisplay(final Player player, final ReputationState state) {
        final ReputationRank rank = getRank(player);
        player.setDisplayName(rank.getDisplayColor() + rank.getChatPrefix() + player.getName() + "§r");
        TextUtil.actionBar(player, "§eReputation: " + rank.getDisplayColor() + rank.getDisplayName() + " §7(" + state.getTotalScore() + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!config.getBoolean("reputation.enabled", true)) {
            return;
        }
        if (!(event.getEntity().getKiller() instanceof Player killer)) {
            return;
        }

        final UUID killerId = killer.getUniqueId();
        final Long lastKill = killCooldowns.get(killerId);
        if (lastKill != null && System.currentTimeMillis() - lastKill < KILL_COOLDOWN_MS) {
            return;
        }
        killCooldowns.put(killerId, System.currentTimeMillis());

        final int heroPoints = getHeroPointsForKill(event.getEntityType());
        if (heroPoints > 0) {
            addHeroPoints(killer, heroPoints);
        }
    }

    private int getHeroPointsForKill(final EntityType type) {
        final String path = "reputation.hero.points-per-monster-kill." + type.name().toLowerCase().replace('_', '-');
        final int configured = config.getInt(path, -1);
        if (configured >= 0) {
            return configured;
        }

        return switch (type) {
            case ZOMBIE, SKELETON, SPIDER, CAVE_SPIDER -> 1;
            case CREEPER, ENDERMAN, WITCH -> 2;
            case BLAZE, GHAST, MAGMA_CUBE -> 3;
            case WITHER_SKELETON, SHULKER -> 4;
            case ELDER_GUARDIAN -> 10;
            case WITHER -> 25;
            case ENDER_DRAGON -> 50;
            case WARDEN -> 30;
            default -> 0;
        };
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        loadPlayerReputation(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final ReputationState state = playerReputation.get(event.getPlayer().getUniqueId());
        if (state != null) {
            state.setDirty(false);
        }
    }

    private void loadPlayerReputation(final Player player) {
        final UUID playerId = player.getUniqueId();
        final ReputationState state = new ReputationState(playerId);
        state.setHeroPoints(config.getInt("reputation." + playerId + ".hero-points", 0));
        state.setVillainPoints(config.getInt("reputation." + playerId + ".villain-points", 0));
        state.setLastRank(rankCalculator.getRank(state.getTotalScore()));
        playerReputation.put(playerId, state);
        updatePlayerDisplay(player, state);
    }

    public void saveAllReputation() {
        for (final Map.Entry<UUID, ReputationState> entry : playerReputation.entrySet()) {
            final ReputationState state = entry.getValue();
            final String path = "reputation." + entry.getKey();
            config.set(path + ".hero-points", state.getHeroPoints());
            config.set(path + ".villain-points", state.getVillainPoints());
        }
        config.saveConfig();
    }

    /**
     * Get all reputation states (for leaderboards).
     */
    public Map<UUID, ReputationState> getAllReputations() {
        return playerReputation;
    }

    /**
     * Get rank from a reputation score directly.
     */
    public ReputationRank getRankByScore(final int score) {
        return rankCalculator.getRank(score);
    }
}
