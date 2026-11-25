package com.clericsbag.listener;

import com.clericsbag.ClericsBagPlugin;
import com.clericsbag.gui.BagGuiManager;
import com.clericsbag.item.ClericsBagStorage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BagCombatListener implements Listener {

    private final ClericsBagPlugin plugin;
    private final ClericsBagStorage storage;
    private final BagGuiManager guiManager;
    private final Map<UUID, Long> lastThrowTime = new ConcurrentHashMap<>();

    public BagCombatListener(
            ClericsBagPlugin plugin,
            ClericsBagStorage storage,
            BagGuiManager guiManager
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBagRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!storage.isClericsBag(item)) {
            return;
        }

        // Prevent vanilla bundle use / block interaction when holding Cleric's Bag
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("clericsbag.use")) {
            player.sendActionBar(plugin.rawMsg("no-permission"));
            return;
        }

        // Safety net: eject any stray vanilla bundle items without ever wiping them
        if (storage.ejectVanillaBundleItems(player, item)) {
            player.sendActionBar(plugin.rawMsg("menu-only"));
        }

        // Shift + Right-Click opens the 16-slot interactive Potion Menu GUI
        if (player.isSneaking()) {
            guiManager.openGui(player, item);
            return;
        }

        // Tiny anti-double-trigger guard so 1 click never fires 2 potions on lag
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("bag.throw-cooldown-ms", 150L);
        Long prev = lastThrowTime.get(player.getUniqueId());
        if (prev != null && (now - prev) < cooldownMs) {
            return;
        }
        lastThrowTime.put(player.getUniqueId(), now);

        ClericsBagStorage.PopResult pop = storage.popNextPotion(player, item);
        if (pop.thrown() == null) {
            player.sendActionBar(plugin.rawMsg("empty-bag"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }

        storage.launchPotion(player, pop.thrown());
        player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 0.9f, 1.05f);

        String thrownName = storage.describePotion(pop.thrown());
        int max = storage.getMaxPotions();

        if (pop.nextUp() != null) {
            String raw = plugin.getConfig().getString("messages.threw-potion", "")
                    .replace("{potion}", thrownName)
                    .replace("{next}", storage.describePotion(pop.nextUp()))
                    .replace("{remaining}", String.valueOf(pop.remaining()))
                    .replace("{max}", String.valueOf(max));
            player.sendActionBar(plugin.parse(raw));
        } else {
            String raw = plugin.getConfig().getString("messages.threw-last-potion", "")
                    .replace("{potion}", thrownName);
            player.sendActionBar(plugin.parse(raw));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastThrowTime.remove(event.getPlayer().getUniqueId());
    }
}
