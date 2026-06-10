package com.mha.plugin.reputation;

import com.mha.plugin.util.ConfigManager;
import com.mha.plugin.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

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

    private static final String TEAM_HERO = "mha_hero";
    private static final String TEAM_VILLAIN = "mha_villain";
    private static final String TEAM_NEUTRAL = "mha_neutral";

    public ReputationManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.playerReputation = new ConcurrentHashMap<>();
        this.killCooldowns = new ConcurrentHashMap<>();
        reloadThresholds();
        setupAlignmentTeams();

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

    /**
     * Get a player's chosen alignment (Hero / Villain / Undecided).
     */
    public Alignment getAlignment(final Player player) {
        final ReputationState state = playerReputation.get(player.getUniqueId());
        return state != null ? state.getAlignment() : Alignment.UNDECIDED;
    }

    /**
     * Whether the player has already picked a side.
     */
    public boolean hasChosenAlignment(final Player player) {
        return getAlignment(player).isChosen();
    }

    /**
     * Set a player's alignment, persist it, refresh their coloured name tag and
     * let them know which side they picked.
     */
    public void setAlignment(final Player player, final Alignment alignment) {
        final ReputationState state = getOrCreateState(player);
        state.setAlignment(alignment);
        config.set("reputation." + player.getUniqueId() + ".alignment", alignment.getId());
        config.saveConfig();

        applyNameTag(player, alignment);
        updatePlayerDisplay(player, state);

        if (alignment == Alignment.HERO) {
            player.sendMessage("§9§l⚔ You have pledged yourself as a HERO!");
            player.sendMessage("§7Protect the innocent and hunt down villains for bounties.");
        } else if (alignment == Alignment.VILLAIN) {
            player.sendMessage("§c§l☠ You have embraced the path of a VILLAIN!");
            player.sendMessage("§7Wreak havoc — but beware, Heroes can claim a bounty on your head.");
        }
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
        applyNameTag(player, state.getAlignment());
        TextUtil.actionBar(player, "§eReputation: " + rank.getDisplayColor() + rank.getDisplayName() + " §7(" + state.getTotalScore() + ")");
    }

    /**
     * Create (or refresh) the scoreboard teams used to colour the above-head and
     * tab-list name tags by alignment.
     */
    private void setupAlignmentTeams() {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        final Scoreboard board = manager.getMainScoreboard();
        ensureTeam(board, TEAM_HERO, ChatColor.BLUE, "§9[Hero] ");
        ensureTeam(board, TEAM_VILLAIN, ChatColor.RED, "§c[Villain] ");
        ensureTeam(board, TEAM_NEUTRAL, ChatColor.GRAY, "");
    }

    private Team ensureTeam(final Scoreboard board, final String name, final ChatColor color, final String prefix) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.setColor(color);
        team.setPrefix(prefix);
        return team;
    }

    /**
     * Put the player into the scoreboard team matching their alignment so their
     * name tag shows blue (Hero) or red (Villain).
     */
    private void applyNameTag(final Player player, final Alignment alignment) {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        final Scoreboard board = manager.getMainScoreboard();
        final String entry = player.getName();

        // Remove from any of our teams first so a re-pick doesn't keep stale colours.
        for (final String teamName : new String[]{TEAM_HERO, TEAM_VILLAIN, TEAM_NEUTRAL}) {
            final Team team = board.getTeam(teamName);
            if (team != null && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }

        final String target = switch (alignment) {
            case HERO -> TEAM_HERO;
            case VILLAIN -> TEAM_VILLAIN;
            case UNDECIDED -> TEAM_NEUTRAL;
        };
        Team team = board.getTeam(target);
        if (team == null) {
            setupAlignmentTeams();
            team = board.getTeam(target);
        }
        if (team != null) {
            team.addEntry(entry);
        }
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
        state.setAlignment(Alignment.fromId(config.getString("reputation." + playerId + ".alignment", "undecided")));
        state.setLastRank(rankCalculator.getRank(state.getTotalScore()));
        state.setDirty(false);
        playerReputation.put(playerId, state);
        updatePlayerDisplay(player, state);
    }

    public void saveAllReputation() {
        for (final Map.Entry<UUID, ReputationState> entry : playerReputation.entrySet()) {
            final ReputationState state = entry.getValue();
            final String path = "reputation." + entry.getKey();
            config.set(path + ".hero-points", state.getHeroPoints());
            config.set(path + ".villain-points", state.getVillainPoints());
            config.set(path + ".alignment", state.getAlignment().getId());
        }
        config.saveConfig();
    }
}
