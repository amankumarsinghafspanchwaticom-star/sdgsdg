package com.clericsbag;

import com.clericsbag.command.ClericsBagCommand;
import com.clericsbag.gui.BagGuiManager;
import com.clericsbag.item.BagRecipeManager;
import com.clericsbag.item.ClericsBagStorage;
import com.clericsbag.listener.BagCombatListener;
import com.clericsbag.listener.BagCraftingListener;
import com.clericsbag.listener.BagInventoryListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClericsBagPlugin extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ClericsBagStorage storage;
    private BagRecipeManager recipeManager;
    private BagGuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.storage = new ClericsBagStorage(this);
        this.recipeManager = new BagRecipeManager(this, storage);
        this.guiManager = new BagGuiManager(this, storage);

        this.recipeManager.registerRecipe();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BagCombatListener(this, storage, guiManager), this);
        pm.registerEvents(new BagInventoryListener(this, storage, guiManager), this);
        pm.registerEvents(new BagCraftingListener(this, storage, recipeManager), this);

        PluginCommand cmd = getCommand("clericsbag");
        if (cmd != null) {
            ClericsBagCommand executor = new ClericsBagCommand(this, storage, recipeManager, guiManager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("ClericsBag v" + getPluginMeta().getVersion() + " enabled (16-slot ordered potion bag).");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeAllSafely();
        }
        if (recipeManager != null) {
            recipeManager.unregisterRecipe();
        }
    }

    public ClericsBagStorage getStorage() {
        return storage;
    }

    public BagRecipeManager getRecipeManager() {
        return recipeManager;
    }

    public BagGuiManager getGuiManager() {
        return guiManager;
    }

    public Component msg(String path) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw = getConfig().getString("messages." + path, "");
        return MINI_MESSAGE.deserialize(prefix + raw);
    }

    public Component rawMsg(String path) {
        String raw = getConfig().getString("messages." + path, "");
        return MINI_MESSAGE.deserialize(raw);
    }

    public Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }
}
