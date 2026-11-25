package com.clericsbag.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BagGuiHolder implements InventoryHolder {

    private final String bagUid;
    private Inventory inventory;

    public BagGuiHolder(String bagUid) {
        this.bagUid = bagUid;
    }

    public String getBagUid() {
        return bagUid;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
