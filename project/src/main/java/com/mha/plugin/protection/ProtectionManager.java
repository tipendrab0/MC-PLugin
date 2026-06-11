package com.mha.plugin.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard integration for protecting regions from Quirk effects.
 * Gracefully degrades if WorldGuard is not installed.
 */
public final class ProtectionManager {

    private static boolean worldGuardEnabled = false;
    private static WorldGuardHandler handler = null;

    static {
        // Check if WorldGuard is available at runtime
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardEnabled = true;
            handler = new WorldGuardHandlerImpl();
        } catch (ClassNotFoundException e) {
            worldGuardEnabled = false;
            handler = new NoOpHandler();
        }
    }

    /**
     * Check if WorldGuard is installed and enabled.
     */
    public static boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    /**
     * Check if a player can break blocks at a location.
     * Returns true if no protection or player has permission.
     */
    public static boolean canBreakBlock(final Player player, final Location location) {
        return handler.canBreakBlock(player, location);
    }

    /**
     * Check if a player can use quirk abilities at a location.
     * Returns true if no protection or player has permission.
     */
    public static boolean canUseQuirk(final Player player, final Location location) {
        return handler.canUseQuirk(player, location);
    }

    /**
     * Check if explosions are allowed at a location.
     */
    public static boolean canExplode(final Location location) {
        return handler.canExplode(location);
    }

    /**
     * Check if entity damage is allowed at a location.
     */
    public static boolean canDamageEntity(final Player source, final Location location) {
        return handler.canDamageEntity(source, location);
    }

    // Handler interface
    private interface WorldGuardHandler {
        boolean canBreakBlock(Player player, Location location);
        boolean canUseQuirk(Player player, Location location);
        boolean canExplode(Location location);
        boolean canDamageEntity(Player source, Location location);
    }

    /**
     * No-op handler when WorldGuard is not installed.
     */
    private static final class NoOpHandler implements WorldGuardHandler {
        @Override public boolean canBreakBlock(Player player, Location location) { return true; }
        @Override public boolean canUseQuirk(Player player, Location location) { return true; }
        @Override public boolean canExplode(Location location) { return true; }
        @Override public boolean canDamageEntity(Player source, Location location) { return true; }
    }

    /**
     * WorldGuard implementation using reflection to avoid compile dependency.
     */
    private static final class WorldGuardHandlerImpl implements WorldGuardHandler {

        @Override
        public boolean canBreakBlock(final Player player, final Location location) {
            try {
                final Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                final Object instance = worldGuardClass.getMethod("getInstance").invoke(null);
                final Object platform = worldGuardClass.getMethod("getPlatform").invoke(instance);
                final Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                final Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

                final Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                final Object world = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class)
                        .invoke(null, location.getWorld());
                final Object loc = bukkitAdapterClass.getMethod("adapt", Location.class)
                        .invoke(null, location);

                final Class<?> flagRegistryClass = Class.forName("com.sk89q.worldguard.protection.flags.registry.FlagRegistry");
                final Object flagRegistry = worldGuardClass.getMethod("getFlagRegistry").invoke(instance);

                // Check BUILD flag
                final Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                final Object buildFlag = flagsClass.getField("BUILD").get(null);

                final Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
                final Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
                final Object allow = stateClass.getField("ALLOW").get(null);

                final Object result = query.getClass().getMethod("queryState", loc.getClass(), stateFlagClass)
                        .invoke(query, loc, stateFlagClass.cast(buildFlag));

                // If result is null, region has no flag set (allow by default)
                return result == null || result.equals(allow);
            } catch (Exception e) {
                return true;
            }
        }

        @Override
        public boolean canUseQuirk(final Player player, final Location location) {
            return canBreakBlock(player, location);
        }

        @Override
        public boolean canExplode(final Location location) {
            try {
                final Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                final Object instance = worldGuardClass.getMethod("getInstance").invoke(null);
                final Object platform = worldGuardClass.getMethod("getPlatform").invoke(instance);
                final Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                final Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

                final Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                final Object world = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class)
                        .invoke(null, location.getWorld());
                final Object loc = bukkitAdapterClass.getMethod("adapt", Location.class)
                        .invoke(null, location);

                // Check TNT flag for explosions
                final Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                final Object tntFlag = flagsClass.getField("TNT").get(null);

                final Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
                final Class<?> stateClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
                final Object allow = stateClass.getField("ALLOW").get(null);

                final Object result = query.getClass().getMethod("queryState", loc.getClass(), stateFlagClass)
                        .invoke(query, loc, stateFlagClass.cast(tntFlag));

                return result == null || result.equals(allow);
            } catch (Exception e) {
                return true;
            }
        }

        @Override
        public boolean canDamageEntity(final Player source, final Location location) {
            return canBreakBlock(source, location);
        }
    }
}
