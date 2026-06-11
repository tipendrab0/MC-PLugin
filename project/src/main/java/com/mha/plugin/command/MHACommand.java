package com.mha.plugin.command;

import com.mha.plugin.MHAPlugin;
import com.mha.plugin.gui.AlignmentGui;
import com.mha.plugin.quirk.QuirkType;
import com.mha.plugin.reputation.Alignment;
import com.mha.plugin.reputation.ReputationRank;
import com.mha.plugin.stamina.StaminaManager;
import com.mha.plugin.util.GuideBook;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the {@code /mha} command and its sub-commands.
 */
public final class MHACommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS = Arrays.asList(
            "help", "quirk", "quirks", "stamina", "reputation", "align", "guide", "version");
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "reload", "awaken", "setquirk", "removequirk", "setalign", "resetstamina");

    private final MHAPlugin plugin;

    public MHACommand(final MHAPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        final String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "version" -> sender.sendMessage("§6[MHA] §fVersion §e" + plugin.getDescription().getVersion());
            case "quirk", "info" -> handleQuirkInfo(sender);
            case "quirks", "list" -> handleQuirkList(sender);
            case "stamina" -> handleStamina(sender);
            case "reputation", "rep" -> handleReputation(sender);
            case "align", "alignment" -> handleAlign(sender);
            case "guide", "handbook" -> handleGuide(sender);
            case "reload" -> handleReload(sender);
            case "awaken" -> handleAwaken(sender, args);
            case "setquirk" -> handleSetQuirk(sender, args);
            case "removequirk" -> handleRemoveQuirk(sender, args);
            case "setalign" -> handleSetAlign(sender, args);
            case "resetstamina" -> handleResetStamina(sender, args);
            default -> sender.sendMessage("§cUnknown sub-command. Use §e/mha help§c.");
        }
        return true;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage("§6§l===== MHA Plugin Help =====");
        sender.sendMessage("§e/mha quirk §7- Show your current Quirk");
        sender.sendMessage("§e/mha quirks §7- List every Quirk");
        sender.sendMessage("§e/mha stamina §7- Check your stamina");
        sender.sendMessage("§e/mha reputation §7- Check rank & alignment");
        sender.sendMessage("§e/mha align §7- Choose Hero or Villain");
        sender.sendMessage("§e/mha guide §7- Get the handbook again");
        if (sender.hasPermission("mha.admin")) {
            sender.sendMessage("§c§lAdmin:");
            sender.sendMessage("§c/mha reload §7- Reload config");
            sender.sendMessage("§c/mha awaken [player] §7- Trigger awakening");
            sender.sendMessage("§c/mha setquirk <player> <quirk> §7- Assign a Quirk");
            sender.sendMessage("§c/mha removequirk <player> §7- Remove a Quirk");
            sender.sendMessage("§c/mha setalign <player> <hero|villain> §7- Set alignment");
            sender.sendMessage("§c/mha resetstamina [player] §7- Reset stamina");
        }
    }

    private void handleQuirkInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players have a Quirk.");
            return;
        }
        final QuirkType type = plugin.getQuirkManager().getPlayerQuirkType(player);
        if (type == QuirkType.NONE) {
            player.sendMessage("§7You have no Quirk yet. Ask an admin to §e/mha awaken§7 you.");
            return;
        }
        player.sendMessage("§6§l===== Your Quirk =====");
        player.sendMessage("§fName: §b" + type.getDisplayName());
        player.sendMessage("§fRarity: " + type.getRarity().getDisplayName());
        player.sendMessage("§fDescription: §7" + type.getDescription());
        player.sendMessage("§7Left-click to activate, Sneak + Right-click for Ultimate.");
    }

    private void handleQuirkList(final CommandSender sender) {
        sender.sendMessage("§6§l===== Quirks =====");
        final QuirkType current = sender instanceof Player p
                ? plugin.getQuirkManager().getPlayerQuirkType(p) : QuirkType.NONE;
        for (final QuirkType type : QuirkType.values()) {
            if (!type.isPlayable()) {
                continue;
            }
            final String marker = type == current ? " §a§l(YOURS)" : "";
            sender.sendMessage(type.getRarity().getDisplayName() + " §7- §f" + type.getDisplayName() + marker);
        }
    }

    private void handleStamina(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players have stamina.");
            return;
        }
        final StaminaManager stamina = plugin.getStaminaManager();
        sender.sendMessage("§6§l===== Stamina =====");
        sender.sendMessage("§fEnergy: §a" + stamina.getStamina(player) + "§7/§a" + stamina.getMaxStamina()
                + " §7(" + stamina.getStaminaPercent(player) + "%)");
        sender.sendMessage("§fState: §e" + stamina.getState(player).name()
                + (stamina.isExhausted(player) ? " §c(Exhausted!)" : ""));
    }

    private void handleReputation(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players have a reputation.");
            return;
        }
        final ReputationRank rank = plugin.getReputationManager().getRank(player);
        final Alignment alignment = plugin.getReputationManager().getAlignment(player);
        sender.sendMessage("§6§l===== Reputation =====");
        sender.sendMessage("§fAlignment: " + alignment.getColor() + alignment.getDisplayName());
        sender.sendMessage("§fRank: " + rank.getDisplayColor() + rank.getDisplayName());
        sender.sendMessage("§fScore: §e" + plugin.getReputationManager().getReputationScore(player));
    }

    private void handleAlign(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can choose an alignment.");
            return;
        }
        AlignmentGui.open(player);
    }

    private void handleGuide(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can hold the handbook.");
            return;
        }
        GuideBook.give(player);
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission("mha.admin.reload")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }
        plugin.getConfigManager().reloadConfig();
        plugin.getReputationManager().reloadThresholds();
        sender.sendMessage("§aMHA configuration reloaded.");
    }

    private void handleAwaken(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin.awaken")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }
        final Player target = resolveTarget(sender, args, 1);
        if (target == null) {
            return;
        }
        final QuirkType awakened = plugin.getQuirkAwakener().awakenQuirk(target);
        if (awakened == QuirkType.NONE) {
            sender.sendMessage("§cCould not awaken " + target.getName() + " (already awakened or in progress).");
        } else {
            sender.sendMessage("§aStarted awakening ceremony for §e" + target.getName() + "§a.");
        }
    }

    private void handleSetQuirk(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin.setquirk")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /mha setquirk <player> <quirk>");
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return;
        }
        final QuirkType type = QuirkType.fromId(args[2]);
        if (type == QuirkType.NONE) {
            sender.sendMessage("§cUnknown Quirk: " + args[2]);
            return;
        }
        plugin.getQuirkManager().assignQuirk(target, type);
        plugin.getQuirkAwakener().markAwakened(target.getUniqueId());
        sender.sendMessage("§aGave §e" + target.getName() + "§a the §b" + type.getDisplayName() + "§a Quirk.");
    }

    private void handleRemoveQuirk(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin.setquirk")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }
        final Player target = resolveTarget(sender, args, 1);
        if (target == null) {
            return;
        }
        plugin.getQuirkManager().removeQuirk(target);
        sender.sendMessage("§aRemoved the Quirk from §e" + target.getName() + "§a.");
    }

    private void handleSetAlign(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /mha setalign <player> <hero|villain|undecided>");
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return;
        }
        final Alignment alignment = Alignment.fromId(args[2]);
        plugin.getReputationManager().setAlignment(target, alignment);
        sender.sendMessage("§aSet §e" + target.getName() + "§a's alignment to "
                + alignment.getColor() + alignment.getDisplayName() + "§a.");
    }

    private void handleResetStamina(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("mha.admin.resetstamina")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }
        final Player target = resolveTarget(sender, args, 1);
        if (target == null) {
            return;
        }
        plugin.getStaminaManager().resetStamina(target);
        sender.sendMessage("§aReset stamina for §e" + target.getName() + "§a.");
    }

    /**
     * Resolve the target player from an optional name argument, defaulting to the
     * sender when they are a player and no name is supplied.
     */
    private Player resolveTarget(final CommandSender sender, final String[] args, final int index) {
        if (args.length > index) {
            final Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: " + args[index]);
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("§cConsole must specify a player name.");
        return null;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            final List<String> options = new ArrayList<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission("mha.admin")) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(options, args[0]);
        }

        final String sub = args[0].toLowerCase();
        if (args.length == 2 && Arrays.asList("awaken", "setquirk", "removequirk", "setalign", "resetstamina").contains(sub)) {
            return filter(onlinePlayerNames(), args[1]);
        }
        if (args.length == 3 && sub.equals("setquirk")) {
            return filter(quirkIds(), args[2]);
        }
        if (args.length == 3 && sub.equals("setalign")) {
            return filter(Arrays.asList("hero", "villain", "undecided"), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> quirkIds() {
        return Arrays.stream(QuirkType.values())
                .filter(QuirkType::isPlayable)
                .map(QuirkType::getId)
                .collect(Collectors.toList());
    }

    private List<String> filter(final List<String> options, final String prefix) {
        final String lower = prefix.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
