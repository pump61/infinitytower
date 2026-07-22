package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.menu.MenuManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class StatsMenuListener implements Listener {

    private final InfinityTower plugin;

    public StatsMenuListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top == null) return;

        MenuManager mm = plugin.getMenuManager();
        if (mm == null) return;

        if (!mm.isStatsMenu(top)) return;

        // bloqueia tudo (ninguém pega item)
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top == null) return;

        MenuManager mm = plugin.getMenuManager();
        if (mm == null) return;

        if (!mm.isStatsMenu(top)) return;

        // se o drag afetar slots do inventário de cima, cancela
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }
}