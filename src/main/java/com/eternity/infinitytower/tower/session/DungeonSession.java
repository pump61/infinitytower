package com.eternity.infinitytower.tower.session;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.database.PlayerStatsRepository;
import com.eternity.infinitytower.database.RunHistoryRepository;
import com.eternity.infinitytower.util.EssentialsBackUtil;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

public final class DungeonSession {

    public static final String TAG_PREFIX = "itower_session:";

    public enum EndReason { LEAVE, DISCONNECT, FINISH, ERROR, SHUTDOWN }

    private final InfinityTower plugin;
    private final UUID sessionId = UUID.randomUUID();
    private final String dungeonId;

    // ✅ arena 1 = principal (root do yml), 2..N = dungeonId_arenaX
    private final int arenaIndex;

    private final Set<UUID> players = new LinkedHashSet<>();

    private int floor = 1;
    private boolean transitioning = false;
    private boolean ended = false;

    private final Set<UUID> mobs = new HashSet<>();
    private final List<Location> floorSpawnPoints = new ArrayList<>();

    private int monitorTaskId = -1;

    // ✅ morreu nesse andar (não recebe recompensa desse andar)
    private final Set<UUID> deadThisFloor = new HashSet<>();

    // ✅ spawn “inicial” por player (leader/members/solo)
    private final Map<UUID, Location> entrySpawnByPlayer = new HashMap<>();

    // ✅ fallback (se precisar)
    private Location towerEntrySpawn = null;

    // =========================
    // RUN / DB META
    // =========================

    private long runStartedAtMs = 0L;

    private boolean partyRun = false;
    private UUID leaderUuid = null;

    public void setPartyRun(boolean partyRun, UUID leaderUuid) {
        this.partyRun = partyRun;
        this.leaderUuid = leaderUuid;
    }

    public boolean isPartyRun() {
        return partyRun;
    }

    public UUID getPartyLeader() {
        return leaderUuid;
    }

