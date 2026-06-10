package com.mha.plugin.awakening;

import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkRarity;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the Quirk Awakening ceremony when a player first receives their Quirk.
 * Creates a dramatic visual sequence with particles, sounds, and effects.
 */
public final class QuirkAwakener {

    private final JavaPlugin plugin;
    private final QuirkManager quirkManager;
    private final ConfigManager configManager;
    private final Set<UUID> awakenedPlayers;
    private final Map<UUID, BukkitRunnable> activeCeremonies;

    private static final int CEREMONY_DURATION = 100; // 5 seconds in ticks
    private static final double PARTICLE_INTENSITY = 4.0;

    public QuirkAwakener(final JavaPlugin plugin, final QuirkManager quirkManager, final ConfigManager configManager) {
        this.plugin = plugin;
        this.quirkManager = quirkManager;
        this.configManager = configManager;
        this.awakenedPlayers = ConcurrentHashMap.newKeySet();
        this.activeCeremonies = new ConcurrentHashMap<>();
    }

    /**
     * Check if a player has already awakened a Quirk.
     */
    public boolean hasAwakened(final UUID playerId) {
        return awakenedPlayers.contains(playerId);
    }

    /**
     * Mark a player as awakened.
     */
    public void markAwakened(final UUID playerId) {
        awakenedPlayers.add(playerId);
        configManager.set("awakened." + playerId.toString(), true);
        configManager.saveConfig();
    }

