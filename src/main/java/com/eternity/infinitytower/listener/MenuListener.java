package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.menu.MenuManager;
import com.eternity.infinitytower.party.Party;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Locale;
import java.util.Map;

public final class MenuListener implements Listener {

    private final InfinityTower plugin;

    public MenuListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Só nosso menu
        if (!plugin.getMenuManager().isTowerMenu(event.getInventory())) return;

        event.setCancelled(true);

        MenuManager.TowerMenuHolder holder = plugin.getMenuManager().getHolder(event.getInventory());
        if (holder == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        // Botão sair
        if (holder.isLeaveSlot(slot)) {
            player.closeInventory();
            plugin.getInfinityTowerManager().leave(player);
            return;
        }

        String dungeonId = holder.getDungeonIdAt(slot);
        if (dungeonId == null) return;

        FileConfiguration dungeon = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendMessage(apply(
                    lang("messages.dungeon_not_found", "&cDungeon não encontrada: &f{dungeon}"),
                    Map.of("{dungeon}", dungeonId)
            ));
            player.closeInventory();
            return;
        }

        String mode = dungeon.getString("mode", "SOLO").toUpperCase(Locale.ROOT);

        // Se já está em sessão, não deixa
        if (plugin.getInfinityTowerManager().getSessionManager().isInSession(player)) {
            player.sendMessage(lang("messages.already_in_dungeon", "&cVocê já está em uma dungeon."));
            player.closeInventory();
            return;
        }

        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        boolean hasParty = (party != null && party.size() > 1);

        // =========================
        // MENU SOLO
        // =========================
        if (holder.getType() == MenuManager.MenuType.SOLO) {

            // party não entra em SOLO
            if (hasParty) {
                player.sendMessage(lang("messages.solo_requires_no_party",
                        "&cVocê está em uma party. Saia da party para entrar em torres SOLO."));
                player.closeInventory();
                return;
            }

            // blindagem: menu solo só inicia dungeon SOLO
            if (!mode.equals("SOLO")) {
                player.sendMessage(lang("messages.dungeon_not_solo", "&cEssa dungeon não é SOLO."));
                player.closeInventory();
                return;
            }

            // ACCESS: key + limiter + consume
            if (!checkAccessAndConsumeIfNeeded(player, dungeonId)) {
                player.closeInventory();
                return;
            }

            player.closeInventory();
            plugin.getInfinityTowerManager().startSolo(player, dungeonId);
            return;
        }

        // =========================
        // MENU PARTY
        // =========================

        // menu party só deve abrir dungeon PARTY
        if (!mode.equals("PARTY")) {
            player.sendMessage(lang("messages.dungeon_not_party", "&cEssa dungeon não é PARTY."));
            player.closeInventory();
            return;
        }

        // Se estiver em party:
        // - só líder inicia
        if (hasParty) {

            if (!party.isLeader(player.getUniqueId())) {
                player.sendMessage(lang("messages.party_only_leader_start_menu",
                        "&cApenas o líder da party pode iniciar pelo menu."));
                player.closeInventory();
                return;
            }

            // ACCESS (do líder)
            if (!checkAccessAndConsumeIfNeeded(player, dungeonId)) {
                player.closeInventory();
                return;
            }

            player.closeInventory();

            var session = plugin.getInfinityTowerManager()
                    .getSessionManager()
                    .createPartySession(party, dungeonId);

            if (session == null) {
                player.sendMessage(lang("messages.party_start_failed",
                        "&cNão foi possível iniciar a dungeon em party."));
            }

            return;
        }

        // ✅ Sem party: entra SOLO numa dungeon PARTY
        if (!checkAccessAndConsumeIfNeeded(player, dungeonId)) {
            player.closeInventory();
            return;
        }

        player.closeInventory();
        plugin.getInfinityTowerManager().startSolo(player, dungeonId);
    }

    private boolean checkAccessAndConsumeIfNeeded(Player player, String dungeonId) {

        boolean requireKey = plugin.getKeyManager().requiresKey(dungeonId);

        if (requireKey && !plugin.getKeyManager().hasKey(player, dungeonId)) {
            player.sendMessage(lang("messages.need_key_to_enter",
                    "&cVocê precisa da chave para entrar nessa torre."));
            return false;
        }

        if (plugin.getAccessLimiter().isEnabled(dungeonId)) {
            String result = plugin.getAccessLimiter().checkAndMark(player.getUniqueId(), dungeonId);
            if (result != null) {
                if (result.startsWith("cooldown:")) {
                    String left = result.substring("cooldown:".length());
                    player.sendMessage(apply(
                            lang("messages.enter_cooldown", "&cAguarde &f{seconds}s &cpara entrar novamente."),
                            Map.of("{seconds}", left)
                    ));
                } else {
                    player.sendMessage(lang("messages.enter_daily_limit",
                            "&cVocê atingiu o limite diário dessa torre."));
                }
                return false;
            }
        }

        if (requireKey && plugin.getKeyManager().consumeKeyOnEnter(dungeonId)) {
            if (!plugin.getKeyManager().consumeOneKey(player, dungeonId)) {
                player.sendMessage(lang("messages.need_key_to_enter",
                        "&cVocê precisa da chave para entrar nessa torre."));
                return false;
            }
        }

        return true;
    }

    private String lang(String path, String def) {
        return TextUtil.color(plugin.getLang().getString(path, def));
    }

    private String apply(String msg, Map<String, String> vars) {
        if (msg == null) return "";
        String out = msg;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return TextUtil.color(out);
    }
}