    public DungeonSession(InfinityTower plugin, String dungeonId, int arenaIndex) {
        this.plugin = plugin;
        this.dungeonId = dungeonId;
        this.arenaIndex = Math.max(1, arenaIndex);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public void addPlayer(Player player) {
        if (player == null) return;
        players.add(player.getUniqueId());
    }

    // =====================================================
    // ✅ ESSENTIALS /BACK PROTECTION
    // =====================================================

    private void protectBackAfterTeleport(Player p, Location safe) {
        if (p == null || safe == null) return;

        // ✅ bloqueia /back (e variações) por alguns segundos, independente de plugin instalado
        int blockSeconds = plugin.getConfig().getInt("tower.back_block_seconds", 5);
        if (blockSeconds > 0 && plugin.getCommandWhitelistListener() != null) {
            plugin.getCommandWhitelistListener().blockBackFor(p, blockSeconds);
        }

        final UUID pid = p.getUniqueId();
        final Location safeClone = safe.clone();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(pid);
            if (online == null || !online.isOnline()) return;

            // ✅ se o EssentialsX estiver presente, também sobrescreve o /back dele
            EssentialsBackUtil.overrideBackLocation(online, safeClone);
        }, 2L);
    }

    /**
     * ✅ Remove UM jogador da sessão sem encerrar a dungeon pros outros.
     */
    public void removePlayer(Player player) {
        if (player == null) return;

        UUID pid = player.getUniqueId();
        if (!players.contains(pid)) return;

        players.remove(pid);
        deadThisFloor.remove(pid);
        entrySpawnByPlayer.remove(pid);

        if (player.isOnline()) {
            FileConfiguration d = dungeon();

            Location back = readArenaLocationSection(d, "return_spawn");
            if (back == null) back = getFallbackSpawn();

            forceAliveState(player);

            if (back != null) {
                player.teleport(back);
                protectBackAfterTeleport(player, back);
            }
        }
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public int getArenaIndex() {
        return arenaIndex;
    }

    private FileConfiguration dungeon() {
        return plugin.getDungeonRegistry().getDungeon(dungeonId);
    }

    public boolean isPlayerInThisSession(UUID playerId) {
        return players.contains(playerId);
    }

    public boolean isDeadThisFloor(UUID playerId) {
        return deadThisFloor.contains(playerId);
    }

    // =========================
    // ARENA PREFIX HELPERS
    // =========================

    private String arenaPrefix(FileConfiguration dungeonCfg) {
        if (arenaIndex <= 1) return "";
        if (dungeonCfg == null) return "";

        String key = dungeonId.toLowerCase(Locale.ROOT) + "_arena" + arenaIndex;
        return (dungeonCfg.getConfigurationSection(key) != null) ? (key + ".") : "";
    }

    // =========================
    // LANG HELPERS
    // =========================

    private String langRaw(String path, String def) {
        FileConfiguration l = plugin.getLang();
        if (l == null) return def;
        String v = l.getString(path);
        return (v == null) ? def : v;
    }

    private String msg(String key, String def) {
        return TextUtil.color(langRaw("messages." + key, def));
    }

    private String msg(String key, String def, Map<String, String> vars) {
        String s = langRaw("messages." + key, def);
        if (vars != null) {
            for (var e : vars.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return TextUtil.color(s);
    }

    private String log(String key, String def) {
        return langRaw("logs." + key, def);
    }

    private String log(String key, String def, Map<String, String> vars) {
        String s = langRaw("logs." + key, def);
        if (vars != null) {
            for (var e : vars.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return s;
    }

    private String titleDefault(String path, String def) {
        return langRaw("titles." + path, def);
    }

    private String subtitleDefault(String path, String def) {
        return langRaw("titles." + path, def);
    }

    // =========================
    // ✅ BOSS FLOOR SYSTEM
    // =========================

    private boolean isBossFloor(FileConfiguration d, int floorNumber) {
        if (d == null) return false;
        return d.getBoolean("floors." + floorNumber + ".boss", false);
    }

    private String bossName(FileConfiguration d, int floorNumber) {
        if (d == null) return "";
        String v = d.getString("floors." + floorNumber + ".boss_name", "");
        return v == null ? "" : v;
    }

    /**
     * Busca texto na ordem:
     * 1) floors.<floor>.titles.<group>.<field>
     * 2) titles.<group>.<field>
     * 3) lang fallback (titles.<fallbackLangPath>)
     */
    private String dungeonTitle(FileConfiguration d, int floorNumber, String group, String field, String fallbackLangPath, String def) {
        String v = d.getString("floors." + floorNumber + ".titles." + group + "." + field);
        if (v != null && !v.isBlank()) return v;

        v = d.getString("titles." + group + "." + field);
        if (v != null && !v.isBlank()) return v;

        return titleDefault(fallbackLangPath, def);
    }

    private String applyExtraPlaceholders(String text, FileConfiguration d, int floorNumber) {
        if (text == null) return "";
        String out = text;
        String bn = bossName(d, floorNumber);
        if (bn == null) bn = "";
        out = out.replace("{boss_name}", bn);
        return out;
    }

    private void announceFloorStart(FileConfiguration d, int floorNumber) {
        boolean boss = isBossFloor(d, floorNumber);

        String group = boss ? "boss_floor_started" : "floor_started";

        String titleRaw = dungeonTitle(
                d, floorNumber,
                group, "title",
                boss ? "boss_floor_started.title" : "floor_started.title",
                boss ? "&4&lBOSS!" : "&eAndar {floor}"
        );

        String subtitleRaw = dungeonTitle(
                d, floorNumber,
                group, "subtitle",
                boss ? "boss_floor_started.subtitle" : "floor_started.subtitle",
                boss ? "&cPrepare-se para o boss!" : "&7Derrote todos os mobs."
        );

        titleRaw = applyExtraPlaceholders(titleRaw, d, floorNumber);
        subtitleRaw = applyExtraPlaceholders(subtitleRaw, d, floorNumber);

        titleRaw = TextUtil.applyPlaceholders(titleRaw, floorNumber, 0);
        subtitleRaw = TextUtil.applyPlaceholders(subtitleRaw, floorNumber, 0);

        sendTitleToAll(titleRaw, subtitleRaw);
    }

    private void announceNextFloorHint(FileConfiguration d, int currentFloor, int nextFloor, int delaySeconds) {
        boolean nextIsBoss = isBossFloor(d, nextFloor);

        String group = nextIsBoss ? "next_is_boss" : "floor_cleared";

        // ✅ pega do PRÓXIMO ANDAR (pra você configurar o anúncio do boss que vem)
        int sourceFloorForTexts = nextIsBoss ? nextFloor : currentFloor;

        String titleRaw = dungeonTitle(
                d, sourceFloorForTexts,
                group, "title",
                nextIsBoss ? "next_is_boss.title" : "floor_cleared.title",
                nextIsBoss ? "&c⚠ Próximo andar: &4&lBOSS" : "Andar {floor} Concluído!"
        );

        String subtitleRaw = dungeonTitle(
                d, sourceFloorForTexts,
                group, "subtitle",
                nextIsBoss ? "next_is_boss.subtitle" : "floor_cleared.subtitle",
                nextIsBoss ? "&7O boss começa em {delay}s!" : "Iniciando próximo andar em {delay}s!"
        );

        titleRaw = applyExtraPlaceholders(titleRaw, d, nextFloor);
        subtitleRaw = applyExtraPlaceholders(subtitleRaw, d, nextFloor);

        titleRaw = TextUtil.applyPlaceholders(titleRaw, currentFloor, delaySeconds);
        subtitleRaw = TextUtil.applyPlaceholders(subtitleRaw, currentFloor, delaySeconds);

        sendTitleToAll(titleRaw, subtitleRaw);
    }

    // =========================
    // FAIL CONDITION (ALL DEAD)
    // =========================

    private boolean allOnlinePlayersDeadThisFloor() {
        boolean hasAnyOnline = false;

        for (UUID pid : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(pid);
            if (p == null || !p.isOnline()) continue;

            hasAnyOnline = true;
            if (!deadThisFloor.contains(pid)) return false;
        }

        return hasAnyOnline;
    }

    private void failDungeonAllDead() {
        if (ended) return;
        broadcast(msg("dungeon_all_dead", "&cTodos morreram. A dungeon falhou!"));
        end(EndReason.ERROR);
    }

    // =========================
    // PLAYER LIFE (DEATH/RESPAWN)
    // =========================

    public void handlePlayerDeath(Player player) {
        if (ended) return;
        if (player == null) return;
        if (!players.contains(player.getUniqueId())) return;

        deadThisFloor.add(player.getUniqueId());

        if (allOnlinePlayersDeadThisFloor()) {
            Bukkit.getScheduler().runTask(plugin, this::failDungeonAllDead);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ended) return;
            if (!player.isOnline()) return;

            if (player.isDead()) {
                forceRespawnNow(player);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ended) return;
                if (!player.isOnline()) return;

                forceSpectatorState(player);

                Location spec = getSpectatorLocation();
                if (spec != null) player.teleport(spec);
            });

        }, 2L);
    }

    public void handlePlayerRespawn(PlayerRespawnEvent event, Player player) {
        if (ended) return;
        if (event == null || player == null) return;
        if (!players.contains(player.getUniqueId())) return;

        if (deadThisFloor.contains(player.getUniqueId())) {
            Location spec = getSpectatorLocation();
            if (spec != null) event.setRespawnLocation(spec);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ended) return;
                if (!player.isOnline()) return;
                forceSpectatorState(player);
                Location s = getSpectatorLocation();
                if (s != null) player.teleport(s);
            });
            return;
        }

        Location personalEntry = entrySpawnByPlayer.get(player.getUniqueId());
        Location to = (personalEntry != null) ? personalEntry : (towerEntrySpawn != null ? towerEntrySpawn : getFallbackSpawn());
        if (to != null) event.setRespawnLocation(to);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ended) return;
            if (!player.isOnline()) return;
            forceAliveState(player);
            if (to != null) player.teleport(to);
        });
    }

    private void forceSpectatorState(Player p) {
        try {
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        } catch (Throwable ignored) {}
        p.setInvisible(false);
        p.setCollidable(false);
        p.setAllowFlight(true);
        p.setFlying(true);
        if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
    }

    private void forceAliveState(Player p) {
        try {
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        } catch (Throwable ignored) {}
        p.setInvisible(false);
        p.setCollidable(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        if (p.getGameMode() != GameMode.SURVIVAL) p.setGameMode(GameMode.SURVIVAL);
    }

    private Location getSpectatorLocation() {
        for (UUID pid : new ArrayList<>(players)) {
            if (deadThisFloor.contains(pid)) continue;
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline()) return p.getLocation().clone().add(0, 2, 0);
        }

        for (UUID pid : new ArrayList<>(players)) {
            if (deadThisFloor.contains(pid)) continue;
            Location loc = entrySpawnByPlayer.get(pid);
            if (loc != null) return loc;
        }

        if (towerEntrySpawn != null) return towerEntrySpawn;

        FileConfiguration d = dungeon();
        Location back = readArenaLocationSection(d, "return_spawn");
        if (back == null) back = getFallbackSpawn();
        return back;
    }

    private void forceRespawnNow(Player p) {
        try {
            Object spigot = p.spigot();
            Method respawn = spigot.getClass().getMethod("respawn");
            respawn.invoke(spigot);
        } catch (Throwable ignored) {}
    }

    // =========================
    // START / FLOORS
    // =========================

    public void start() {
        final FileConfiguration dungeonCfg = dungeon();
        if (dungeonCfg == null) {
            broadcast(msg("dungeon_not_found", "&cDungeon não encontrada: &f{dungeon}", Map.of("dungeon", dungeonId)));
            end(EndReason.ERROR);
            return;
        }

        int tpDelayTmp = plugin.getConfig().getInt("tower.enter_teleport_delay_seconds", 2);
        if (tpDelayTmp < 0) tpDelayTmp = 0;
        final int tpDelay = tpDelayTmp;

        int startDelayTmp = dungeonCfg.getInt("start_delay_seconds", 10);
        if (startDelayTmp < 0) startDelayTmp = 0;
        final int startDelay = startDelayTmp;

        sendDungeonTitleToAll(dungeonCfg, "titles.on_enter.title", "titles.on_enter.subtitle", 1, startDelay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ended) return;

            entrySpawnByPlayer.clear();

            Location fallbackEntry = pickRandomPlayerSpawn(dungeonCfg);
            if (fallbackEntry == null) fallbackEntry = getFallbackSpawn();
            if (fallbackEntry != null) this.towerEntrySpawn = fallbackEntry.clone();

            forEachOnline(p -> {
                Location entry = pickEntryForPlayer(dungeonCfg, p);
                if (entry == null) entry = (towerEntrySpawn != null ? towerEntrySpawn : getFallbackSpawn());
                if (entry != null) entrySpawnByPlayer.put(p.getUniqueId(), entry.clone());

                forceAliveState(p);
                if (entry != null) p.teleport(entry);
            });

            if (runStartedAtMs == 0L) runStartedAtMs = System.currentTimeMillis();

            String prepTitle = dungeonCfg.getString("titles.preparing.title", "");
            String prepSubtitle = dungeonCfg.getString("titles.preparing.subtitle", "");

            if (prepTitle != null && !prepTitle.isBlank()) {
                prepTitle = TextUtil.applyPlaceholders(prepTitle, 1, startDelay);
                prepSubtitle = TextUtil.applyPlaceholders(prepSubtitle, 1, startDelay);
                sendTitleToAll(prepTitle, prepSubtitle);
            }

            Bukkit.getScheduler().runTaskLater(plugin, this::startFloor, startDelay * 20L);

        }, tpDelay * 20L);
    }

    private void startFloor() {
        if (ended) return;
        transitioning = false;

        FileConfiguration d = dungeon();
        if (d == null) {
            end(EndReason.ERROR);
            return;
        }

        int maxFloors = d.getInt("max_floors", 10);
        if (floor > maxFloors) {
            finishDungeon(d);
            return;
        }

        boolean allowEmpty = d.getBoolean("allow_empty_floors", false);

        stopFloorMonitor();
        clearTrackedMobs();
        mobs.clear();
        floorSpawnPoints.clear();

        deadThisFloor.clear();

        forEachOnline(p -> {
            Location entry = entrySpawnByPlayer.get(p.getUniqueId());
            if (entry == null) entry = (towerEntrySpawn != null ? towerEntrySpawn : getFallbackSpawn());

            forceAliveState(p);
            if (entry != null) p.teleport(entry);
        });

        ConfigurationSection floorSec = d.getConfigurationSection("floors." + floor);
        if (floorSec == null) {
            if (!allowEmpty) {
                broadcast(msg("floor_not_configured",
                        "&cEste andar ainda não está configurado (&f{floor}&c). Dungeon encerrada.",
                        Map.of("floor", String.valueOf(floor))));
                end(EndReason.ERROR);
            } else {
                onFloorCleared(d);
            }
            return;
        }

        int expected = countExpectedMobs(floorSec);
        int trackedNow = spawnFloorMobs(floorSec);

        startFloorMonitor();

        if (!allowEmpty && expected > 0 && trackedNow == 0) {
            broadcast(msg("floor_spawn_failed",
                    "&cFalha ao spawnar mobs deste andar (&f{floor}&c). Dungeon encerrada.",
                    Map.of("floor", String.valueOf(floor))));
            end(EndReason.ERROR);
            return;
        }

        if (expected == 0) {
            if (!allowEmpty) {
                broadcast(msg("floor_no_mobs",
                        "&cAndar configurado sem mobs (&f{floor}&c). Dungeon encerrada.",
                        Map.of("floor", String.valueOf(floor))));
                end(EndReason.ERROR);
            } else {
                onFloorCleared(d);
            }
            return;
        }

        // ✅ NOVO: anúncio de início do andar (normal ou boss)
        announceFloorStart(d, floor);
    }

    private int countExpectedMobs(ConfigurationSection floorSec) {
        int expected = 0;
        List<Map<?, ?>> mobList = floorSec.getMapList("mobs");
        for (Map<?, ?> mobEntry : mobList) {
            int amount = 1;
            if (mobEntry.get("amount") instanceof Number n) amount = n.intValue();
            if (amount < 0) amount = 0;
            expected += amount;
        }
        return expected;
    }

    // =========================
    // FLOOR MONITOR
    // =========================

    private void startFloorMonitor() {
        stopFloorMonitor();

        monitorTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (ended || transitioning) return;

            if (allOnlinePlayersDeadThisFloor()) {
                failDungeonAllDead();
                return;
            }

            pruneMobsSet();

            if (mobs.isEmpty()) {
                FileConfiguration d = dungeon();
                if (d != null) onFloorCleared(d);
            }
        }, 20L, 20L);
    }

    private void stopFloorMonitor() {
        if (monitorTaskId != -1) {
            Bukkit.getScheduler().cancelTask(monitorTaskId);
            monitorTaskId = -1;
        }
    }

    private void pruneMobsSet() {
        mobs.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return (e == null || e.isDead() || !(e instanceof LivingEntity));
        });
    }

    // =========================
    // SPAWN MOBS (MYTHIC)
    // =========================

    private int spawnFloorMobs(ConfigurationSection floorSec) {
        int tracked = 0;

        FileConfiguration d = dungeon();
        if (d == null) return 0;

        List<Map<?, ?>> mobListBase = floorSec.getMapList("mobs");
        List<Map<?, ?>> mobListArena = getArenaMobOverrides(d, floor);

        for (int idx = 0; idx < mobListBase.size(); idx++) {

            Map<?, ?> mobEntry = mobListBase.get(idx);

            String vanillaType = mobEntry.get("type") != null ? String.valueOf(mobEntry.get("type")) : null;
            String mythicMob = mobEntry.get("mythic") != null ? String.valueOf(mobEntry.get("mythic")) : null;

            int amount = 1;
            if (mobEntry.get("amount") instanceof Number n) amount = n.intValue();
            if (amount < 0) amount = 0;

            Object spawnsObj = mobEntry.get("spawns");

            if (mobListArena != null && idx < mobListArena.size()) {
                Map<?, ?> arenaEntry = mobListArena.get(idx);
                if (arenaEntry != null && arenaEntry.get("spawns") instanceof List<?>) {
                    spawnsObj = arenaEntry.get("spawns");
                }
            }

            List<Location> spawns = new ArrayList<>();
            if (spawnsObj instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> map) {
                        Location loc = readLocation(map);
                        if (loc != null) spawns.add(loc);
                    }
                }
            }

            if (spawns.isEmpty()) {
                Player any = firstOnlinePlayer();
                if (any != null) spawns.add(any.getLocation());
            }

            if (spawns.isEmpty()) continue;

            floorSpawnPoints.addAll(spawns);

            for (int i = 0; i < amount; i++) {
                Location loc = spawns.get(i % spawns.size());

                if (mythicMob == null || mythicMob.isBlank()) {
                    if (vanillaType == null || vanillaType.isBlank()) continue;

                    LivingEntity spawned = spawnVanillaMob(vanillaType, loc);
                    if (spawned != null) {
                        applyVanillaEquipment(spawned, mobEntry, vanillaType);
                        trackMob(spawned);
                        tracked++;
                    }
                    continue;
                }

                UUID uuid = spawnMythicMobUUID(mythicMob, loc);
                if (uuid == null) continue;

                mobs.add(uuid);

                if (!tryTagMythicNow(uuid)) {
                    scheduleTagRetry(uuid);
                } else {
                    tracked++;
                }
            }
        }

        return tracked;
    }

    private List<Map<?, ?>> getArenaMobOverrides(FileConfiguration d, int floorNumber) {
        if (arenaIndex <= 1) return null;
        if (d == null) return null;

        String key = dungeonId.toLowerCase(Locale.ROOT) + "_arena" + arenaIndex;
        String path = key + ".floors." + floorNumber + ".mobs";

        List<Map<?, ?>> list = d.getMapList(path);
        return (list == null || list.isEmpty()) ? null : list;
    }

    private LivingEntity spawnVanillaMob(String typeName, Location loc) {
        try {
            EntityType type = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
            Entity e = loc.getWorld().spawnEntity(loc, type);
            if (e instanceof LivingEntity le) return le;
            return null;
        } catch (Exception ex) {
            plugin.getLogger().warning(log(
                    "dungeon.vanilla_mob_invalid",
                    "Mob vanilla inválido: {mob} (dungeon {dungeon}, floor {floor})",
                    Map.of(
                            "mob", String.valueOf(typeName),
                            "dungeon", String.valueOf(dungeonId),
                            "floor", String.valueOf(floor)
                    )
            ));
            return null;
        }
    }

    // =========================
    // EQUIPAMENTO (MOBS VANILLA)
    // =========================

    private void applyVanillaEquipment(LivingEntity entity, Map<?, ?> mobEntry, String vanillaType) {
        Object equipObj = mobEntry.get("equipment");
        if (!(equipObj instanceof Map<?, ?> equipMap)) return;

        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;

        setEquipmentSlot(eq, equipMap, "hand", vanillaType, eq::setItemInMainHand);
        setEquipmentSlot(eq, equipMap, "offhand", vanillaType, eq::setItemInOffHand);
        setEquipmentSlot(eq, equipMap, "helmet", vanillaType, eq::setHelmet);
        setEquipmentSlot(eq, equipMap, "chestplate", vanillaType, eq::setChestplate);
        setEquipmentSlot(eq, equipMap, "leggings", vanillaType, eq::setLeggings);
        setEquipmentSlot(eq, equipMap, "boots", vanillaType, eq::setBoots);

        // não dropa o equipamento configurado quando o mob morre
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
    }

    private void setEquipmentSlot(EntityEquipment eq, Map<?, ?> equipMap, String slot, String vanillaType, Consumer<ItemStack> setter) {
        Object raw = equipMap.get(slot);
        if (raw == null) return;

        // aceita tanto "slot: MATERIAL" quanto "slot: { material: MATERIAL, enchantments: {...} }"
        String materialName;
        Map<?, ?> enchantments = null;

        if (raw instanceof Map<?, ?> slotMap) {
            Object matObj = slotMap.get("material");
            materialName = matObj == null ? null : String.valueOf(matObj);

            Object enchObj = slotMap.get("enchantments");
            if (enchObj instanceof Map<?, ?> em) enchantments = em;
        } else {
            materialName = String.valueOf(raw);
        }

        if (materialName == null || materialName.isBlank()) return;

        try {
            Material mat = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
            ItemStack stack = new ItemStack(mat);
            applyEnchantments(stack, enchantments, slot, vanillaType);
            setter.accept(stack);
        } catch (Exception ex) {
            plugin.getLogger().warning(log(
                    "dungeon.equipment_invalid_item",
                    "Equipamento inválido: {item} (slot {slot}, mob {mob})",
                    Map.of("item", materialName, "slot", slot, "mob", String.valueOf(vanillaType))
            ));
        }
    }

    private void applyEnchantments(ItemStack stack, Map<?, ?> enchantments, String slot, String vanillaType) {
        if (enchantments == null || enchantments.isEmpty()) return;

        for (var e : enchantments.entrySet()) {
            String enchName = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);

            int level = 1;
            if (e.getValue() instanceof Number n) level = n.intValue();
            if (level < 1) level = 1;

            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchName));
            if (ench == null) {
                plugin.getLogger().warning(log(
                        "dungeon.equipment_invalid_enchantment",
                        "Encantamento inválido: {enchant} (slot {slot}, mob {mob})",
                        Map.of("enchant", enchName, "slot", slot, "mob", String.valueOf(vanillaType))
                ));
                continue;
            }

            stack.addUnsafeEnchantment(ench, level);
        }
    }

    private UUID spawnMythicMobUUID(String mythicInternalName, Location loc) {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            plugin.getLogger().warning(log(
                    "dungeon.mythic_missing",
                    "MythicMobs não instalado, mas pediu mythic: {mob}",
                    Map.of("mob", String.valueOf(mythicInternalName))
            ));
            return null;
        }

        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method inst = mythicBukkit.getMethod("inst");
            Object instObj = inst.invoke(null);

            Method getMobManager = instObj.getClass().getMethod("getMobManager");
            Object mobManager = getMobManager.invoke(instObj);

            Method spawnMob = mobManager.getClass().getMethod("spawnMob", String.class, Location.class);
            Object activeMob = spawnMob.invoke(mobManager, mythicInternalName, loc);

            try {
                Method getEntity = activeMob.getClass().getMethod("getEntity");
                Object ent = getEntity.invoke(activeMob);

                if (ent instanceof Entity bukkitEntity) return bukkitEntity.getUniqueId();

                try {
                    Method getBukkitEntity = ent.getClass().getMethod("getBukkitEntity");
                    Object bukkit = getBukkitEntity.invoke(ent);
                    if (bukkit instanceof Entity bukkitEntity) return bukkitEntity.getUniqueId();
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            return null;

        } catch (Throwable t) {
            plugin.getLogger().warning(log(
                    "dungeon.mythic_spawn_failed",
                    "Falha MythicMob: {mob} ({err})",
                    Map.of(
                            "mob", String.valueOf(mythicInternalName),
                            "err", t.getClass().getSimpleName()
                    )
            ));
            return null;
        }
    }

    private boolean tryTagMythicNow(UUID uuid) {
        Entity e = Bukkit.getEntity(uuid);
        if (!(e instanceof LivingEntity le)) return false;
        trackMob(le);
        return true;
    }

    private void scheduleTagRetry(UUID uuid) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ended) return;
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof LivingEntity le) {
                boolean has = false;
                for (String t : le.getScoreboardTags()) {
                    if (t.startsWith(TAG_PREFIX)) {
                        has = true;
                        break;
                    }
                }
                if (!has) trackMob(le);
            }
        }, 1L);
    }

    private void trackMob(LivingEntity entity) {
        entity.addScoreboardTag(TAG_PREFIX + sessionId.toString());
        mobs.add(entity.getUniqueId());
    }

    // =========================
    // EXTERNAL SPAWNS
    // =========================

    public void handleExternalMobSpawn(LivingEntity entity) {
        if (ended) return;
        if (entity instanceof Player) return;

        for (String t : entity.getScoreboardTags()) {
            if (t.startsWith(TAG_PREFIX)) return;
        }

        int radius = plugin.getConfig().getInt("tower.tracking.spawn_radius", 30);
        if (floorSpawnPoints.isEmpty()) return;

        for (Location point : floorSpawnPoints) {
            if (point == null) continue;
            if (point.getWorld() == null || entity.getWorld() == null) continue;
            if (!point.getWorld().equals(entity.getWorld())) continue;

            if (point.distanceSquared(entity.getLocation()) <= (radius * radius)) {
                trackMob(entity);
                return;
            }
        }
    }

    // =========================
    // MOB DEATH
    // =========================

    public void handleMobDeath(LivingEntity entity) {
        if (ended) return;

        mobs.remove(entity.getUniqueId());
        pruneMobsSet();

        if (mobs.isEmpty()) {
            FileConfiguration d = dungeon();
            if (d != null) onFloorCleared(d);
        }
    }

    // =========================
    // FLOOR CLEARED / NEXT
    // =========================

    private void onFloorCleared(FileConfiguration d) {
        if (ended) return;
        if (transitioning) return;
        transitioning = true;

        stopFloorMonitor();

        final int clearedFloor = this.floor;
        final int delay = getDelaySeconds(d);
        final int maxFloors = d.getInt("max_floors", 10);

        giveFloorRewards(d, clearedFloor);

        if (clearedFloor % 10 == 0) {
            String name = firstOnlinePlayerName();
            if (name != null) {
                Bukkit.broadcastMessage(
                        TextUtil.color(
                                langRaw("messages.dungeon_broadcast_floor10",
                                        "&6[InfinityTower] &f{player} &econcluiu &6{floor} &eordas!")
                                        .replace("{player}", name)
                                        .replace("{floor}", String.valueOf(clearedFloor))
                        )
                );
            }
        }

        if (clearedFloor >= maxFloors) {
            String display = d.getString("display", dungeonId);

            String titleRaw = d.getString("titles.completed.title");
            String subtitleRaw = d.getString("titles.completed.subtitle");

            if (titleRaw == null) titleRaw = titleDefault("completed.title", "Dungeon Concluída!");
            if (subtitleRaw == null) subtitleRaw = subtitleDefault("completed.subtitle", "Voltando ao spawn...");

            titleRaw = titleRaw.replace("{dungeon}", display);
            subtitleRaw = subtitleRaw.replace("{dungeon}", display);

            titleRaw = TextUtil.applyPlaceholders(titleRaw, clearedFloor, delay);
            subtitleRaw = TextUtil.applyPlaceholders(subtitleRaw, clearedFloor, delay);

            sendTitleToAll(titleRaw, subtitleRaw);

            Bukkit.getScheduler().runTaskLater(plugin, () -> finishDungeon(d), delay * 20L);
            return;
        }

        // ✅ NOVO: anuncia "andar limpo" ou "próximo é boss" (textos do próximo andar quando boss)
        int nextFloor = clearedFloor + 1;
        announceNextFloorHint(d, clearedFloor, nextFloor, delay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            this.floor = clearedFloor + 1;
            startFloor();
        }, delay * 20L);
    }

    // =========================
    // FINISH (DB SAVE HERE)
    // =========================

    private void finishDungeon(FileConfiguration d) {
        int maxFloors = d.getInt("max_floors", 10);
        long durationMs = (runStartedAtMs > 0L) ? (System.currentTimeMillis() - runStartedAtMs) : 0L;
        if (durationMs < 0) durationMs = 0;

        recordRunToDatabase(true, maxFloors, durationMs);

        end(EndReason.FINISH);
    }

    private void recordRunToDatabase(boolean finished, int reachedFloor, long durationMs) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;

        try {
            UUID leader = (leaderUuid != null) ? leaderUuid : (players.isEmpty() ? null : players.iterator().next());
            if (leader == null) return;

            PlayerStatsRepository statsRepo = plugin.getPlayerStatsRepository();
            String membersCsv = RunHistoryRepository.membersCsv(players);
            String partyKey = partyRun ? RunHistoryRepository.partyKey(players) : "";

            for (UUID pid : new ArrayList<>(players)) {
                statsRepo.recordRun(pid, partyRun, finished);
            }

            plugin.getTowerStatsRepository().recordRun(players, dungeonId, finished, reachedFloor, durationMs);

            plugin.getRunHistoryRepository().insertRun(
                    dungeonId,
                    finished,
                    reachedFloor,
                    durationMs,
                    partyRun,
                    partyKey,
                    Math.max(1, players.size()),
                    leader,
                    membersCsv
            );

        } catch (Throwable t) {
            plugin.getLogger().warning(log(
                    "dungeon.database_record_failed",
                    "recordRunToDatabase falhou: {err}",
                    Map.of("err", String.valueOf(t.getMessage()))
            ));
        }
    }

    // =========================
    // END / CLEANUP
    // =========================

    public void end(EndReason reason) {
        if (ended) return;
        ended = true;

        if (reason != EndReason.FINISH) {
            FileConfiguration d = dungeon();
            int reached = Math.max(0, this.floor);
            long durationMs = (runStartedAtMs > 0L) ? (System.currentTimeMillis() - runStartedAtMs) : 0L;
            if (runStartedAtMs > 0L && d != null) {
                recordRunToDatabase(false, reached, durationMs);
            }
        }

        stopFloorMonitor();
        clearTrackedMobs();

        FileConfiguration d = dungeon();

        Location back = readArenaLocationSection(d, "return_spawn");
        if (back == null) back = getFallbackSpawn();

        final Location backFinal = back;
        if (backFinal != null) {
            forEachOnline(p -> {
                forceAliveState(p);
                p.teleport(backFinal);
                protectBackAfterTeleport(p, backFinal);
            });
        }

        deadThisFloor.clear();
        entrySpawnByPlayer.clear();

        plugin.getInfinityTowerManager().getSessionManager().removeSession(this);
    }

    private void clearTrackedMobs() {
        stopFloorMonitor();

        for (UUID mobId : new HashSet<>(mobs)) {
            Entity e = Bukkit.getEntity(mobId);
            if (e != null && !e.isDead()) e.remove();
        }
        mobs.clear();
    }

    // =========================
    // REWARDS (VIVOS NO ANDAR)
    // =========================

    private void giveFloorRewards(FileConfiguration d, int floorValue) {
        ConfigurationSection rewardsSec = d.getConfigurationSection("floors." + floorValue + ".rewards");
        if (rewardsSec == null) return;
        if (!rewardsSec.getBoolean("enabled", true)) return;

        List<Map<?, ?>> items = rewardsSec.getMapList("items");

        for (UUID pid : new ArrayList<>(players)) {
            if (deadThisFloor.contains(pid)) continue;

            Player p = Bukkit.getPlayer(pid);
            if (p == null || !p.isOnline()) continue;

            for (Map<?, ?> map : items) {
                String typeName = map.get("type") != null ? String.valueOf(map.get("type")) : null;

                int amount = 1;
                Object amtObj = map.get("amount");
                if (amtObj instanceof Number n) amount = n.intValue();

                if (typeName == null || typeName.isBlank() || amount <= 0) continue;

                try {
                    Material mat = Material.valueOf(typeName.toUpperCase(Locale.ROOT));
                    ItemStack stack = new ItemStack(mat, amount);

                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) {
                        for (ItemStack item : leftover.values()) {
                            p.getWorld().dropItemNaturally(p.getLocation(), item);
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning(log(
                            "dungeon.reward_invalid_item",
                            "Reward item inválido: {item} (floor {floor})",
                            Map.of("item", String.valueOf(typeName), "floor", String.valueOf(floorValue))
                    ));
                }
            }
        }

        List<String> commands = rewardsSec.getStringList("commands");
        for (UUID pid : new ArrayList<>(players)) {
            if (deadThisFloor.contains(pid)) continue;

            Player p = Bukkit.getPlayer(pid);
            if (p == null || !p.isOnline()) continue;

            for (String cmd : commands) {
                if (cmd == null || cmd.isBlank()) continue;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
            }
        }
    }

    // =========================
    // TITLES / HELPERS
    // =========================

    private int getDelaySeconds(FileConfiguration d) {
        return d.getInt("next_floor_delay_seconds", 5);
    }

    private void sendDungeonTitleToAll(FileConfiguration d, String titlePath, String subtitlePath, int floor, int delay) {
        String titleRaw = d.getString(titlePath);
        String subtitleRaw = d.getString(subtitlePath);

        if (titleRaw == null) titleRaw = titleDefault("tower_started.title", "Infinity Tower");
        if (subtitleRaw == null) subtitleRaw = subtitleDefault("tower_started.subtitle", "Boa sorte!");

        titleRaw = TextUtil.applyPlaceholders(titleRaw, floor, delay);
        subtitleRaw = TextUtil.applyPlaceholders(subtitleRaw, floor, delay);
        sendTitleToAll(titleRaw, subtitleRaw);
    }

    private void sendTitleToAll(String titleRaw, String subtitleRaw) {
        int fadeIn = plugin.getConfig().getInt("tower.title_times.fade_in_ticks", 10);
        int stay = plugin.getConfig().getInt("tower.title_times.stay_ticks", 60);
        int fadeOut = plugin.getConfig().getInt("tower.title_times.fade_out_ticks", 10);

        forEachOnline(p -> p.sendTitle(TextUtil.color(titleRaw), TextUtil.color(subtitleRaw), fadeIn, stay, fadeOut));
    }

    private void broadcast(String msg) {
        forEachOnline(p -> p.sendMessage(msg));
    }

    private void forEachOnline(java.util.function.Consumer<Player> action) {
        for (UUID pid : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline()) action.accept(p);
        }
    }

    private Player firstOnlinePlayer() {
        for (UUID pid : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline()) return p;
        }
        return null;
    }

    private String firstOnlinePlayerName() {
        Player p = firstOnlinePlayer();
        return p != null ? p.getName() : null;
    }

    // =========================
    // ENTRY SPAWNS (SOLO / PARTY)
    // =========================

    private Location pickEntryForPlayer(FileConfiguration d, Player p) {
        if (d == null || p == null) return null;

        if (partyRun && leaderUuid != null) {
            if (p.getUniqueId().equals(leaderUuid)) {
                Location leader = readArenaLocationSection(d, "party_leader_spawn");
                if (leader != null) return leader;
            } else {
                Location member = pickRandomArenaListSpawn(d, "party_member_spawns");
                if (member != null) return member;
            }
        }

        return pickRandomPlayerSpawn(d);
    }

    private Location pickRandomArenaListSpawn(FileConfiguration d, String pathWithoutPrefix) {
        String pfx = arenaPrefix(d);
        List<Map<?, ?>> list = d.getMapList(pfx + pathWithoutPrefix);
        if (list == null || list.isEmpty()) return null;
        Map<?, ?> pick = list.get(new Random().nextInt(list.size()));
        return readLocation(pick);
    }

    // =========================
    // LOCATIONS
    // =========================

    private Location pickRandomPlayerSpawn(FileConfiguration d) {
        String pfx = arenaPrefix(d);
        List<Map<?, ?>> list = d.getMapList(pfx + "player_spawns");
        if (list == null || list.isEmpty()) return null;
        Map<?, ?> pick = list.get(new Random().nextInt(list.size()));
        return readLocation(pick);
    }

    private Location readArenaLocationSection(FileConfiguration cfg, String pathWithoutPrefix) {
        if (cfg == null) return null;
        String pfx = arenaPrefix(cfg);
        return readLocationSection(cfg, pfx + pathWithoutPrefix);
    }

    private Location readLocationSection(FileConfiguration cfg, String path) {
        if (cfg == null) return null;
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return null;

        Map<String, Object> map = new HashMap<>();
        for (String key : sec.getKeys(false)) map.put(key, sec.get(key));
        return readLocation(map);
    }

    private Location readLocation(Map<?, ?> map) {
        if (map == null) return null;

        String worldName = map.get("world") != null ? String.valueOf(map.get("world")) : "world";
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = getDouble(map.get("x"), 0);
        double y = getDouble(map.get("y"), 64);
        double z = getDouble(map.get("z"), 0);
        float yaw = (float) getDouble(map.get("yaw"), 0);
        float pitch = (float) getDouble(map.get("pitch"), 0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location getFallbackSpawn() {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = plugin.getConfig().getDouble("spawn.x", 0.5);
        double y = plugin.getConfig().getDouble("spawn.y", 100.0);
        double z = plugin.getConfig().getDouble("spawn.z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private double getDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            if (o != null) return Double.parseDouble(String.valueOf(o));
        } catch (Exception ignored) {}
        return def;
    }
}