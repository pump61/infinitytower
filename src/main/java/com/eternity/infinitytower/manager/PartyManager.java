package com.eternity.infinitytower.manager;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.party.Party;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public final class PartyManager {

    private final InfinityTower plugin;

    // player -> party
    private final Map<UUID, Party> partyByPlayer = new HashMap<>();

    // invited -> invite info
    private final Map<UUID, Invite> invites = new HashMap<>();

    private final int maxSize = 4;

    private static final class Invite {
        final UUID inviterLeader;
        final String inviterNameSnapshot;
        final String targetNameSnapshot;
        final long expiresAtMillis;
        final int taskId;

        Invite(UUID inviterLeader, String inviterNameSnapshot, String targetNameSnapshot, long expiresAtMillis, int taskId) {
            this.inviterLeader = inviterLeader;
            this.inviterNameSnapshot = inviterNameSnapshot;
            this.targetNameSnapshot = targetNameSnapshot;
            this.expiresAtMillis = expiresAtMillis;
            this.taskId = taskId;
        }
    }

    public PartyManager(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public Party getParty(UUID player) {
        return partyByPlayer.get(player);
    }

    // =========================
    // DISCONNECT / FORCE REMOVE
    // =========================

    /**
     * Remove o player da party por desconexão.
     * - líder: disband
     * - membro: remove; se sobrar 1 => disband
     */
    public void handleDisconnect(UUID uuid) {
        if (uuid == null) return;

        Party party = partyByPlayer.get(uuid);

        // sempre limpa convite do cara
        clearInvite(uuid);

        if (party == null) return;

        boolean isLeader = party.isLeader(uuid);

        if (isLeader) {
            disbandParty(party);
            return;
        }

        // remove membro
        party.remove(uuid);
        partyByPlayer.remove(uuid);

        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) name = "Desconhecido";

        // se ficou só 1 (ou 0) dissolve
        if (party.size() <= 1) {
            disbandParty(party);
            return;
        }

        broadcastParty(party, "party_player_left", Map.of(
                "player", name,
                "size", String.valueOf(party.size()),
                "max", String.valueOf(maxSize)
        ));
    }

    public void leaveById(UUID playerId) {
        handleDisconnect(playerId);
    }

    /**
     * Força disband se o líder desconectou.
     * Se não for líder, cai no handleDisconnect padrão.
     */
    public void disbandByLeader(UUID leaderId) {
        if (leaderId == null) return;

        Party party = partyByPlayer.get(leaderId);
        clearInvite(leaderId);

        if (party == null) return;

        if (!party.isLeader(leaderId)) {
            handleDisconnect(leaderId);
            return;
        }

        disbandParty(party);
    }

    public void disbandParty(Party party) {
        if (party == null) return;

        // avisa antes (mantém membros no objeto)
        broadcastParty(party, "party_disbanded");

        // remove mappings e convites
        removeMappings(party);

        // limpa membros do objeto (opcional, mas evita reuso acidental)
        try {
            for (UUID m : new ArrayList<>(party.getMembers())) {
                party.remove(m);
            }
        } catch (Throwable ignored) {}
    }

    private void removeMappings(Party party) {
        for (UUID m : new ArrayList<>(party.getMembers())) {
            partyByPlayer.remove(m);
            clearInvite(m);
        }
    }

    // =========================
    // INVITE
    // =========================

    public void invite(Player inviter, Player target) {
        if (inviter == null || target == null) return;

        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            inviter.sendMessage(msg("party_cannot_invite_self"));
            return;
        }

        UUID inviterId = inviter.getUniqueId();
        UUID targetId = target.getUniqueId();

        Party party = partyByPlayer.get(inviterId);

        // cria party se não existe
        if (party == null) {
            party = new Party(inviterId);

            // ✅ garante mapeamento do líder também
            partyByPlayer.put(inviterId, party);
        } else {
            // ✅ garante que o líder está mapeado (segurança)
            partyByPlayer.put(inviterId, party);
        }

        if (!party.isLeader(inviterId)) {
            inviter.sendMessage(msg("party_only_leader_invite"));
            return;
        }

        if (party.size() >= maxSize) {
            inviter.sendMessage(msg("party_full", Map.of("max", String.valueOf(maxSize))));
            return;
        }

        if (partyByPlayer.containsKey(targetId)) {
            inviter.sendMessage(msg("party_target_already_in_party"));
            return;
        }

        clearInvite(targetId);

        int expireSeconds = plugin.getConfig().getInt("party.invite_expire_seconds", 20);
        if (expireSeconds < 20) expireSeconds = 20;

        long expiresAt = System.currentTimeMillis() + (expireSeconds * 1000L);

        final String inviterNameSnap = inviter.getName();
        final String targetNameSnap = target.getName();

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Invite current = invites.get(targetId);
            if (current == null) return;

            // só expira se ainda for o mesmo líder que convidou
            if (!current.inviterLeader.equals(inviterId)) return;

            invites.remove(targetId);

            Player inv = Bukkit.getPlayer(inviterId);
            Player tar = Bukkit.getPlayer(targetId);

            if (partyByPlayer.containsKey(targetId)) return;

            if (inv != null && inv.isOnline()) {
                inv.sendMessage(msg("party_invite_expired_inviter", Map.of("player", current.targetNameSnapshot)));
            }
            if (tar != null && tar.isOnline()) {
                tar.sendMessage(msg("party_invite_expired_target", Map.of("player", current.inviterNameSnapshot)));
            }

        }, expireSeconds * 20L).getTaskId();

        invites.put(targetId, new Invite(inviterId, inviterNameSnap, targetNameSnap, expiresAt, taskId));

        inviter.sendMessage(msg("party_invite_sent", Map.of("player", target.getName())));
        target.sendMessage(msg("party_invite_received", Map.of("player", inviter.getName())));
        target.sendMessage(msg("party_invite_use_accept"));
    }

    public void accept(Player target) {
        if (target == null) return;

        UUID targetId = target.getUniqueId();
        Invite invite = invites.get(targetId);

        if (invite == null) {
            target.sendMessage(msg("party_no_pending_invite"));
            return;
        }

        if (System.currentTimeMillis() > invite.expiresAtMillis) {
            clearInvite(targetId);
            target.sendMessage(msg("party_invite_expired"));
            return;
        }

        Player inviter = Bukkit.getPlayer(invite.inviterLeader);
        if (inviter == null || !inviter.isOnline()) {
            clearInvite(targetId);
            target.sendMessage(msg("party_leader_offline"));
            return;
        }

        UUID inviterId = inviter.getUniqueId();

        Party party = partyByPlayer.get(inviterId);
        if (party == null) {
            party = new Party(inviterId);
            partyByPlayer.put(inviterId, party);
        } else {
            // ✅ garante mapeamento do líder
            partyByPlayer.put(inviterId, party);
        }

        if (!party.isLeader(inviterId)) {
            clearInvite(targetId);
            target.sendMessage(msg("party_not_party_leader"));
            return;
        }

        if (party.size() >= maxSize) {
            clearInvite(targetId);
            target.sendMessage(msg("party_full", Map.of("max", String.valueOf(maxSize))));
            return;
        }

        if (partyByPlayer.containsKey(targetId)) {
            clearInvite(targetId);
            target.sendMessage(msg("party_already_in_party"));
            return;
        }

        clearInvite(targetId);

        party.add(targetId);
        partyByPlayer.put(targetId, party);

        broadcastParty(party, "party_player_joined", Map.of(
                "player", target.getName(),
                "size", String.valueOf(party.size()),
                "max", String.valueOf(maxSize)
        ));
    }

    // =========================
    // LEAVE / KICK / LEADER
    // =========================

    public void leave(Player player) {
        if (player == null) return;

        Party party = partyByPlayer.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(msg("party_not_in_party"));
            return;
        }

        UUID id = player.getUniqueId();
        boolean wasLeader = party.isLeader(id);

        party.remove(id);
        partyByPlayer.remove(id);
        clearInvite(id);

        // ✅ confirma pro próprio jogador que ele saiu — sem isso, quem saía não recebia nenhuma mensagem
        player.sendMessage(msg("party_you_left"));

        if (party.size() <= 1) {
            disbandParty(party);
            return;
        }

        if (wasLeader) {
            UUID newLeader = party.getMembers().iterator().next();
            party.setLeader(newLeader);

            String newLeaderName = Bukkit.getOfflinePlayer(newLeader).getName();
            if (newLeaderName == null) newLeaderName = "Desconhecido";

            broadcastParty(party, "party_leader_left_new_leader", Map.of("player", newLeaderName));
        }

        broadcastParty(party, "party_player_left", Map.of(
                "player", player.getName(),
                "size", String.valueOf(party.size()),
                "max", String.valueOf(maxSize)
        ));
    }

    public void kick(Player leader, String targetName) {
        if (leader == null) return;

        Party party = partyByPlayer.get(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(msg("party_not_in_party"));
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(msg("party_only_leader_kick"));
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            leader.sendMessage(msg("party_player_offline"));
            return;
        }

        if (!party.contains(target.getUniqueId())) {
            leader.sendMessage(msg("party_player_not_in_party"));
            return;
        }

        if (party.isLeader(target.getUniqueId())) {
            leader.sendMessage(msg("party_cannot_kick_leader"));
            return;
        }

        party.remove(target.getUniqueId());
        partyByPlayer.remove(target.getUniqueId());
        clearInvite(target.getUniqueId());

        target.sendMessage(msg("party_kicked"));

        if (party.size() <= 1) {
            disbandParty(party);
            return;
        }

        broadcastParty(party, "party_player_kicked", Map.of(
                "player", target.getName(),
                "size", String.valueOf(party.size()),
                "max", String.valueOf(maxSize)
        ));
    }

    public void transferLeader(Player currentLeader, String newLeaderName) {
        if (currentLeader == null) return;

        Party party = partyByPlayer.get(currentLeader.getUniqueId());
        if (party == null) {
            currentLeader.sendMessage(msg("party_not_in_party"));
            return;
        }
        if (!party.isLeader(currentLeader.getUniqueId())) {
            currentLeader.sendMessage(msg("party_only_leader_transfer"));
            return;
        }

        Player target = Bukkit.getPlayerExact(newLeaderName);
        if (target == null || !target.isOnline()) {
            currentLeader.sendMessage(msg("party_player_offline"));
            return;
        }
        if (!party.contains(target.getUniqueId())) {
            currentLeader.sendMessage(msg("party_player_not_in_party"));
            return;
        }

        party.setLeader(target.getUniqueId());
        broadcastParty(party, "party_leadership_transferred", Map.of("player", target.getName()));
    }

    // =========================
    // CLEAR / HELPERS
    // =========================

    public void clear() {
        for (Invite inv : new ArrayList<>(invites.values())) {
            try { Bukkit.getScheduler().cancelTask(inv.taskId); } catch (Throwable ignored) {}
        }
        invites.clear();
        partyByPlayer.clear();
    }

    private void clearInvite(UUID targetId) {
        if (targetId == null) return;
        Invite inv = invites.remove(targetId);
        if (inv != null) {
            try { Bukkit.getScheduler().cancelTask(inv.taskId); } catch (Throwable ignored) {}
        }
    }

    private void broadcastParty(Party party, String key) {
        broadcastParty(party, key, Map.of());
    }

    private void broadcastParty(Party party, String key, Map<String, String> placeholders) {
        if (party == null) return;
        String colored = msg(key, placeholders);

        for (UUID id : new ArrayList<>(party.getMembers())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(colored);
        }
    }

    // =========================
    // LANG: party.<key>
    // =========================

    private String msg(String key) {
        return msg(key, Map.of());
    }

    private String msg(String key, Map<String, String> placeholders) {
        String path = "party." + key;

        String raw = plugin.getLang().getString(path, "&c(lang) " + path + " não encontrado");
        if (raw == null) raw = "&c(lang) " + path + " não encontrado";

        for (var e : placeholders.entrySet()) {
            raw = raw.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return TextUtil.color(raw);
    }
}