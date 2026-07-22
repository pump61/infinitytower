package com.eternity.infinitytower.setup;

public final class SetupSession {

    private String dungeonId;
    private int arenaIndex;

    public SetupSession(String dungeonId, int arenaIndex) {
        this.dungeonId = (dungeonId == null ? "" : dungeonId);
        this.arenaIndex = Math.max(1, arenaIndex);
    }

    public String dungeonId() {
        return dungeonId;
    }

    public int arenaIndex() {
        return arenaIndex;
    }

    public void setArenaIndex(int arenaIndex) {
        this.arenaIndex = Math.max(1, arenaIndex);
    }

    // ✅ necessário pro SetupManager
    public void setDungeonId(String dungeonId) {
        this.dungeonId = (dungeonId == null ? "" : dungeonId);
    }
}