package com.mha.plugin.destruction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Immutable snapshot of a block's state for restoration.
 */
public final class BlockSnapshot {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final BlockData blockData;

    public BlockSnapshot(final String worldName, final int x, final int y, final int z, final BlockData blockData) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockData = blockData;
    }

    public boolean matches(final BlockSnapshot other) {
        return worldName.equals(other.worldName)
                && x == other.x
                && y == other.y
                && z == other.z;
    }

    public void restore() {
        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        final Block block = world.getBlockAt(x, y, z);
        block.setBlockData(blockData, false);
    }

    public Location getLocation() {
        final World world = Bukkit.getWorld(worldName);
        return world != null ? new Location(world, x, y, z) : null;
    }
}