    /**
     * Load awakened players from config.
     */
    public void loadAwakenedPlayers() {
        final ConfigurationSection section = configManager.getConfig().getConfigurationSection("awakened");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                if (section.getBoolean(key)) {
                    awakenedPlayers.add(UUID.fromString(key));
                }
            }
        }
    }

    /**
     * Start the Quirk awakening ceremony for a player.
     * Returns the Quirk type they will receive.
     */
    public QuirkType awakenQuirk(final Player player) {
        if (hasAwakened(player.getUniqueId())) {
            return QuirkType.NONE; // Already awakened
        }

        if (quirkManager.isAwakeningInProgress(player.getUniqueId())) {
            return QuirkType.NONE; // Ceremony already running
        }

        quirkManager.setAwakeningInProgress(player.getUniqueId(), true);

        // Pick quirk now, assign only after the ceremony completes
        final QuirkType quirkType = quirkManager.pickRandomQuirkType();
        if (quirkType == QuirkType.NONE) {
            quirkManager.setAwakeningInProgress(player.getUniqueId(), false);
            return QuirkType.NONE;
        }

        startCeremony(player, quirkType);
        return quirkType;
    }

    /**
     * Execute the dramatic awakening ceremony.
     */
    private void startCeremony(final Player player, final QuirkType quirkType) {
        // Phase 1: The Awakening Begins
        player.sendTitle("", "§eSomething is awakening within you...", 10, 60, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.5, 0), 50, 0.5, 1.0, 0.5, 0.1);

        // Create ceremony particles
        final BukkitRunnable ceremony = new BukkitRunnable() {
            int ticks = 0;
            final Location particleCenter = player.getLocation().clone();

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ > CEREMONY_DURATION) {
                    finishCeremony();
                    cancel();
                    return;
                }

                final double progress = (double) ticks / CEREMONY_DURATION;
                final double intensity = PARTICLE_INTENSITY * (0.5 + progress);

                // Phase-based effects
                if (ticks < CEREMONY_DURATION / 2) {
                    preAwakenPhase(player, progress * 2, intensity, ticks);
                } else {
                    awakenPhase(player, quirkType, (progress - 0.5) * 2, intensity);
                }

                // Ambient particles throughout
                ambientParticles(player, intensity);
            }

            private void finishCeremony() {
                quirkManager.setAwakeningInProgress(player.getUniqueId(), false);
                quirkManager.assignQuirk(player, quirkType);
                markAwakened(player.getUniqueId());
                activeCeremonies.remove(player.getUniqueId());

                // Final announcement
                final String quirkName = quirkType.getDisplayName();
                final String rarityColor = getRarityColor(quirkType.getRarity());
                final String rarityDisplay = quirkType.getRarity().getDisplayName();

                player.sendTitle(rarityColor + "QUIRK AWAKENED!", "§f" + quirkName + " §7(" + rarityDisplay + "§7)", 20, 80, 20);

                // Massive final effect
                player.getWorld().spawnParticle(Particle.FLASH, particleCenter.add(0, 1.5, 0), 10, 0, 0, 0, 0);
                player.getWorld().spawnParticle(Particle.EXPLOSION, particleCenter.add(0, 1, 0), 5, 1, 1, 1, 0.1);

                // Spawn quirk-specific particle burst
                spawnQuirkBurst(player, quirkType);

                // Sound celebration
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);

                // Extra sounds for rare quirks
                if (quirkType.getRarity().ordinal() >= QuirkRarity.EPIC.ordinal()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
                }

                // Broadcast message
                player.sendMessage("\n" + rarityColor + "§l★ QUIRK AWAKENED! ★");
                player.sendMessage("§fYour Quirk has manifested: §b" + quirkName + " §7[" + rarityDisplay + "§7]");
                player.sendMessage("§fRarity: " + rarityColor + quirkType.getRarity().getDescription());
                player.sendMessage("§7Use §eLEFT CLICK §7to activate!");
                player.sendMessage("§7Sneak + Right Click for §6Ultimate Move§7!\n");

                // Broadcast to nearby players
                for (final org.bukkit.entity.Entity nearby : player.getNearbyEntities(30, 30, 30)) {
                    if (nearby instanceof Player witness) {
                        witness.sendMessage("§e" + player.getName() + " §fhas awakened their Quirk: " + rarityColor + quirkName + " §7[" + rarityDisplay + "§7]");
                    }
                }
            }
        };

        activeCeremonies.put(player.getUniqueId(), ceremony);
        ceremony.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Phase 1: Pre-awakening buildup.
     */
    private void preAwakenPhase(final Player player, final double progress, final double intensity, final int ticks) {
        final Location center = player.getLocation();

        // Spiral effect around player
        final double angle = progress * Math.PI * 8;
        for (double y = 0; y < 2.5; y += 0.5) {
            final double radius = 1.5 + Math.sin(progress * Math.PI) * 0.5;
            final double x = Math.cos(angle + y) * radius;
            final double z = Math.sin(angle + y) * radius;

            final Location particleLoc = center.clone().add(x, y, z);
            player.getWorld().spawnParticle(Particle.SOUL, particleLoc, (int) intensity, 0, 0, 0, 0.01);
        }

        // Ground circles
        final double groundRadius = 2 + progress;
        for (int i = 0; i < 36; i++) {
            final double phi = Math.toRadians(i * 10);
            final double x = Math.cos(phi) * groundRadius;
            final double z = Math.sin(phi) * groundRadius;

            final Location groundLoc = center.clone().add(x, 0.1, z);
            player.getWorld().spawnParticle(Particle.END_ROD, groundLoc, 1, 0, 0, 0, 0);

            if (progress > 0.5) {
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, groundLoc, 1, 0, 0, 0, 0);
            }
        }

        // Rising power sound
        if (ticks % 5 == 0) {
            player.playSound(center, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.5f, 1.0f + (float) progress);
        }
    }

    /**
     * Phase 2: Quirk manifestation.
     */
    private void awakenPhase(final Player player, final QuirkType type, final double progress, final double intensity) {
        final Location center = player.getLocation();
        final Particle quirkParticle = getQuirkParticle(type);

        // Expanding sphere
        final double sphereRadius = 1 + progress * 3;
        for (double theta = 0; theta < Math.PI; theta += Math.PI / 8) {
            for (double phi = 0; phi < 2 * Math.PI; phi += Math.PI / 8) {
                final double x = sphereRadius * Math.sin(theta) * Math.cos(phi);
                final double y = sphereRadius * Math.cos(theta) + 1;
                final double z = sphereRadius * Math.sin(theta) * Math.sin(phi);

                final Location sphereLoc = center.clone().add(x, y, z);
                if (quirkParticle != null) {
                    player.getWorld().spawnParticle(quirkParticle, sphereLoc, 1, 0, 0, 0, 0);
                }
            }
        }

        // Central beam
        for (double y = 0; y < 15; y += 0.5) {
            final Location beamLoc = center.clone().add(0, y + 1, 0);
            player.getWorld().spawnParticle(Particle.END_ROD, beamLoc, Math.max(1, (int) intensity / 2), 0.1, 0, 0.1, 0);
        }

        // Power wave on ground at end
        if (progress > 0.8) {
            final double waveRadius = (progress - 0.8) * 20;
            for (int i = 0; i < 72; i++) {
                final double phi = Math.toRadians(i * 5);
                final double x = Math.cos(phi) * waveRadius;
                final double z = Math.sin(phi) * waveRadius;

                final Location waveLoc = center.clone().add(x, 0.1, z);
                player.getWorld().spawnParticle(Particle.END_ROD, waveLoc, 1, 0, 0.2, 0, 0.05);
            }
        }

        // Dramatic sound
        final int currentInt = (int) (progress * 10);
        final int prevInt = (int) ((progress - 0.1) * 10);
        if (currentInt != prevInt) {
            player.playSound(center, Sound.BLOCK_BELL_USE, 1.0f, 0.5f + (float) progress);
        }
    }

    /**
     * Ambient particles throughout ceremony.
     */
    private void ambientParticles(final Player player, final double intensity) {
        final Location center = player.getLocation();
        for (int i = 0; i < (int) intensity; i++) {
            final double x = (Math.random() - 0.5) * 4;
            final double y = Math.random() * 3;
            final double z = (Math.random() - 0.5) * 4;

            player.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(x, y, z), 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawn quirk-specific particle burst at the end.
     */
    private void spawnQuirkBurst(final Player player, final QuirkType type) {
        final Particle particle = getQuirkParticle(type);
        if (particle == null) return;

        final Location center = player.getLocation();
        for (int i = 0; i < 200; i++) {
            final double x = (Math.random() - 0.5) * 6;
            final double y = Math.random() * 4;
            final double z = (Math.random() - 0.5) * 6;

            player.getWorld().spawnParticle(particle, center.clone().add(x, y, z), 3, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /**
     * Get particle for Quirk type.
     */
    private Particle getQuirkParticle(final QuirkType type) {
        return switch (type) {
            case EXPLOSION -> Particle.FLAME;
            case ICE_FIRE -> Particle.SNOWFLAKE;
            case ZERO_GRAVITY -> Particle.REVERSE_PORTAL;
            case ONE_FOR_ALL -> Particle.ELECTRIC_SPARK;
            case ENGINE -> Particle.SMOKE;
            case FROG -> Particle.WITCH;
            case HARDENING -> Particle.END_ROD;
            case DECAY -> Particle.SOUL;
            case CREMATION -> Particle.LAVA;
            case ELECTRIFICATION -> Particle.ELECTRIC_SPARK;
            case CREATION -> Particle.END_ROD;
            case PERMEATION -> Particle.PORTAL;
            case WAVE_MOTION -> Particle.SWEEP_ATTACK;
            case NAVEL_LASER -> Particle.END_ROD;
            case POP_OFF -> Particle.SNOWFLAKE;
            case BLACKWHIP -> Particle.FALLING_SPORE_BLOSSOM;
            case TRANSFORMATION -> Particle.WITCH;
            case BLOODCURDLE -> Particle.DRIPPING_LAVA;
            default -> Particle.END_ROD;
        };
    }

    /**
     * Get color based on rarity.
     */
    private String getRarityColor(final QuirkRarity rarity) {
        return switch (rarity) {
            case COMMON -> "§f";     // White
            case UNCOMMON -> "§a";   // Green
            case RARE -> "§b";       // Aqua
            case EPIC -> "§d";       // Light purple
            case LEGENDARY -> "§6";  // Gold
        };
    }

    /**
     * Cancel any active ceremony for a player.
     */
    public void cancelCeremony(final UUID playerId) {
        final BukkitRunnable ceremony = activeCeremonies.remove(playerId);
        if (ceremony != null) {
            ceremony.cancel();
            quirkManager.setAwakeningInProgress(playerId, false);
        }
    }

    /**
     * Cleanup all active ceremonies.
     */
    public void shutdown() {
        for (final BukkitRunnable ceremony : activeCeremonies.values()) {
            ceremony.cancel();
        }
        activeCeremonies.clear();
    }
}
