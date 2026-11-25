package com.clericsbag.gui;

import com.clericsbag.ClericsBagPlugin;
import com.clericsbag.item.ClericsBagStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BagGuiManager {

    public static final int GUI_SIZE = 27;
    public static final int POTION_SLOTS = ClericsBagStorage.CAPACITY; // 0..15

    public static final int BTN_CYCLE = 18;
    public static final int BTN_REVERSE = 20;
    public static final int BTN_INFO = 22;
    public static final int BTN_COMPACT = 24;
    public static final int BTN_CLOSE = 26;

    private final ClericsBagPlugin plugin;
    private final ClericsBagStorage storage;
    private final Map<UUID, String> openBagSessions = new ConcurrentHashMap<>();

    public BagGuiManager(ClericsBagPlugin plugin, ClericsBagStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public boolean hasOpenGui(Player player) {
        return openBagSessions.containsKey(player.getUniqueId());
    }

    public String getOpenBagUid(Player player) {
        return openBagSessions.get(player.getUniqueId());
    }

    public void openGui(Player player, ItemStack bag) {
        if (player == null || !storage.isClericsBag(bag)) {
            return;
        }
        String bagUid = storage.getOrAssignBagUid(bag);
        if (bagUid == null) {
            return;
        }

        BagGuiHolder holder = new BagGuiHolder(bagUid);
        Component title = plugin.parse("<dark_gray>Cleric's Bag <dark_gray>»</dark_gray> <gold>Order Queue</gold></dark_gray>");
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack[] stored = storage.readSlots(bag);
        for (int i = 0; i < POTION_SLOTS; i++) {
            if (storage.isSupportedPotion(stored[i])) {
                inv.setItem(i, stored[i].clone());
            } else {
                inv.setItem(i, null);
            }
        }

        paintControlBar(inv, stored);

        openBagSessions.put(player.getUniqueId(), bagUid);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.85f, 1.15f);
    }

    public void refreshControlBar(Inventory inv) {
        ItemStack[] snapshot = extractPotionSlots(inv);
        paintControlBar(inv, snapshot);
    }

    public void paintControlBar(Inventory inv, ItemStack[] potionSlots) {
        ItemStack filler = createButton(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        inv.setItem(16, filler);
        inv.setItem(17, filler);
        for (int s = 18; s < GUI_SIZE; s++) {
            inv.setItem(s, filler);
        }

        inv.setItem(BTN_CYCLE, createButton(
                Material.HOPPER,
                "<aqua><bold>Cycle Queue (+1)</bold></aqua>",
                List.of(
                        "<gray>Moves slot <white>#1</white> to the back</gray>",
                        "<gray>so slot <white>#2</white> throws next.</gray>"
                )
        ));

        inv.setItem(BTN_REVERSE, createButton(
                Material.ARROW,
                "<light_purple><bold>Reverse Order</bold></light_purple>",
                List.of(
                        "<gray>Flips the entire potion</gray>",
                        "<gray>throw order in 1 click.</gray>"
                )
        ));

        List<ItemStack> ordered = storage.getOrderedNonEmpty(potionSlots);
        int max = storage.getMaxPotions();
        List<String> infoLore = new ArrayList<>();
        infoLore.add("<gray>Loaded: <white>" + ordered.size() + "/" + max + "</white></gray>");
        infoLore.add("<gray>Slots <white>1–16</white> throw left-to-right.</gray>");
        infoLore.add("<gray>Drag & swap freely without emptying!</gray>");
        if (!ordered.isEmpty()) {
            infoLore.add("");
            infoLore.add("<gold>Next Up:</gold>");
            int show = Math.min(5, ordered.size());
            for (int i = 0; i < show; i++) {
                infoLore.add("<yellow>#" + (i + 1) + " <white>" + storage.describePotion(ordered.get(i)) + "</white></yellow>");
            }
        }
        inv.setItem(BTN_INFO, createButton(
                Material.BREWING_STAND,
                "<gold><bold>Throw Order Guide</bold></gold>",
                infoLore
        ));

        inv.setItem(BTN_COMPACT, createButton(
                Material.COMPARATOR,
                "<green><bold>Compact Gaps</bold></green>",
                List.of(
                        "<gray>Slides all potions left</gray>",
                        "<gray>while keeping exact order.</gray>"
                )
        ));

        inv.setItem(BTN_CLOSE, createButton(
                Material.BARRIER,
                "<red><bold>Save & Close</bold></red>",
                List.of("<gray>Saves your exact potion order.</gray>")
        ));
    }

    public ItemStack[] extractPotionSlots(Inventory inv) {
        ItemStack[] result = storage.createEmptySlotArray();
        for (int i = 0; i < POTION_SLOTS; i++) {
            ItemStack item = inv.getItem(i);
            if (storage.isSupportedPotion(item)) {
                ItemStack single = item.clone();
                single.setAmount(1);
                result[i] = single;
            }
        }
        return result;
    }

    public void applySlotsToGui(Inventory inv, ItemStack[] slots) {
        for (int i = 0; i < POTION_SLOTS; i++) {
            if (i < slots.length && storage.isSupportedPotion(slots[i])) {
                inv.setItem(i, slots[i].clone());
            } else {
                inv.setItem(i, null);
            }
        }
        paintControlBar(inv, slots);
    }

    public void saveGuiToPlayerBag(Player player, Inventory inv, String bagUid) {
        ItemStack bag = findBagByUid(player, bagUid);
        ItemStack[] slots = extractPotionSlots(inv);
        if (bag != null) {
            storage.writeSlots(bag, slots);
        }
    }

    public void endSession(Player player, Inventory inv, String bagUid) {
        saveGuiToPlayerBag(player, inv, bagUid);
        openBagSessions.remove(player.getUniqueId());
    }

    public void closeAllSafely() {
        for (UUID playerId : new ArrayList<>(openBagSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof BagGuiHolder holder) {
                    saveGuiToPlayerBag(player, top, holder.getBagUid());
                }
                player.closeInventory();
            }
        }
        openBagSessions.clear();
    }

    public ItemStack findBagByUid(Player player, String targetUid) {
        if (player == null || targetUid == null) {
            return null;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (storage.isClericsBag(item)) {
                String uid = storage.getOrAssignBagUid(item);
                if (targetUid.equals(uid)) {
                    return item;
                }
            }
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (storage.isClericsBag(offhand) && targetUid.equals(storage.getOrAssignBagUid(offhand))) {
            return offhand;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (storage.isClericsBag(cursor) && targetUid.equals(storage.getOrAssignBagUid(cursor))) {
            return cursor;
        }
        return null;
    }

    private ItemStack createButton(Material material, String nameMini, List<String> loreMini) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.parse(nameMini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (!loreMini.isEmpty()) {
                List<Component> lore = loreMini.stream()
                        .map(line -> plugin.parse(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                        .toList();
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
