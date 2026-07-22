package com.eternity.infinitytower.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MainMenuHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
