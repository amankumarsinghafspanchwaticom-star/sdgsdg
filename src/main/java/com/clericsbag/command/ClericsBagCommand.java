package com.clericsbag.command;

import com.clericsbag.ClericsBagPlugin;
import com.clericsbag.gui.BagGuiManager;
import com.clericsbag.item.BagRecipeManager;
import com.clericsbag.item.ClericsBagStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClericsBagCommand implements CommandExecutor, TabCompleter {

    private final ClericsBagPlugin plugin;
    private final ClericsBagStorage storage;
    private final BagRecipeManager recipeManager;
    private final BagGuiManager guiManager;

    public ClericsBagCommand(
            ClericsBagPlugin plugin,
            ClericsBagStorage storage,
            BagRecipeManager recipeManager,
            BagGuiManager guiManager
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.recipeManager = recipeManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> handleGive(sender, args);
            case "open" -> handleOpen(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("clericsbag.admin.give")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.parse("<red>Player not found: " + args[1] + "</red>"));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(plugin.parse("<red>Specify a player from console: /clericsbag give <player> [color]</red>"));
            return;
        }

        Material bundleMat = Material.BUNDLE;
        if (args.length >= 3) {
            Material parsed = parseBundleColor(args[2]);
            if (parsed == null) {
                sender.sendMessage(plugin.parse("<red>Unknown bundle color: " + args[2] + ". Try default, red, blue, purple, black, etc.</red>"));
                return;
            }
            bundleMat = parsed;
        }

        ItemStack bag = storage.createClericsBag(bundleMat);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(bag);
        overflow.values().forEach(drop -> target.getWorld().dropItemNaturally(target.getLocation(), drop));

        sender.sendMessage(plugin.parse("<green>Gave <gold><bold>Cleric's Bag</bold></gold> (<white>"
                + bundleMat.name().toLowerCase(Locale.ROOT) + "</white>) to <white>" + target.getName() + "</white>.</green>"));
    }

    private void handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.parse("<red>Players only.</red>"));
            return;
        }
        if (!player.hasPermission("clericsbag.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!storage.isClericsBag(held)) {
            held = player.getInventory().getItemInOffHand();
        }
        if (!storage.isClericsBag(held)) {
            player.sendMessage(plugin.parse("<red>Hold a Cleric's Bag in your hand to open its Order Editor.</red>"));
            return;
        }
        guiManager.openGui(player, held);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("clericsbag.admin.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        plugin.reloadConfig();
        recipeManager.registerRecipe();
        sender.sendMessage(plugin.msg("reloaded"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.parse("<gold><bold>Cleric's Bag Commands:</bold></gold>"));
        sender.sendMessage(plugin.parse("<gray>/clericsbag give <player> [color]</gray>"));
        sender.sendMessage(plugin.parse("<gray>/clericsbag open</gray>"));
        sender.sendMessage(plugin.parse("<gray>/clericsbag reload</gray>"));
    }

    private Material parseBundleColor(String input) {
        String key = input.trim().toUpperCase(Locale.ROOT);
        if (key.equals("DEFAULT") || key.equals("NORMAL") || key.equals("BUNDLE")) {
            return Material.BUNDLE;
        }
        Material direct = Material.matchMaterial(key);
        if (direct != null && Tag.ITEMS_BUNDLES.isTagged(direct)) {
            return direct;
        }
        Material withSuffix = Material.matchMaterial(key + "_BUNDLE");
        if (withSuffix != null && Tag.ITEMS_BUNDLES.isTagged(withSuffix)) {
            return withSuffix;
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return List.of("give", "open", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> colors = new ArrayList<>();
            colors.add("default");
            for (Material mat : Tag.ITEMS_BUNDLES.getValues()) {
                if (mat != Material.BUNDLE) {
                    colors.add(mat.name().replace("_BUNDLE", "").toLowerCase(Locale.ROOT));
                }
            }
            return colors.stream()
                    .filter(c -> c.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
