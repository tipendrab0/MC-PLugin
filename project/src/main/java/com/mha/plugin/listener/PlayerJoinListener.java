package com.mha.plugin.listener;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.util.TextUtil;
import com.mha.plugin.awakening.QuirkAwakener;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
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
    private final ConfigManager config;
    private final boolean autoAwaken;

    /**
     * Create the join listener.
     */
    public PlayerJoinListener(final JavaPlugin plugin, final QuirkManager quirkManager,
                               final ConfigManager config, final QuirkAwakener quirkAwakener) {
        this.plugin = plugin;
        this.quirkManager = quirkManager;
        this.config = config;
        this.quirkAwakener = quirkAwakener;
        this.autoAwaken = config.getBoolean("settings.auto-awaken-on-first-join", true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

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
