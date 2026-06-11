package com.mha.plugin.destruction;

import com.mha.plugin.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks environmental destruction from Quirks and restores blocks over time.
 * Uses both session-based and global tracking for reliability.
 */
public final class DestructionManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, DestructionSession> sessionsById;
    private final Map<UUID, UUID> activeSessionByPlayer;
    private final Set<String> protectedLocations;
    private final List<BlockSnapshot> globalSnapshots;
    private BukkitRunnable restoreTask;

    public DestructionManager(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.sessionsById = new ConcurrentHashMap<>();
        this.activeSessionByPlayer = new ConcurrentHashMap<>();
        this.protectedLocations = ConcurrentHashMap.newKeySet();
        this.globalSnapshots = Collections.synchronizedList(new ArrayList<>());

        startRestoreTask();
    }

    public boolean isEnabled() {
        return config.getBoolean("destruction.enabled", true);
    }

    /**
     * Start a destruction tracking session for a Quirk activation.
     */
    public DestructionSession startSession(final Player player) {
        final DestructionSession session = new DestructionSession(player.getUniqueId());
        sessionsById.put(session.getSessionId(), session);
        activeSessionByPlayer.put(player.getUniqueId(), session.getSessionId());
        return session;
    }

    /**
     * Get the active session for a player, if any.
     */
    public DestructionSession getActiveSession(final Player player) {
        final UUID sessionId = activeSessionByPlayer.get(player.getUniqueId());
        return sessionId != null ? sessionsById.get(sessionId) : null;
    }

    /**
     * Record a block before it is changed.
     * Works both with active sessions (preferred) and globally (fallback).
     */
    public void recordBlockChange(final Player player, final Block block) {
        if (!isEnabled() || block == null || isProtected(block.getLocation())) {
            return;
        }

        // Try to use active session first
        final DestructionSession session = getActiveSession(player);
        if (session != null) {
            session.recordBlock(block.getState());
        } else {
            // Fallback: Record globally for cleanup on crash
            final BlockSnapshot snapshot = new BlockSnapshot(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    block.getBlockData().clone()
            );
            globalSnapshots.add(snapshot);
        }
    }

    /**
     * End a session and schedule restoration of its snapshots.
     */
    public void endSession(final UUID sessionId) {
        final DestructionSession session = sessionsById.remove(sessionId);
        if (session == null) {
            return;
        }

        activeSessionByPlayer.remove(session.getPlayerId());

        if (!isEnabled() || session.isEmpty()) {
            return;
        }

        // Also add to global tracking for crash recovery
        globalSnapshots.addAll(session.getSnapshots());
        scheduleRestore(session);
    }

    /**
     * Immediately restore all pending destruction (session-based and global).
     */
    public int restoreAll() {
        int restored = 0;

        // Restore session-based
        for (final DestructionSession session : new ArrayList<>(sessionsById.values())) {
            restored += restoreSession(session);
            sessionsById.remove(session.getSessionId());
            activeSessionByPlayer.remove(session.getPlayerId());
        }

        // Restore global snapshots
        synchronized (globalSnapshots) {
            for (final BlockSnapshot snapshot : new ArrayList<>(globalSnapshots)) {
                try {
                    snapshot.restore();
                    restored++;
                } catch (Exception ignored) {
                }
            }
            globalSnapshots.clear();
        }

        return restored;
    }

    /**
     * Get count of pending restorations.
     */
    public int getPendingRestorationCount() {
        return sessionsById.size() + globalSnapshots.size();
    }

    public boolean protectLocation(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return protectedLocations.add(locationKey(location));
    }

    public boolean unprotectLocation(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return protectedLocations.remove(locationKey(location));
    }

    public boolean isProtected(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (protectedLocations.contains(locationKey(location))) {
            return true;
        }

        return location.getWorld().getSpawnLocation().distanceSquared(location) < 100;
    }

    public void shutdown() {
        if (restoreTask != null) {
            restoreTask.cancel();
        }
        restoreAll();
        sessionsById.clear();
        activeSessionByPlayer.clear();
        globalSnapshots.clear();
    }

    private void scheduleRestore(final DestructionSession session) {
        final long delayMinutes = config.getInt("destruction.restore-delay-minutes", 5);
        final long delayTicks = delayMinutes * 60L * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                restoreSession(session);
                // Remove from global tracking after restoration
                globalSnapshots.removeAll(session.getSnapshots());
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private int restoreSession(final DestructionSession session) {
        int restored = 0;
        for (final BlockSnapshot snapshot : session.getSnapshots()) {
            try {
                snapshot.restore();
                restored++;
            } catch (Exception ignored) {
            }
        }
        return restored;
    }

    private void startRestoreTask() {
        final int blocksPerTick = config.getInt("destruction.blocks-per-restore-tick", 5);
        if (blocksPerTick <= 0) {
            return;
        }

        restoreTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Periodic cleanup of invalid snapshots
                synchronized (globalSnapshots) {
                    globalSnapshots.removeIf(snapshot -> {
                        final World world = Bukkit.getWorld(snapshot.getLocation().getWorld().getName());
                        if (world == null) return false;
                        final Location loc = snapshot.getLocation();
                        return loc == null || loc.getBlock().getType().isAir();
                    });
                }
            }
        };
        restoreTask.runTaskTimer(plugin, 20L * 60, 20L * 60); // Check every minute
    }

    private static String locationKey(final Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
