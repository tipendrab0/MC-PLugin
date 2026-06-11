package com.mha.plugin;

import com.mha.plugin.awakening.QuirkAwakener;
import com.mha.plugin.bounty.BountyManager;
import com.mha.plugin.destruction.DestructionManager;
import com.mha.plugin.event.EventManager;
import com.mha.plugin.listener.*;
import com.mha.plugin.qte.QTEManager;
import com.mha.plugin.quirk.Quirk;
import com.mha.plugin.quirk.QuirkManager;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.reputation.ReputationManager;
import com.mha.plugin.reputation.ReputationRank;
import com.mha.plugin.reputation.ReputationState;
import com.mha.plugin.synergy.SynergyManager;
import com.mha.plugin.util.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MHAPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private QuirkManager quirkManager;
    private QTEManager qteManager;
    private DestructionManager destructionManager;
    private SynergyManager synergyManager;
    private ReputationManager reputationManager;
    private BountyManager bountyManager;
    private EventManager eventManager;
    private QuirkAwakener quirkAwakener;
    private Economy econ = null;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        quirkManager = new QuirkManager(this, configManager);
        qteManager = new QTEManager(this, configManager);
        destructionManager = new DestructionManager(this, configManager);
        synergyManager = new SynergyManager(this, quirkManager, configManager);
        reputationManager = new ReputationManager(this, configManager);
        bountyManager = new BountyManager(reputationManager);
        eventManager = new EventManager();
        quirkAwakener = new QuirkAwakener(this, quirkManager, configManager);
        quirkAwakener.loadAwakenedPlayers();

        if (!setupEconomy()) {
            getLogger().warning("Vault not found! Bounties will drop diamonds instead of money.");
        }

        registerListeners();
        registerCommand("mha", this, this);

        final int saveInterval = configManager.getInt("settings.save-interval", 300) * 20;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            quirkManager.saveQuirkAssignments();
            reputationManager.saveAllReputation();
        }, saveInterval, saveInterval);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (quirkManager != null) {
            quirkManager.saveQuirkAssignments();
            quirkManager.shutdown();
        }
        if (reputationManager != null) reputationManager.saveAllReputation();
        if (qteManager != null) qteManager.shutdown();
        if (destructionManager != null) destructionManager.shutdown();
        if (quirkAwakener != null) quirkAwakener.shutdown();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public ReputationManager getReputationManager() { return reputationManager; }
    public BountyManager getBountyManager() { return bountyManager; }
    public EventManager getEventManager() { return eventManager; }
    public QuirkManager getQuirkManager() { return quirkManager; }
    public QTEManager getQteManager() { return qteManager; }
    public DestructionManager getDestructionManager() { return destructionManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public Economy getEconomy() { return econ; }

    private void registerListeners() {
        registerListener(new PlayerQuitListener(this));
        registerListener(new QuirkActivationListener(quirkManager, configManager));
        registerListener(new QuirkPassiveListener(this, quirkManager));
        registerListener(new PlayerJoinListener(this, quirkManager, configManager, quirkAwakener));
        registerListener(new SupportGearListener(this));
        registerListener(new BountyListener(this));

        // Register quirk-specific listeners that implement Listener interface
        quirkManager.getAllQuirks().forEach(quirk -> {
            if (quirk instanceof Listener listener) {
                registerListener(listener);
            }
        });
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(completer);
        }
    }

    // Command Handlers
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        final String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "info" -> handleInfo(sender);
            case "assign" -> handleAssign(sender, args);
            case "random" -> handleRandom(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "awaken" -> handleAwaken(sender, args);
            case "reload" -> handleReload(sender);
            case "bounty", "bounties" -> handleBounty(sender);
            case "leaderboard", "lb", "top" -> handleLeaderboard(sender, args);
            case "event" -> handleEvent(sender, args);
            case "stats" -> handleStats(sender, args);
            default -> sender.sendMessage("§cUnknown command. Use /mha help");
        }

        return true;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage("§6§l=== My Hero Academia Plugin ===");
        sender.sendMessage("§e/mha info §7- View your quirk info");
        sender.sendMessage("§e/mha bounty §7- View active bounties");
        sender.sendMessage("§e/mha leaderboard [type] §7- View leaderboards");
        sender.sendMessage("§e/mha stats [player] §7- View player statistics");
        if (sender.hasPermission("mha.admin")) {
            sender.sendMessage("§c§lAdmin Commands:");
            sender.sendMessage("§c/mha assign <player> <quirk> §7- Assign a quirk");
            sender.sendMessage("§c/mha random <player> §7- Assign random quirk");
            sender.sendMessage("§c/mha remove <player> §7- Remove player's quirk");
            sender.sendMessage("§c/mha awaken <player> §7- Trigger awakening ceremony");
            sender.sendMessage("§c/mha event <type> <minutes> [multiplier] §7- Start event");
            sender.sendMessage("§c/mha event stop <type> §7- End event");
            sender.sendMessage("§c/mha reload §7- Reload configuration");
        }
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        final QuirkType quirkType = quirkManager.getPlayerQuirkType(player);
        if (quirkType == QuirkType.NONE) {
            sender.sendMessage("§cYou don't have a quirk assigned. Use /mha awaken to get one!");
            return;
        }

        final Quirk quirk = quirkManager.getPlayerQuirk(player);
        final ReputationRank rank = reputationManager.getRank(player);
        final int score = reputationManager.getReputationScore(player);

        player.sendMessage("§6§l=== Your Quirk ===");
        player.sendMessage("§eQuirk: §f" + quirkType.getDisplayName());
        player.sendMessage("§eRarity: " + quirkType.getRarity().getDisplayName());
        player.sendMessage("§eCooldown: §7" + (quirk != null ? quirk.getCooldown() / 1000 + "s" : "N/A"));
        player.sendMessage("§eRank: " + rank.getDisplayName() + " §7(" + score + " rep)");
    }

    private void handleAssign(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /mha assign <player> <quirk>");
            return;
        }

        final Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        final String quirkId = args[2].toLowerCase().replace("_", "-");
        final QuirkType quirkType = QuirkType.fromId(quirkId);

        if (quirkType == QuirkType.NONE) {
            sender.sendMessage("§cUnknown quirk. Available: " +
                    Arrays.stream(QuirkType.values())
                            .filter(t -> t != QuirkType.NONE)
                            .map(t -> t.getId())
                            .collect(Collectors.joining(", ")));
            return;
        }

        quirkManager.assignQuirk(target, quirkType);
        sender.sendMessage("§aAssigned " + quirkType.getDisplayName() + " to " + target.getName());
    }

    private void handleRandom(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mha random <player>");
            return;
        }

        final Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        final QuirkType assigned = quirkManager.assignRandomQuirk(target);
        sender.sendMessage("§aAssigned " + assigned.getDisplayName() + " (" + assigned.getRarity().getDisplayName() + "§a) to " + target.getName());
    }

    private void handleRemove(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mha remove <player>");
            return;
        }

        final Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        quirkManager.removeQuirk(target);
        sender.sendMessage("§aRemoved quirk from " + target.getName());
    }

    private void handleAwaken(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mha awaken <player>");
            return;
        }

        final Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        quirkAwakener.awakenQuirk(target);
        sender.sendMessage("§aStarted awakening ceremony for " + target.getName());
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        configManager.reloadConfig();
        reputationManager.reloadThresholds();
        sender.sendMessage("§aConfiguration reloaded!");
    }

    private void handleBounty(final CommandSender sender) {
        final List<BountyManager.BountyInfo> bounties = bountyManager.getActiveBounties();

        if (bounties.isEmpty()) {
            sender.sendMessage("§eNo active bounties. Villains will appear as players gain villain reputation!");
            return;
        }

        sender.sendMessage("§6§l=== Active Bounties ===");
        int count = 0;
        for (final BountyManager.BountyInfo bounty : bounties) {
            if (count++ >= 10) break; // Show top 10

            final String bountyStr = econ != null
                    ? econ.format(bounty.bounty())
                    : String.format("%.0f diamonds", bounty.bounty() / 50);

            sender.sendMessage("§c" + bounty.name() + " §7- §e" + bountyStr + " §8(Score: " + bounty.villainScore() + ")");
        }

        if (bounties.size() > 10) {
            sender.sendMessage("§7... and " + (bounties.size() - 10) + " more");
        }
    }

    private void handleLeaderboard(final CommandSender sender, final String[] args) {
        final String type = args.length > 1 ? args[1].toLowerCase() : "reputation";

        sender.sendMessage("§6§l=== " + switch (type) {
            case "bounty", "bounties" -> "Top Bounty Hunters";
            case "kills" -> "Top Killers";
            default -> "Top Heroes";
        } + " ===");

        switch (type) {
            case "bounty", "bounties" -> {
                final List<Map.Entry<UUID, Double>> top = bountyManager.getTopHunters(10);
                int rank = 1;
                for (final Map.Entry<UUID, Double> entry : top) {
                    final OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                    final String amount = econ != null ? econ.format(entry.getValue()) : String.format("%.0f", entry.getValue());
                    sender.sendMessage("§e#" + rank++ + " §f" + player.getName() + " §7- §e" + amount);
                }
            }
            case "kills" -> {
                // Would need kill tracking - placeholder
                sender.sendMessage("§7Kill tracking coming soon!");
            }
            default -> {
                // Reputation leaderboard
                final List<Map.Entry<UUID, ReputationState>> topRep = reputationManager.getAllReputations().entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue().getTotalScore(), a.getValue().getTotalScore()))
                        .limit(10)
                        .collect(Collectors.toList());

                int rank = 1;
                for (final Map.Entry<UUID, ReputationState> entry : topRep) {
                    final OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                    final ReputationRank playerRank = reputationManager.getRankByScore(entry.getValue().getTotalScore());

                    sender.sendMessage("§e#" + rank++ + " §f" + player.getName() + " §7- " +
                            playerRank.getDisplayName() + " §8(" + entry.getValue().getTotalScore() + ")");
                }
            }
        }
    }

    private void handleEvent(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /mha event <type> <minutes> [multiplier]");
            sender.sendMessage("§cTypes: double-rep, double-bounty, bonus-drops, power-surge");
            sender.sendMessage("§cUse /mha event stop <type> to end early");
            return;
        }

        final String action = args[1].toLowerCase();

        if (action.equals("stop") && args.length >= 3) {
            final String eventType = args[2].toLowerCase().replace("-", "_");
            try {
                final EventManager.EventType type = EventManager.EventType.valueOf(eventType.toUpperCase());
                eventManager.endEvent(type);
                sender.sendMessage("§aEnded " + type.getDisplayName() + " event.");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cUnknown event type.");
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /mha event <type> <minutes> [multiplier]");
            return;
        }

        try {
            final String eventTypeStr = action.replace("-", "_").toUpperCase();
            final EventManager.EventType type = EventManager.EventType.valueOf(eventTypeStr);
            final int minutes = Integer.parseInt(args[2]);
            final double multiplier = args.length > 3 ? Double.parseDouble(args[3]) : 2.0;

            eventManager.startEvent(type, minutes, multiplier);
            sender.sendMessage("§aStarted " + type.getDisplayName() + " for " + minutes + " minutes!");
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cUnknown event type. Use: double-rep, double-bounty, bonus-drops, power-surge");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number format.");
        }
    }

    private void handleStats(final CommandSender sender, final String[] args) {
        final Player target;
        if (args.length > 1) {
            if (!sender.hasPermission("mha.admin") && !sender.hasPermission("mha.stats.others")) {
                sender.sendMessage("§cYou don't have permission to view others' stats.");
                return;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cSpecify a player: /mha stats <player>");
            return;
        }

        final QuirkType quirk = quirkManager.getPlayerQuirkType(target);
        final ReputationRank rank = reputationManager.getRank(target);
        final int repScore = reputationManager.getReputationScore(target);
        final double bountyEarnings = bountyManager.getTotalEarnings(target.getUniqueId());
        final int bountyKills = bountyManager.getTotalClaims(target.getUniqueId());

        sender.sendMessage("§6§l=== Stats: " + target.getName() + " ===");
        sender.sendMessage("§eQuirk: §f" + (quirk != QuirkType.NONE ? quirk.getDisplayName() : "None"));
        sender.sendMessage("§eRarity: " + (quirk != QuirkType.NONE ? quirk.getRarity().getDisplayName() : "N/A"));
        sender.sendMessage("§eRank: " + rank.getDisplayName() + " §7(" + repScore + ")");
        sender.sendMessage("§eBounties Claimed: §f" + bountyKills);
        sender.sendMessage("§eBounty Earnings: §f" + (econ != null ? econ.format(bountyEarnings) : String.format("%.0f diamonds", bountyEarnings / 50)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("help", "info", "bounty", "leaderboard", "stats"));
            if (sender.hasPermission("mha.admin")) {
                completions.addAll(List.of("assign", "random", "remove", "awaken", "reload", "event"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "assign", "remove", "awaken", "stats" -> {
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                }
                case "leaderboard" -> {
                    completions.addAll(List.of("reputation", "bounty", "kills"));
                }
                case "event" -> {
                    if (sender.hasPermission("mha.admin")) {
                        completions.addAll(List.of("double-reputation", "double-bounty", "bonus-drops", "power-surge", "stop"));
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("assign")) {
            Arrays.stream(QuirkType.values())
                    .filter(t -> t != QuirkType.NONE)
                    .map(QuirkType::getId)
                    .forEach(completions::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("event") && sender.hasPermission("mha.admin")) {
            if (args[1].equalsIgnoreCase("stop")) {
                Arrays.stream(EventManager.EventType.values())
                        .map(EventManager.EventType::name)
                        .map(n -> n.toLowerCase().replace("_", "-"))
                        .forEach(completions::add);
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}