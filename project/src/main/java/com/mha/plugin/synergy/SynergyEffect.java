package com.mha.plugin.synergy;

import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Defines possible synergy combos when two Quirks combine.
 */
public enum SynergyEffect {
    THERMAL_SHOCK(
            "Thermal Shock",
            "Explosion and Ice combine into a devastating blast!",
            Particle.SNOWFLAKE,
            Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,
            18.0, 7.0, 8000,
            new String[]{"explosion", "ice-fire"}
    ),
    ZERO_POINT_BLAST(
            "Gravity Well",
            "Zero Gravity and Explosion create a crushing void!",
            Particle.REVERSE_PORTAL,
            Sound.ENTITY_WITHER_SHOOT,
            25.0, 8.0, 10000,
            new String[]{"explosion", "zero-gravity"}
    ),
    CRYO_FIRESTORM(
            "Cryo Firestorm",
            "Ice-Fire mastery creates a blazing blizzard!",
            Particle.SOUL_FIRE_FLAME,
            Sound.ENTITY_BLAZE_AMBIENT,
            20.0, 10.0, 6000,
            new String[]{"ice-fire", "ice-fire"}
    ),
    GRAVITY_ICE(
            "Gravity Ice",
            "Zero Gravity freezes enemies mid-air!",
            Particle.END_ROD,
            Sound.BLOCK_GLASS_BREAK,
            15.0, 6.0, 12000,
            new String[]{"zero-gravity", "ice-fire"}
    ),
    FIRESTORM(
            "Firestorm",
            "Fire and wind erupt into a raging inferno!",
            Particle.FLAME,
            Sound.ENTITY_BLAZE_SHOOT,
            15.0, 8.0, 5000,
            new String[]{"cremation", "wave-motion"}
    ),
    ICE_STORM(
            "Ice Storm",
            "Ice and wind create a freezing cyclone!",
            Particle.SNOWFLAKE,
            Sound.BLOCK_GLASS_BREAK,
            12.0, 7.0, 8000,
            new String[]{"ice-fire", "wave-motion"}
    ),
    THUNDERCLAP(
            "Thunderclap",
            "Explosion and lightning detonate in thunderous fury!",
            Particle.ELECTRIC_SPARK,
            Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
            20.0, 12.0, 4000,
            new String[]{"explosion", "electrification"}
    ),
    STEAM_ERUPTION(
            "Steam Eruption",
            "Fire and ice collide into scalding steam!",
            Particle.CLOUD,
            Sound.BLOCK_FIRE_EXTINGUISH,
            18.0, 6.0, 5000,
            new String[]{"ice-fire", "cremation"}
    );

    private final String name;
    private final String description;
    private final Particle particle;
    private final Sound sound;
    private final double damage;
    private final double radius;
    private final long duration;
    private final String[] requiredQuirks;

    SynergyEffect(final String name, final String description, final Particle particle,
                  final Sound sound, final double damage, final double radius,
                  final long duration, final String[] requiredQuirks) {
        this.name = name;
        this.description = description;
        this.particle = particle;
        this.sound = sound;
        this.damage = damage;
        this.radius = radius;
        this.duration = duration;
        this.requiredQuirks = requiredQuirks;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Particle getParticle() {
        return particle;
    }

    public Sound getSound() {
        return sound;
    }

    public double getDamage() {
        return damage;
    }

    public double getRadius() {
        return radius;
    }

    public long getDuration() {
        return duration;
    }

    public String[] getRequiredQuirks() {
        return requiredQuirks;
    }
}
