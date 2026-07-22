package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.menu.MenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MainMenuListener implements Listener {

    private final InfinityTower plugin;

    public MainMenuListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        if (top == null) return;

        MenuManager mm = plugin.getMenuManager();
        if (mm == null) return;

        // só bloqueia e trata cliques no MAIN MENU
        if (!mm.isMainMenu(top)) return;

        // impede pegar/mover QUALQUER item do menu
        e.setCancelled(true);

        // só reage se clicou dentro do inventário de cima (menu)
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() != top) return;

        MenuManager.MainMenuHolder holder = mm.getMainMenuHolder(top);
        if (holder == null) return;

        int slot = e.getSlot();

        if (slot == holder.getSoloSlot()) {
            mm.openSoloMenu(p);
            return;
        }

        if (slot == holder.getPartySlot()) {
            mm.openPartyMenu(p);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top == null) return;

        MenuManager mm = plugin.getMenuManager();
        if (mm == null) return;

        if (!mm.isMainMenu(top)) return;

        // se o drag afetar slots do inventário de cima, cancela
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }
}