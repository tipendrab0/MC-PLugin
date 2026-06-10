package com.mha.plugin.destruction;

import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks block changes made during a single Quirk activation.
 */
public final class DestructionSession {

    private final UUID sessionId;
    private final UUID playerId;
    private final long startedAt;
    private final List<BlockSnapshot> snapshots;

    public DestructionSession(final UUID playerId) {
        this.sessionId = UUID.randomUUID();
        this.playerId = playerId;
        this.startedAt = System.currentTimeMillis();
        this.snapshots = new ArrayList<>();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public List<BlockSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * Record a block change before it is modified.
     */
    public void recordBlock(final BlockState state) {
        if (state == null || state.getWorld() == null) {
            return;
        }

        final BlockSnapshot snapshot = new BlockSnapshot(
                state.getWorld().getName(),
                state.getX(),
                state.getY(),
                state.getZ(),
                state.getBlockData().clone()
        );

        if (snapshots.stream().noneMatch(s -> s.matches(snapshot))) {
            snapshots.add(snapshot);
        }
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }
}
