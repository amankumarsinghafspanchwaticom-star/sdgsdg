package com.clericsbag.item;

import com.clericsbag.ClericsBagPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

public final class BagRecipeManager {

    private final ClericsBagPlugin plugin;
    private final ClericsBagStorage storage;
    private final NamespacedKey recipeKey;

    public BagRecipeManager(ClericsBagPlugin plugin, ClericsBagStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.recipeKey = new NamespacedKey(plugin, "clerics_bag_recipe");
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    /**
     * Exact recipe layout:
     * Row 1: BLAZE_ROD    | BREWING_STAND | BLAZE_ROD
     * Row 2: NETHER_WART  | ANY BUNDLE    | NETHER_WART
     * Row 3: DISPENSER    | DIAMOND_BLOCK | DISPENSER
     */
    @SuppressWarnings("deprecation")
    public void registerRecipe() {
        unregisterRecipe();
        if (!plugin.getConfig().getBoolean("crafting.enabled", true)) {
            return;
        }

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, storage.createClericsBag(Material.BUNDLE));
        recipe.shape("RBR", "WUW", "DXD");
        recipe.setIngredient('R', Material.BLAZE_ROD);
        recipe.setIngredient('B', Material.BREWING_STAND);
        recipe.setIngredient('W', Material.NETHER_WART);
        recipe.setIngredient('U', new RecipeChoice.MaterialChoice(Tag.ITEMS_BUNDLES));
        recipe.setIngredient('D', Material.DISPENSER);
        recipe.setIngredient('X', Material.DIAMOND_BLOCK);

        Bukkit.addRecipe(recipe);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.discoverRecipe(recipeKey);
        }
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    public void syncRecipeForPlayer(Player player) {
        if (plugin.getConfig().getBoolean("crafting.enabled", true)) {
            player.discoverRecipe(recipeKey);
        } else {
            player.undiscoverRecipe(recipeKey);
        }
    }
}
