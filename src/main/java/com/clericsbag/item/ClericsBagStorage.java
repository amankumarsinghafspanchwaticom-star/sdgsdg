package com.clericsbag.item;

import com.clericsbag.ClericsBagPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.SplashPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ClericsBagStorage {

    public static final int CAPACITY = 16;

    private final ClericsBagPlugin plugin;
    private final NamespacedKey bagMarkerKey;
    private final NamespacedKey bagUidKey;
    private final NamespacedKey bagContentsKey;

    public ClericsBagStorage(ClericsBagPlugin plugin) {
        this.plugin = plugin;
        this.bagMarkerKey = new NamespacedKey(plugin, "clerics_bag");
        this.bagUidKey = new NamespacedKey(plugin, "clerics_bag_uid");
        this.bagContentsKey = new NamespacedKey(plugin, "clerics_bag_contents");
    }

    public int getMaxPotions() {
        int cfg = plugin.getConfig().getInt("bag.max-potions", CAPACITY);
        return Math.max(1, Math.min(CAPACITY, cfg));
    }

    public ItemStack createClericsBag(Material bundleColorMaterial) {
        Material mat = (bundleColorMaterial != null && Tag.ITEMS_BUNDLES.isTagged(bundleColorMaterial))
                ? bundleColorMaterial
                : Material.BUNDLE;

        ItemStack bag = new ItemStack(mat, 1);
        ItemMeta meta = bag.getItemMeta();
        if (meta == null) {
            return bag;
        }

        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.setItems(List.of());
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(bagMarkerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(bagUidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        bag.setItemMeta(meta);

        ItemStack[] emptySlots = createEmptySlotArray();
        writeSlots(bag, emptySlots);
        return bag;
    }

    public boolean isClericsBag(ItemStack item) {
        if (item == null || item.getType().isAir() || !Tag.ITEMS_BUNDLES.isTagged(item.getType())) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(bagMarkerKey, PersistentDataType.BYTE);
    }

    /**
     * Safety net: if vanilla items somehow slipped into BundleMeta (e.g., Creative-mode client packets),
     * safely ejects them back to the player's inventory/ground instead of ever deleting them.
     * Returns true if any vanilla bundle contents were ejected.
     */
    public boolean ejectVanillaBundleItems(Player player, ItemStack bag) {
        if (!isClericsBag(bag)) {
            return false;
        }
        ItemMeta meta = bag.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return false;
        }

        List<ItemStack> trapped = new ArrayList<>(bundleMeta.getItems());
        bundleMeta.setItems(List.of());
        bag.setItemMeta(bundleMeta);

        if (player != null && player.isOnline()) {
            for (ItemStack item : trapped) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
                overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
        }
        return !trapped.isEmpty();
    }

    public String getOrAssignBagUid(ItemStack bag) {
        if (!isClericsBag(bag)) {
            return null;
        }
        ItemMeta meta = bag.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uid = pdc.get(bagUidKey, PersistentDataType.STRING);
        if (uid == null || uid.isBlank()) {
            uid = UUID.randomUUID().toString();
            pdc.set(bagUidKey, PersistentDataType.STRING, uid);
            bag.setItemMeta(meta);
        }
        return uid;
    }

    public boolean isSupportedPotion(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        return type == Material.POTION
                || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION;
    }

    public ItemStack[] createEmptySlotArray() {
        ItemStack[] slots = new ItemStack[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            slots[i] = new ItemStack(Material.AIR);
        }
        return slots;
    }

    /**
     * Reads all 16 queue slots from PDC. Empty slots are returned as AIR ItemStacks (never null).
     */
    public ItemStack[] readSlots(ItemStack bag) {
        ItemStack[] result = createEmptySlotArray();
        if (!isClericsBag(bag)) {
            return result;
        }
        ItemMeta meta = bag.getItemMeta();
        if (meta == null) {
            return result;
        }
        byte[] raw = meta.getPersistentDataContainer().get(bagContentsKey, PersistentDataType.BYTE_ARRAY);
        if (raw == null || raw.length == 0) {
            return result;
        }
        try {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(raw);
            int limit = Math.min(CAPACITY, decoded.length);
            for (int i = 0; i < limit; i++) {
                ItemStack entry = decoded[i];
                if (isSupportedPotion(entry)) {
                    ItemStack single = entry.clone();
                    single.setAmount(1);
                    result[i] = single;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize Cleric's Bag contents: " + e.getMessage());
        }
        return result;
    }

    /**
     * Writes the 16-slot array back into PDC and refreshes display name, glint, and live queue lore.
     */
    public void writeSlots(ItemStack bag, ItemStack[] slots) {
        if (!isClericsBag(bag)) {
            return;
        }
        int max = getMaxPotions();
        ItemStack[] sanitized = createEmptySlotArray();
        int count = 0;
        if (slots != null) {
            int limit = Math.min(CAPACITY, slots.length);
            for (int i = 0; i < limit; i++) {
                ItemStack item = slots[i];
                if (count < max && isSupportedPotion(item)) {
                    ItemStack single = item.clone();
                    single.setAmount(1);
                    sanitized[i] = single;
                    count++;
                }
            }
        }

        ItemMeta meta = bag.getItemMeta();
        if (meta == null) {
            return;
        }

        // Keep vanilla bundle contents strictly empty — storage lives solely in PDC via the modded GUI
        if (meta instanceof BundleMeta bundleMeta) {
            bundleMeta.setItems(List.of());
        }

        byte[] encoded = ItemStack.serializeItemsAsBytes(sanitized);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(bagMarkerKey, PersistentDataType.BYTE, (byte) 1);
        if (!pdc.has(bagUidKey, PersistentDataType.STRING)) {
            pdc.set(bagUidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        pdc.set(bagContentsKey, PersistentDataType.BYTE_ARRAY, encoded);

        applyPresentation(meta, sanitized, count, max);
        bag.setItemMeta(meta);
    }

    public int countPotions(ItemStack[] slots) {
        int count = 0;
        for (ItemStack item : slots) {
            if (isSupportedPotion(item)) {
                count++;
            }
        }
        return count;
    }

    public List<ItemStack> getOrderedNonEmpty(ItemStack[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : slots) {
            if (isSupportedPotion(item)) {
                ItemStack single = item.clone();
                single.setAmount(1);
                list.add(single);
            }
        }
        return list;
    }

    public ItemStack[] compactSlots(ItemStack[] slots) {
        List<ItemStack> ordered = getOrderedNonEmpty(slots);
        ItemStack[] compacted = createEmptySlotArray();
        int limit = Math.min(getMaxPotions(), ordered.size());
        for (int i = 0; i < limit; i++) {
            compacted[i] = ordered.get(i);
        }
        return compacted;
    }

    /**
     * Pops the first non-empty potion in queue order, compacts remaining potions left, and saves.
     */
    public PopResult popNextPotion(Player owner, ItemStack bag) {
        ejectVanillaBundleItems(owner, bag);
        ItemStack[] slots = readSlots(bag);
        List<ItemStack> ordered = getOrderedNonEmpty(slots);
        if (ordered.isEmpty()) {
            return new PopResult(null, null, 0);
        }
        ItemStack popped = ordered.remove(0);
        ItemStack nextUp = ordered.isEmpty() ? null : ordered.get(0);

        ItemStack[] updated = createEmptySlotArray();
        for (int i = 0; i < Math.min(getMaxPotions(), ordered.size()); i++) {
            updated[i] = ordered.get(i);
        }
        writeSlots(bag, updated);
        return new PopResult(popped, nextUp, ordered.size());
    }

    /**
     * Launches the potion accurately in Paper 1.21.11 (SplashPotion vs LingeringPotion).
     * Regular drinkable potions are seamlessly converted into Splash Potions keeping exact PotionMeta.
     */
    public void launchPotion(Player thrower, ItemStack potionItem) {
        if (thrower == null || !isSupportedPotion(potionItem)) {
            return;
        }

        double speed = plugin.getConfig().getDouble("bag.throw-speed", 1.25);
        Vector velocity = thrower.getEyeLocation().getDirection().normalize().multiply(speed);

        if (potionItem.getType() == Material.LINGERING_POTION) {
            ItemStack lingeringStack = potionItem.clone();
            lingeringStack.setAmount(1);
            LingeringPotion thrown = thrower.launchProjectile(LingeringPotion.class, velocity);
            thrown.setShooter(thrower);
            thrown.setItem(lingeringStack);
        } else {
            ItemStack splashStack = convertToSplashIfNeeded(potionItem);
            SplashPotion thrown = thrower.launchProjectile(SplashPotion.class, velocity);
            thrown.setShooter(thrower);
            thrown.setItem(splashStack);
        }

        if (plugin.getConfig().getBoolean("bag.show-particles", true)) {
            Color dustColor = resolvePotionColor(potionItem);
            thrower.getWorld().spawnParticle(
                    Particle.ENTITY_EFFECT,
                    thrower.getEyeLocation().add(thrower.getEyeLocation().getDirection().multiply(0.6)),
                    8,
                    0.15, 0.15, 0.15,
                    dustColor
            );
        }
    }

    private ItemStack convertToSplashIfNeeded(ItemStack source) {
        if (source.getType() == Material.SPLASH_POTION) {
            ItemStack clone = source.clone();
            clone.setAmount(1);
            return clone;
        }
        ItemStack splash = new ItemStack(Material.SPLASH_POTION, 1);
        if (source.hasItemMeta() && source.getItemMeta() instanceof PotionMeta srcMeta
                && splash.getItemMeta() instanceof PotionMeta dstMeta) {
            if (srcMeta.getBasePotionType() != null) {
                dstMeta.setBasePotionType(srcMeta.getBasePotionType());
            }
            for (PotionEffect effect : srcMeta.getCustomEffects()) {
                dstMeta.addCustomEffect(effect, true);
            }
            if (srcMeta.hasColor()) {
                dstMeta.setColor(srcMeta.getColor());
            }
            if (srcMeta.hasDisplayName()) {
                dstMeta.displayName(srcMeta.displayName());
            }
            splash.setItemMeta(dstMeta);
        }
        return splash;
    }

    private Color resolvePotionColor(ItemStack potion) {
        if (potion.hasItemMeta() && potion.getItemMeta() instanceof PotionMeta pm) {
            if (pm.hasColor() && pm.getColor() != null) {
                return pm.getColor();
            }
            if (!pm.getCustomEffects().isEmpty()) {
                return pm.getCustomEffects().get(0).getType().getColor();
            }
            PotionType base = pm.getBasePotionType();
            if (base != null && !base.getPotionEffects().isEmpty()) {
                return base.getPotionEffects().get(0).getType().getColor();
            }
        }
        return Color.fromRGB(160, 100, 255);
    }

    private void applyPresentation(ItemMeta meta, ItemStack[] slots, int count, int max) {
        String rawName = plugin.getConfig().getString("bag.name", "<gold><bold>Cleric's Bag</bold></gold>");
        meta.displayName(plugin.parse(rawName).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));

        boolean glint = plugin.getConfig().getBoolean("bag.enchantment-glint", true);
        meta.setEnchantmentGlintOverride(glint);

        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(buildCapacityBar(count, max)));

        List<ItemStack> ordered = getOrderedNonEmpty(slots);
        int previewLimit = Math.max(1, plugin.getConfig().getInt("bag.lore-preview-count", 5));

        if (ordered.isEmpty()) {
            lore.add(noItalic("<dark_gray>No potions loaded yet.</dark_gray>"));
        } else {
            lore.add(noItalic("<gray>Throw Order:</gray>"));
            int shown = Math.min(previewLimit, ordered.size());
            for (int i = 0; i < shown; i++) {
                String prefixColor = (i == 0) ? "<yellow>▸ #1 " : "<gray>  #" + (i + 1) + " ";
                String closeTag = (i == 0) ? "</yellow>" : "</gray>";
                lore.add(noItalic(prefixColor + "<white>" + describePotion(ordered.get(i)) + "</white>" + closeTag));
            }
            if (ordered.size() > shown) {
                lore.add(noItalic("<dark_gray>  +" + (ordered.size() - shown) + " more in queue...</dark_gray>"));
            }
        }

        lore.add(Component.empty());
        lore.add(noItalic("<gold>Right-Click:</gold> <gray>Throw next potion</gray>"));
        lore.add(noItalic("<aqua>Shift + Right-Click:</aqua> <gray>Open Potion Menu</gray>"));
        lore.add(noItalic("<dark_gray>Potions Only — Load & reorder inside menu</dark_gray>"));

        meta.lore(lore);
    }

    private String buildCapacityBar(int count, int max) {
        String fillColor = count == 0 ? "<dark_gray>" : (count >= max ? "<green>" : "<gold>");
        StringBuilder sb = new StringBuilder();
        sb.append("<dark_gray>[</dark_gray>").append(fillColor);
        for (int i = 0; i < count; i++) {
            sb.append("■");
        }
        sb.append("</").append(fillColor.substring(1)).append("<dark_gray>");
        for (int i = count; i < max; i++) {
            sb.append("□");
        }
        sb.append("]</dark_gray> <white>").append(count).append("/").append(max).append("</white>");
        return sb.toString();
    }

    private Component noItalic(String mini) {
        return plugin.parse(mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public String describePotion(ItemStack potion) {
        if (potion == null || potion.getType().isAir()) {
            return "Empty";
        }
        String kindPrefix = switch (potion.getType()) {
            case SPLASH_POTION -> "Splash ";
            case LINGERING_POTION -> "Lingering ";
            default -> "";
        };

        if (potion.hasItemMeta() && potion.getItemMeta() instanceof PotionMeta pm) {
            if (pm.hasDisplayName() && pm.displayName() != null) {
                String custom = PlainTextComponentSerializer.plainText().serialize(pm.displayName());
                if (!custom.isBlank()) {
                    return custom;
                }
            }
            PotionType base = pm.getBasePotionType();
            if (base != null && base != PotionType.WATER && base != PotionType.MUNDANE && base != PotionType.THICK && base != PotionType.AWKWARD) {
                return kindPrefix + formatPotionTypeName(base.name());
            }
            if (!pm.getCustomEffects().isEmpty()) {
                PotionEffect eff = pm.getCustomEffects().get(0);
                String name = formatWords(eff.getType().getKey().getKey());
                int level = eff.getAmplifier() + 1;
                return kindPrefix + name + (level > 1 ? " " + toRoman(level) : "");
            }
            if (base != null) {
                return kindPrefix + formatWords(base.name());
            }
        }
        return formatWords(potion.getType().name());
    }

    private String formatPotionTypeName(String enumName) {
        String upper = enumName.toUpperCase(Locale.ROOT);
        boolean isStrong = upper.startsWith("STRONG_");
        boolean isLong = upper.startsWith("LONG_");
        String core = upper;
        if (isStrong) {
            core = core.substring("STRONG_".length());
        } else if (isLong) {
            core = core.substring("LONG_".length());
        }
        String pretty = formatWords(core);
        if (isStrong) {
            return pretty + " II";
        }
        if (isLong) {
            return pretty + " (+)";
        }
        return pretty;
    }

    private String formatWords(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private String toRoman(int num) {
        return switch (num) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }

    public record PopResult(ItemStack thrown, ItemStack nextUp, int remaining) {
    }
}
