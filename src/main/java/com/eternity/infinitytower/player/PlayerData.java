package com.eternity.infinitytower.player;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private int maxFloor;
    private int keys;

    public PlayerData(UUID uuid, int maxFloor, int keys) {
        this.uuid = uuid;
        this.maxFloor = maxFloor;
        this.keys = keys;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public void setMaxFloor(int maxFloor) {
        this.maxFloor = maxFloor;
    }

    public int getKeys() {
        return keys;
    }

    public void setKeys(int keys) {
        this.keys = keys;
    }

    public void addKey(int amount) {
        this.keys += amount;
    }
}
