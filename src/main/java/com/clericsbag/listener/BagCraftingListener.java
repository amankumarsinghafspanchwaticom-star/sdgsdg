package com.clericsbag.listener;

import com.clericsbag.ClericsBagPlugin;
import com.clericsbag.item.BagRecipeManager;
import com.clericsbag.item.ClericsBagStorage;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BundleMeta;

public final class BagCraftingListener implements Listener {

    private final ClericsBagPlugin plugin;
    private final ClericsBagStorage storage;
    private final BagRecipeManager recipeManager;

    public BagCraftingListener(
            ClericsBagPlugin plugin,
            ClericsBagStorage storage,
            BagRecipeManager recipeManager
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.recipeManager = recipeManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        recipeManager.syncRecipeForPlayer(event.getPlayer());
    }

    /**
     * Preserves the exact color of the bundle placed in slot 5 (center of 3x3 matrix, index 4)
     * and blocks crafting if a non-empty bundle or an existing Cleric's Bag is placed in the center.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(recipeManager.getRecipeKey())) {
            return;
        }

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix.length < 9) {
            return;
        }

        ItemStack centerBundle = matrix[4];
        if (centerBundle == null || !Tag.ITEMS_BUNDLES.isTagged(centerBundle.getType())) {
            inv.setResult(null);
            return;
        }

        // Don't allow recrafting an already-created Cleric's Bag (would wipe stored potions)
        if (storage.isClericsBag(centerBundle)) {
            inv.setResult(null);
            return;
        }

        // Protect player items if they accidentally put a non-empty vanilla bundle in the center
        if (plugin.getConfig().getBoolean("crafting.require-empty-bundle", true)
                && centerBundle.hasItemMeta()
                && centerBundle.getItemMeta() instanceof BundleMeta bm
                && bm.hasItems()) {
            inv.setResult(null);
            return;
        }

        // Preserve the exact bundle color (red, purple, black, default, etc.) on the crafted Cleric's Bag
        Material bundleColor = centerBundle.getType();
        inv.setResult(storage.createClericsBag(bundleColor));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(recipeManager.getRecipeKey())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("clericsbag.use")) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("no-permission"));
        }
    }
}
