package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.gui.AlignmentGui;
import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.util.GuideBook;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.awakening.QuirkAwakener;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener for player join events.
 * Handles Quirk awakening ceremony for first-time players.
 */
public final class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final QuirkManager quirkManager;
    private final QuirkAwakener quirkAwakener;
    private final StaminaManager staminaManager;
    private final ReputationManager reputationManager;
    private final ConfigManager config;
    private final boolean autoAwaken;
    private final boolean giveGuide;
    private final boolean alignmentEnabled;

    /**
     * Create the join listener.
     */
    public PlayerJoinListener(final JavaPlugin plugin, final QuirkManager quirkManager,
                               final StaminaManager staminaManager, final ConfigManager config,
                               final QuirkAwakener quirkAwakener, final ReputationManager reputationManager) {
        this.plugin = plugin;
        this.quirkManager = quirkManager;
        this.staminaManager = staminaManager;
        this.config = config;
        this.quirkAwakener = quirkAwakener;
        this.reputationManager = reputationManager;
        this.autoAwaken = config.getBoolean("settings.auto-awaken-on-first-join", true);
        this.giveGuide = config.getBoolean("guide.give-on-join", true);
        this.alignmentEnabled = config.getBoolean("alignment.enabled", true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // Initialize stamina
        staminaManager.getOrCreateStamina(player);

        // Hand out the guide book once per player.
        if (giveGuide && !config.getBoolean("guide-given." + player.getUniqueId(), false)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    GuideBook.give(player);
                    config.set("guide-given." + player.getUniqueId(), true);
                    config.saveConfig();
                }
            }, 20L);
        }

        // Let new players pick Hero or Villain.
        if (alignmentEnabled && !reputationManager.hasChosenAlignment(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !reputationManager.hasChosenAlignment(player)) {
                    AlignmentGui.open(player);
                }
            }, 40L);
        }

        // Check if player needs Quirk awakening
        if (autoAwaken && !quirkManager.hasQuirk(player) && !quirkAwakener.hasAwakened(player.getUniqueId())) {
            // Delay awakening to let the player see the spawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // Check again in case something changed
                if (!quirkManager.hasQuirk(player) && !quirkAwakener.hasAwakened(player.getUniqueId())) {
                    // Send awakening message
                    player.sendMessage("\n§6§l★ Welcome to Hero Society! ★");
                    player.sendMessage("§7Your Quirk is about to awaken...");
                    player.sendMessage("§7Stand still to witness your power!\n");

                    // Small delay for dramatic effect
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        final QuirkType awakened = quirkAwakener.awakenQuirk(player);
                        if (awakened == QuirkType.NONE && !quirkManager.hasQuirk(player)) {
                            player.sendMessage("§cYour Quirk awakening could not start. Ask an admin to use §e/mha awaken§c.");
                        }
                    }, 60L); // 3 second delay
                }
            }, 100L); // 5 second initial delay after join
        } else if (quirkManager.hasQuirk(player)) {
            // Show their current quirk
            final QuirkType quirk = quirkManager.getPlayerQuirkType(player);
            TextUtil.actionBar(player, "§aQuirk Active: §b" + quirk.getDisplayName());
        }
    }
}
