package com.eternity.infinitytower.party;

import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {

    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    public Party(UUID leader) {
        this.leader = leader;
        if (leader != null) {
            members.add(leader);
        }
    }

    public UUID getLeader() {
        return leader;
    }

    public boolean isLeader(UUID uuid) {
        if (leader == null || uuid == null) return false;
        return leader.equals(uuid);
    }

    public String getLeaderName() {
        if (leader == null) return "Desconhecido";
        var off = Bukkit.getOfflinePlayer(leader);
        return off.getName() == null ? leader.toString() : off.getName();
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean contains(UUID uuid) {
        if (uuid == null) return false;
        return members.contains(uuid);
    }

    public int size() {
        return members.size();
    }

    public boolean add(UUID uuid) {
        if (uuid == null) return false;

        boolean added = members.add(uuid);

        // se não existir líder, define automaticamente
        if (leader == null) {
            leader = uuid;
        }

        return added;
    }

    public boolean remove(UUID uuid) {
        if (uuid == null) return false;

        boolean removed = members.remove(uuid);

        // se removeu o líder, promove outro automaticamente
        if (removed && uuid.equals(leader)) {
            if (members.isEmpty()) {
                leader = null;
            } else {
                leader = members.iterator().next();
            }
        }

        return removed;
    }

    public void setLeader(UUID newLeader) {
        if (newLeader == null) return;

        leader = newLeader;
        members.add(newLeader);
    }
}