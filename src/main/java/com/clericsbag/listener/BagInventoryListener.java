package com.clericsbag.listener;

import com.clericsbag.ClericsBagPlugin;
import com.clericsbag.gui.BagGuiHolder;
import com.clericsbag.gui.BagGuiManager;
import com.clericsbag.item.ClericsBagStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BagInventoryListener implements Listener {

    private final ClericsBagPlugin plugin;
    private final ClericsBagStorage storage;
    private final BagGuiManager guiManager;

    public BagInventoryListener(
            ClericsBagPlugin plugin,
            ClericsBagStorage storage,
            BagGuiManager guiManager
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.guiManager = guiManager;
    }

    /**
     * Blocks renaming Cleric's Bag in an anvil.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        if (storage.isClericsBag(inv.getFirstItem()) || storage.isClericsBag(inv.getSecondItem())) {
            event.setResult(null);
        }
    }

    /**
     * Creative-mode safety net: prevents client-side vanilla bundle insertion in Creative Mode
     * by ejecting any vanilla BundleMeta items back to the player safely.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (storage.isClericsBag(cursor) && storage.ejectVanillaBundleItems(player, cursor)) {
            event.setCursor(cursor);
            player.sendActionBar(plugin.rawMsg("menu-only"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Block taking a renamed Cleric's Bag out of an anvil
        if (event.getInventory() instanceof AnvilInventory inv
                && event.getRawSlot() == 2
                && (storage.isClericsBag(inv.getFirstItem()) || storage.isClericsBag(inv.getSecondItem()) || storage.isClericsBag(event.getCurrentItem()))) {
            event.setCancelled(true);
            player.sendActionBar(plugin.rawMsg("rename-blocked"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof BagGuiHolder holder) {
            handleGuiClick(event, player, top, holder);
            return;
        }

        // =========================================================================
        // OUTSIDE THE MODDED GUI:
        // Strictly block ALL vanilla 1.21.11 bundle insertion & extraction (both
        // Left-Click and Right-Click, for BOTH normal items AND potions).
        // Potions can ONLY be stored inside the modded Cleric's Bag menu!
        // =========================================================================
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ClickType click = event.getClick();

        boolean clickedIsBag = storage.isClericsBag(clicked);
        boolean cursorIsBag = storage.isClericsBag(cursor);
        boolean clickedNotEmpty = clicked != null && !clicked.getType().isAir();
        boolean cursorNotEmpty = cursor != null && !cursor.getType().isAir();

        // 1. Clicking ONTO Cleric's Bag while holding ANY item on cursor
        if (clickedIsBag && cursorNotEmpty) {
            if (click == ClickType.LEFT) {
                // Cancel vanilla 1.21.11 bundle Left-Click insertion!
                event.setCancelled(true);
                if (isNormalSwappableSlot(event)) {
                    ItemStack oldBag = clicked.clone();
                    ItemStack oldCursor = cursor.clone();
                    event.setCurrentItem(oldCursor);
                    player.setItemOnCursor(oldBag);
                    if (storage.isSupportedPotion(oldCursor)) {
                        player.sendActionBar(plugin.rawMsg("menu-only"));
                    }
                } else {
                    player.sendActionBar(plugin.rawMsg("menu-only"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
                }
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
            if (click == ClickType.RIGHT) {
                event.setCancelled(true);
                player.sendActionBar(plugin.rawMsg("menu-only"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
        }

        // 2. Clicking Cleric's Bag (held on cursor) ONTO ANY non-empty slot
        if (cursorIsBag && clickedNotEmpty) {
            if (click == ClickType.LEFT) {
                // Cancel vanilla 1.21.11 bundle Left-Click suck-in!
                event.setCancelled(true);
                if (isNormalSwappableSlot(event)) {
                    ItemStack oldSlotItem = clicked.clone();
                    ItemStack oldBag = cursor.clone();
                    event.setCurrentItem(oldBag);
                    player.setItemOnCursor(oldSlotItem);
                    if (storage.isSupportedPotion(oldSlotItem)) {
                        player.sendActionBar(plugin.rawMsg("menu-only"));
                    }
                } else {
                    player.sendActionBar(plugin.rawMsg("menu-only"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
                }
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
            if (click == ClickType.RIGHT) {
                event.setCancelled(true);
                player.sendActionBar(plugin.rawMsg("menu-only"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
        }

        // 3. Right-clicking Cleric's Bag with an EMPTY cursor opens the modded Potion Menu GUI
        if (clickedIsBag && !cursorNotEmpty && click == ClickType.RIGHT) {
            event.setCancelled(true);
            if (player.hasPermission("clericsbag.use")) {
                storage.ejectVanillaBundleItems(player, clicked);
                Bukkit.getScheduler().runTask(plugin, () -> guiManager.openGui(player, clicked));
            } else {
                player.sendActionBar(plugin.rawMsg("no-permission"));
            }
        }
    }

    private boolean isNormalSwappableSlot(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return false;
        }
        InventoryType.SlotType type = event.getSlotType();
        return type == InventoryType.SlotType.CONTAINER || type == InventoryType.SlotType.QUICKBAR;
    }

    private void handleGuiClick(InventoryClickEvent event, Player player, Inventory top, BagGuiHolder holder) {
        String openUid = holder.getBagUid();

        // Anti-dupe: never allow interacting with the open Cleric's Bag item itself
        if (isLockedBag(event.getCurrentItem(), openUid) || isLockedBag(event.getCursor(), openUid)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (isLockedBag(hotbar, openUid)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND && isLockedBag(player.getInventory().getItemInOffHand(), openUid)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean clickedTop = rawSlot >= 0 && rawSlot < top.getSize();

        // Also block vanilla bundle clicks on ANY OTHER Cleric's Bag sitting in the player's bottom inventory while GUI is open
        if (!clickedTop) {
            ItemStack clicked = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            boolean clickedBag = storage.isClericsBag(clicked);
            boolean cursorBag = storage.isClericsBag(cursor);
            boolean clickedNotEmpty = clicked != null && !clicked.getType().isAir();
            boolean cursorNotEmpty = cursor != null && !cursor.getType().isAir();
            if ((clickedBag && cursorNotEmpty) || (cursorBag && clickedNotEmpty)) {
                event.setCancelled(true);
                player.sendActionBar(plugin.rawMsg("menu-only"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                return;
            }
        }

        // Control bar buttons & borders (slots 16..26)
        if (clickedTop && rawSlot >= BagGuiManager.POTION_SLOTS) {
            event.setCancelled(true);
            handleControlButton(player, top, holder, rawSlot);
            return;
        }

        // Clicking inside slots 0..15 (the 16 ordered potion slots — POTIONS ONLY)
        if (clickedTop) {
            int max = storage.getMaxPotions();
            if (rawSlot >= max) {
                event.setCancelled(true);
                return;
            }

            if (event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (hotbarItem != null && !hotbarItem.getType().isAir() && !storage.isSupportedPotion(hotbarItem)) {
                    event.setCancelled(true);
                    player.sendActionBar(plugin.rawMsg("potions-only"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                    return;
                }
                if (hotbarItem != null && hotbarItem.getAmount() > 1) {
                    event.setCancelled(true);
                    return;
                }
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhand = player.getInventory().getItemInOffHand();
                if (!offhand.getType().isAir() && (!storage.isSupportedPotion(offhand) || offhand.getAmount() > 1)) {
                    event.setCancelled(true);
                    player.sendActionBar(plugin.rawMsg("potions-only"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                    return;
                }
            } else {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    if (!storage.isSupportedPotion(cursor)) {
                        event.setCancelled(true);
                        player.sendActionBar(plugin.rawMsg("potions-only"));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                        return;
                    }
                    // If player somehow holds a stacked potion (>1), place only 1 into the slot cleanly
                    if (cursor.getAmount() > 1) {
                        event.setCancelled(true);
                        ItemStack existing = top.getItem(rawSlot);
                        if (existing == null || existing.getType().isAir()) {
                            ItemStack single = cursor.clone();
                            single.setAmount(1);
                            top.setItem(rawSlot, single);
                            cursor.setAmount(cursor.getAmount() - 1);
                            player.setItemOnCursor(cursor);
                            scheduleGuiSync(player, top, openUid);
                        }
                        return;
                    }
                }
            }

            scheduleGuiSync(player, top, openUid);
            return;
        }

        // Shift-clicking from player's bottom inventory into the bag GUI
        if (event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack moving = event.getCurrentItem();
            if (!storage.isSupportedPotion(moving)) {
                if (moving != null && !moving.getType().isAir()) {
                    player.sendActionBar(plugin.rawMsg("potions-only"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                }
                return;
            }
            int max = storage.getMaxPotions();
            int freeSlot = -1;
            for (int i = 0; i < max; i++) {
                ItemStack slotItem = top.getItem(i);
                if (slotItem == null || slotItem.getType().isAir()) {
                    freeSlot = i;
                    break;
                }
            }
            if (freeSlot == -1) {
                String raw = plugin.getConfig().getString("messages.bag-full", "")
                        .replace("{max}", String.valueOf(max));
                player.sendActionBar(plugin.parse(raw));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.75f);
                return;
            }
            ItemStack single = moving.clone();
            single.setAmount(1);
            top.setItem(freeSlot, single);
            moving.setAmount(moving.getAmount() - 1);
            if (moving.getAmount() <= 0) {
                event.setCurrentItem(new ItemStack(Material.AIR));
            }
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 1.15f);
            scheduleGuiSync(player, top, openUid);
        }
    }

    private void handleControlButton(Player player, Inventory top, BagGuiHolder holder, int slot) {
        ItemStack[] current = guiManager.extractPotionSlots(top);
        List<ItemStack> ordered = storage.getOrderedNonEmpty(current);

        switch (slot) {
            case BagGuiManager.BTN_CYCLE -> {
                if (ordered.size() > 1) {
                    ItemStack first = ordered.remove(0);
                    ordered.add(first);
                    ItemStack[] updated = toSlotArray(ordered);
                    guiManager.applySlotsToGui(top, updated);
                    guiManager.saveGuiToPlayerBag(player, top, holder.getBagUid());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.25f);
                }
            }
            case BagGuiManager.BTN_REVERSE -> {
                if (ordered.size() > 1) {
                    List<ItemStack> reversed = new ArrayList<>(ordered.size());
                    for (int i = ordered.size() - 1; i >= 0; i--) {
                        reversed.add(ordered.get(i));
                    }
                    ItemStack[] updated = toSlotArray(reversed);
                    guiManager.applySlotsToGui(top, updated);
                    guiManager.saveGuiToPlayerBag(player, top, holder.getBagUid());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                }
            }
            case BagGuiManager.BTN_COMPACT -> {
                ItemStack[] compacted = storage.compactSlots(current);
                guiManager.applySlotsToGui(top, compacted);
                guiManager.saveGuiToPlayerBag(player, top, holder.getBagUid());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.35f);
            }
            case BagGuiManager.BTN_CLOSE -> {
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 1.0f);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    private ItemStack[] toSlotArray(List<ItemStack> ordered) {
        ItemStack[] slots = storage.createEmptySlotArray();
        int limit = Math.min(storage.getMaxPotions(), ordered.size());
        for (int i = 0; i < limit; i++) {
            slots[i] = ordered.get(i);
        }
        return slots;
    }

    private void scheduleGuiSync(Player player, Inventory top, String bagUid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            guiManager.refreshControlBar(top);
            guiManager.saveGuiToPlayerBag(player, top, bagUid);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();

        // Outside the GUI: block dragging a Cleric's Bag onto non-empty slots or dragging items over a Cleric's Bag
        if (!(top.getHolder() instanceof BagGuiHolder holder)) {
            boolean draggingBag = storage.isClericsBag(event.getOldCursor());
            for (int rawSlot : event.getRawSlots()) {
                ItemStack existing = event.getView().getItem(rawSlot);
                if (storage.isClericsBag(existing)) {
                    event.setCancelled(true);
                    return;
                }
                if (draggingBag && existing != null && !existing.getType().isAir()) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (isLockedBag(event.getOldCursor(), holder.getBagUid())) {
            event.setCancelled(true);
            return;
        }
        int max = storage.getMaxPotions();
        boolean touchesTop = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                touchesTop = true;
                if (rawSlot >= max || !storage.isSupportedPotion(event.getOldCursor())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (touchesTop) {
            scheduleGuiSync(player, top, holder.getBagUid());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getInventory();
        if (top.getHolder() instanceof BagGuiHolder holder) {
            guiManager.endSession(player, top, holder.getBagUid());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropWhileOpen(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        String openUid = guiManager.getOpenBagUid(player);
        if (openUid != null && isLockedBag(event.getItemDrop().getItemStack(), openUid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandWhileOpen(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String openUid = guiManager.getOpenBagUid(player);
        if (openUid != null && (isLockedBag(event.getMainHandItem(), openUid) || isLockedBag(event.getOffHandItem(), openUid))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof BagGuiHolder holder) {
            guiManager.endSession(player, top, holder.getBagUid());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof BagGuiHolder holder) {
            guiManager.endSession(player, top, holder.getBagUid());
        }
    }

    private boolean isLockedBag(ItemStack item, String openUid) {
        if (openUid == null || !storage.isClericsBag(item)) {
            return false;
        }
        return openUid.equals(storage.getOrAssignBagUid(item));
    }
}
