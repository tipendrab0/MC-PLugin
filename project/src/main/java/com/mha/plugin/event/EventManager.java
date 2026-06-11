package com.mha.plugin.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages server-wide events and bonuses.
 * Admins can trigger events like double reputation, bonus bounties, etc.
 */
public final class EventManager {

    private final Map<EventType, ActiveEvent> activeEvents;
    private final Set<UUID> eventParticipants;

    public EventManager() {
        this.activeEvents = new ConcurrentHashMap<>();
        this.eventParticipants = ConcurrentHashMap.newKeySet();
    }

    /**
     * Start an event with specified duration.
     */
    public void startEvent(final EventType type, final int durationMinutes, final double multiplier) {
        final long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
        activeEvents.put(type, new ActiveEvent(type, multiplier, endTime));

        // Announce to all players
        final String eventName = type.getDisplayName();
        final String announcement = switch (type) {
            case DOUBLE_REPUTATION -> "§6§l★ EVENT: " + eventName + " §7- Earn reputation 2x faster!";
            case DOUBLE_BOUNTY -> "§6§l★ EVENT: " + eventName + " §7- All bounties worth 2x!";
            case BONUS_DROPS -> "§6§l★ EVENT: " + eventName + " §7- Extra loot from all sources!";
            case POWER_SURGE -> "§6§l★ EVENT: " + eventName + " §7- All quirks deal 50% more damage!";
        };

        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§6★ EVENT STARTED", eventName, 10, 60, 10);
            player.sendMessage(announcement);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 1.0f);
        }
    }

    /**
     * End an event early.
     */
    public void endEvent(final EventType type) {
        final ActiveEvent event = activeEvents.remove(type);
        if (event != null) {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§7Event §e" + type.getDisplayName() + " §7has ended.");
            }
        }
    }

    /**
     * End all active events.
     */
    public void endAllEvents() {
        activeEvents.clear();
    }

    /**
     * Check if an event is currently active.
     */
    public boolean isEventActive(final EventType type) {
        final ActiveEvent event = activeEvents.get(type);
        if (event == null) return false;

        // Check if expired
        if (System.currentTimeMillis() > event.endTime()) {
            activeEvents.remove(type);
            return false;
        }
        return true;
    }

    /**
     * Get the multiplier for an active event (1.0 if not active).
     */
    public double getMultiplier(final EventType type) {
        final ActiveEvent event = activeEvents.get(type);
        if (event == null || System.currentTimeMillis() > event.endTime()) {
            return 1.0;
        }
        return event.multiplier();
    }

    /**
     * Get all active events.
     */
    public Map<EventType, ActiveEvent> getActiveEvents() {
        // Clean expired events
        activeEvents.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().endTime());
        return activeEvents;
    }

    /**
     * Get remaining time for an event in seconds.
     */
    public long getRemainingTime(final EventType type) {
        final ActiveEvent event = activeEvents.get(type);
        if (event == null) return 0;
        return Math.max(0, (event.endTime() - System.currentTimeMillis()) / 1000);
    }

    /**
     * Types of events.
     */
    public enum EventType {
        DOUBLE_REPUTATION("Double Reputation", "2x reputation gains"),
        DOUBLE_BOUNTY("Double Bounty", "2x bounty rewards"),
        BONUS_DROPS("Bonus Drops", "Extra item drops"),
        POWER_SURGE("Power Surge", "50% more quirk damage");

        private final String displayName;
        private final String description;

        EventType(final String displayName, final String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Active event data.
     */
    public record ActiveEvent(EventType type, double multiplier, long endTime) {}
}
