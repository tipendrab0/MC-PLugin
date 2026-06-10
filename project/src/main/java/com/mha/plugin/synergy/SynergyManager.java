package com.mha.plugin.synergy;

import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Quirk Synergy combos when two players attack the same target.
 * Tracks recent damage events and triggers special effects on combos.
 */
public final class SynergyManager implements Listener {

    private final JavaPlugin plugin;
    private final QuirkManager quirkManager;
    private final ConfigManager config;
    private final Map<UUID, List<DamageRecord>> damageHistory;

    public SynergyManager(final JavaPlugin plugin, final QuirkManager quirkManager, final ConfigManager config) {
        this.plugin = plugin;
        this.quirkManager = quirkManager;
        this.config = config;
        this.damageHistory = new ConcurrentHashMap<>();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Cleanup task for old damage records
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOldRecords, 100L, 100L);
    }

    /**
     * Record a damage event for potential synergy combos.
     */
    private long getSynergyWindowMs() {
        return config.getInt("synergy.combo-window-ms", 2000);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageByEntityEvent event) {
        if (!config.getBoolean("synergy.enabled", true)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        final Quirk attackerQuirk = quirkManager.getPlayerQuirk(attacker);
        if (attackerQuirk == null) {
            return;
        }

        recordDamage(target.getUniqueId(), attacker.getUniqueId(), attackerQuirk.getType());

        checkAndTriggerSynergy(target, attacker, attackerQuirk.getType());
    }

    /**
     * Record damage for synergy tracking.
     */
    private void recordDamage(final UUID targetId, final UUID attackerId, final QuirkType quirkType) {
        damageHistory.computeIfAbsent(targetId, k -> new ArrayList<>())
                .add(new DamageRecord(attackerId, quirkType, System.currentTimeMillis()));
    }

    /**
     * Check if a synergy combo should trigger.
     */
    private void checkAndTriggerSynergy(final LivingEntity target, final Player currentAttacker, final QuirkType currentQuirk) {
        final List<DamageRecord> records = damageHistory.get(target.getUniqueId());
        if (records == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        final long window = getSynergyWindowMs();
        final List<DamageRecord> recentRecords = records.stream()
                .filter(r -> now - r.timestamp() < window)
                .filter(r -> !r.attackerId().equals(currentAttacker.getUniqueId()))
                .toList();

        for (final DamageRecord record : recentRecords) {
            final SynergyEffect synergy = findSynergyEffect(currentQuirk, record.quirkType());
            if (synergy != null) {
                triggerSynergyEffect(synergy, target.getLocation(), currentAttacker, record.attackerId());
                // Remove used records to prevent double-trigger
                damageHistory.get(target.getUniqueId()).removeIf(r -> now - r.timestamp() < window);
                break;
            }
        }
    }

    /**
     * Find a synergy effect for two Quirk types.
     */
    private SynergyEffect findSynergyEffect(final QuirkType type1, final QuirkType type2) {
        final String id1 = type1.getId();
        final String id2 = type2.getId();

        for (final SynergyEffect effect : SynergyEffect.values()) {
            final String[] required = effect.getRequiredQuirks();
            if (required.length == 2) {
                if ((required[0].equals(id1) && required[1].equals(id2)) ||
                    (required[0].equals(id2) && required[1].equals(id1))) {
                    return effect;
                }
            } else if (required.length == 1 && required[0].equals(id1) && id1.equals(id2)) {
                // Special case for same-quirk synergies
                return effect;
            }
        }

        return null;
    }

    /**
     * Execute a synergy effect at the target location.
     */
    private void triggerSynergyEffect(final SynergyEffect effect, final Location location,
                                       final Player triggerPlayer, final UUID partnerId) {
        final Player partner = Bukkit.getPlayer(partnerId);

        // Announce combo
        triggerPlayer.sendMessage("§d§l✦ SYNERGY COMBO: §e" + effect.getName() + " §d✦");
        triggerPlayer.sendMessage("§7" + effect.getDescription());

        if (partner != null && partner.isOnline()) {
            partner.sendMessage("§d§l✦ SYNERGY COMBO: §e" + effect.getName() + " §d✦");
            partner.sendMessage("§7" + effect.getDescription());
        }

        // Play dramatic sound
        location.getWorld().playSound(location, effect.getSound(), 2.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 0.8f);

        // Spawn particles
        location.getWorld().spawnParticle(effect.getParticle(), location, 200, 3.0, 3.0, 3.0, 0.3);
        location.getWorld().spawnParticle(Particle.FLASH, location, 5, 0, 0, 0, 0);

        // Deal synergy damage to all entities in radius
        for (final Entity entity : location.getWorld().getNearbyEntities(location, effect.getRadius(), effect.getRadius(), effect.getRadius())) {
            if (entity instanceof LivingEntity living && !entity.equals(triggerPlayer)) {
                living.damage(effect.getDamage(), triggerPlayer);
            }
        }

        // Apply additional effects based on synergy type
        applySpecialEffects(effect, location, triggerPlayer);
    }

    /**
     * Apply special effects unique to each synergy type.
     */
    private void applySpecialEffects(final SynergyEffect effect, final Location location, final Player triggerPlayer) {
        switch (effect) {
            case THERMAL_SHOCK:
                // Ice + explosion = frozen blast
                location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 150, 5.0, 5.0, 5.0, 0.3);
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 3, 2.0, 2.0, 2.0, 0);
                break;
            case ZERO_POINT_BLAST:
                // Zero gravity + explosion = floating debris
                location.getWorld().spawnParticle(Particle.REVERSE_PORTAL, location, 100, 4.0, 4.0, 4.0, 0.2);
                for (Entity entity : location.getWorld().getNearbyEntities(location, effect.getRadius(), effect.getRadius(), effect.getRadius())) {
                    if (entity instanceof LivingEntity living && !entity.equals(triggerPlayer)) {
                        living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.LEVITATION, 100, 2));
                    }
                }
                break;
            case CRYO_FIRESTORM:
                // Ice-fire mastery = blazing blizzard
                location.getWorld().spawnParticle(Particle.FLAME, location, 100, 4.0, 4.0, 4.0, 0.3);
                location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 100, 4.0, 4.0, 4.0, 0.3);
                break;
            case GRAVITY_ICE:
                location.getWorld().spawnParticle(Particle.END_ROD, location, 100, 5.0, 5.0, 5.0, 0.2);
                applySlownessInRadius(location, effect.getRadius(), triggerPlayer, 300, 4);
                break;
            case FIRESTORM:
                location.getWorld().spawnParticle(Particle.FLAME, location, 150, 6.0, 6.0, 6.0, 0.3);
                igniteInRadius(location, effect.getRadius(), triggerPlayer);
                break;
            case ICE_STORM:
                location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 150, 6.0, 6.0, 6.0, 0.3);
                applySlownessInRadius(location, effect.getRadius(), triggerPlayer, 200, 5);
                break;
            case THUNDERCLAP:
                location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 200, 8.0, 8.0, 8.0, 0.4);
                location.getWorld().strikeLightningEffect(location);
                break;
            case STEAM_ERUPTION:
                location.getWorld().spawnParticle(Particle.CLOUD, location, 120, 5.0, 5.0, 5.0, 0.2);
                location.getWorld().spawnParticle(Particle.SMOKE, location, 80, 4.0, 4.0, 4.0, 0.1);
                knockbackInRadius(location, effect.getRadius(), triggerPlayer, 1.5);
                break;
            default:
                break;
        }
    }

    /**
     * Cleanup old damage records to prevent memory leaks.
     */
    private void applySlownessInRadius(final Location location, final double radius, final Player triggerPlayer,
                                       final int durationTicks, final int amplifier) {
        for (final Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(triggerPlayer)) {
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, durationTicks, amplifier));
            }
        }
    }

    private void igniteInRadius(final Location location, final double radius, final Player triggerPlayer) {
        for (final Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(triggerPlayer)) {
                living.setFireTicks(100);
            }
        }
    }

    private void knockbackInRadius(final Location location, final double radius, final Player triggerPlayer, final double strength) {
        for (final Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(triggerPlayer)) {
                final var knockback = living.getLocation().toVector()
                        .subtract(location.toVector())
                        .normalize()
                        .multiply(strength)
                        .setY(0.4);
                living.setVelocity(knockback);
            }
        }
    }

    private void cleanupOldRecords() {
        final long now = System.currentTimeMillis();
        final long window = getSynergyWindowMs();
        damageHistory.forEach((uuid, records) -> {
            records.removeIf(r -> now - r.timestamp() > window * 2);
            if (records.isEmpty()) {
                damageHistory.remove(uuid);
            }
        });
    }

    /**
     * Remove records for a disconnected player.
     */
    public void onPlayerQuit(final UUID playerId) {
        damageHistory.forEach((targetId, records) ->
                records.removeIf(r -> r.attackerId().equals(playerId)));
    }

    /**
     * Record of damage dealt for synergy tracking.
     */
    private record DamageRecord(UUID attackerId, QuirkType quirkType, long timestamp) {}
}
