package com.eternity.infinitytower.setup;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.dungeon.DungeonRegistry;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public final class ArenaSetupManager {

    private final InfinityTower plugin;
    private final Map<UUID, SetupSession> sessions = new HashMap<>();

    public ArenaSetupManager(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public SetupSession get(Player admin) {
        return sessions.get(admin.getUniqueId());
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
        return TextUtil.color(langRaw("setup." + key, def));
    }

    private String msg(String key, String def, Map<String, String> vars) {
        String s = langRaw("setup." + key, def);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return TextUtil.color(s);
    }

    public void cancel(Player admin) {
        sessions.remove(admin.getUniqueId());
        admin.sendMessage(msg("canceled", "&cSetup cancelado."));
    }

    public boolean begin(Player admin, String dungeonId, int arenaIndex) {
        DungeonRegistry reg = plugin.getDungeonRegistry();
        if (!reg.exists(dungeonId)) {
            admin.sendMessage(msg("dungeon_not_found", "&cDungeon não encontrada: &f{dungeon}",
                    Map.of("dungeon", String.valueOf(dungeonId))));
            return false;
        }

        int arena = Math.max(1, arenaIndex);
        SetupSession ss = new SetupSession(dungeonId.toLowerCase(Locale.ROOT), arena);
        sessions.put(admin.getUniqueId(), ss);

        admin.sendMessage(msg("started", "&aSetup iniciado em &f{dungeon} &7| arena &f{arena}",
                Map.of("dungeon", ss.dungeonId(), "arena", String.valueOf(ss.arenaIndex()))));
        admin.sendMessage(msg("hint_save", "&7Use &f/tower admin setup save &7para salvar."));
        return true;
    }

    public boolean setArena(Player admin, int arenaIndex) {
        SetupSession ss = get(admin);
        if (ss == null) {
            admin.sendMessage(msg("not_in_setup",
                    "&cVocê não está em setup. Use: &f/tower admin setup <dungeonId> [arena]"));
            return false;
        }
        ss.setArenaIndex(Math.max(1, arenaIndex));
        admin.sendMessage(msg("arena_selected", "&aArena selecionada: &f{arena}",
                Map.of("arena", String.valueOf(ss.arenaIndex()))));
        return true;
    }

    // =========================
    // WRITE HELPERS
    // =========================

    private ConfigurationSection editable(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return null;
        return plugin.getDungeonRegistry().getEditableSection(ss.dungeonId());
    }

    private String arenaPrefix(ConfigurationSection rootOrExtra, String dungeonId, int arenaIndex) {
        if (arenaIndex <= 1) return "";
        String key = dungeonId.toLowerCase(Locale.ROOT) + "_arena" + arenaIndex;

        if (rootOrExtra.getConfigurationSection(key) == null) {
            rootOrExtra.createSection(key);
        }
        return key + ".";
    }

    private void setLocSection(ConfigurationSection root, String path, Location l) {
        if (root == null || l == null || l.getWorld() == null) return;

        root.set(path + ".world", l.getWorld().getName());
        root.set(path + ".x", round(l.getX()));
        root.set(path + ".y", round(l.getY()));
        root.set(path + ".z", round(l.getZ()));
        root.set(path + ".yaw", round((double) l.getYaw()));
        root.set(path + ".pitch", round((double) l.getPitch()));
    }

    private Map<String, Object> locToMap(Location l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", l.getWorld() == null ? "world" : l.getWorld().getName());
        m.put("x", round(l.getX()));
        m.put("y", round(l.getY()));
        m.put("z", round(l.getZ()));
        m.put("yaw", round((double) l.getYaw()));
        m.put("pitch", round((double) l.getPitch()));
        return m;
    }

    private Map<String, Object> safeCopyMap(Map<?, ?> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (in == null) return out;
        for (Map.Entry<?, ?> e : in.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
        return out;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String fmtLoc(Map<?, ?> m) {
        if (m == null) return "null";
        Object w = m.get("world");
        Object x = m.get("x");
        Object y = m.get("y");
        Object z = m.get("z");
        Object yaw = m.get("yaw");
        Object pitch = m.get("pitch");

        String ws = (w == null ? "world" : String.valueOf(w));
        String xs = (x == null ? "0" : String.valueOf(x));
        String ys = (y == null ? "0" : String.valueOf(y));
        String zs = (z == null ? "0" : String.valueOf(z));
        String yaws = (yaw == null ? "0" : String.valueOf(yaw));
        String pitchs = (pitch == null ? "0" : String.valueOf(pitch));

        return ws + " " + xs + " " + ys + " " + zs + " yaw=" + yaws + " pitch=" + pitchs;
    }

    // =========================
    // SOLO SPAWNS
    // =========================

    public boolean soloSetSpawn(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(locToMap(admin.getLocation()));
        edit.set(pfx + "player_spawns", list);

        admin.sendMessage(msg("solo_spawn_set", "&aSpawn SOLO definido (player_spawns = 1)"));
        return true;
    }

    public boolean soloAddSpawn(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());

        List<Map<?, ?>> current = edit.getMapList(pfx + "player_spawns");
        List<Map<String, Object>> out = new ArrayList<>();
        if (current != null) for (Map<?, ?> x : current) out.add(safeCopyMap(x));

        out.add(locToMap(admin.getLocation()));
        edit.set(pfx + "player_spawns", out);

        admin.sendMessage(msg("solo_spawn_added", "&aSpawn SOLO adicionado. Total: &f{total}",
                Map.of("total", String.valueOf(out.size()))));
        return true;
    }

    public boolean soloClear(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());
        edit.set(pfx + "player_spawns", new ArrayList<>());

        admin.sendMessage(msg("solo_spawns_cleared", "&aSpawns SOLO limpos."));
        return true;
    }

    public boolean soloList(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        FileConfiguration view = plugin.getDungeonRegistry().getDungeon(ss.dungeonId());
        if (view == null) return false;

        String pfx = (ss.arenaIndex() <= 1) ? "" : (ss.dungeonId() + "_arena" + ss.arenaIndex() + ".");
        List<Map<?, ?>> list = view.getMapList(pfx + "player_spawns");

        int total = (list == null ? 0 : list.size());
        admin.sendMessage(msg("solo_list_header", "&eSpawns SOLO (&f{total}&e):",
                Map.of("total", String.valueOf(total))));

        if (list != null) {
            int i = 1;
            for (Map<?, ?> m : list) {
                admin.sendMessage(msg("solo_list_item", "&7- &f{i}&7) &a{loc}",
                        Map.of("i", String.valueOf(i), "loc", fmtLoc(m))));
                i++;
            }
        }
        return true;
    }

    // =========================
    // PARTY SPAWNS
    // =========================

    public boolean partySetLeader(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());
        setLocSection(edit, pfx + "party_leader_spawn", admin.getLocation());

        admin.sendMessage(msg("party_leader_set", "&aSpawn do líder definido."));
        return true;
    }

    public boolean partyAddMember(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());

        List<Map<?, ?>> current = edit.getMapList(pfx + "party_member_spawns");
        List<Map<String, Object>> out = new ArrayList<>();
        if (current != null) for (Map<?, ?> x : current) out.add(safeCopyMap(x));

        out.add(locToMap(admin.getLocation()));
        edit.set(pfx + "party_member_spawns", out);

        admin.sendMessage(msg("party_member_added", "&aSpawn de membro adicionado. Total: &f{total}",
                Map.of("total", String.valueOf(out.size()))));
        return true;
    }

    public boolean partyClearMembers(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());
        edit.set(pfx + "party_member_spawns", new ArrayList<>());

        admin.sendMessage(msg("party_members_cleared", "&aSpawns de membros limpos."));
        return true;
    }

    public boolean partyList(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        FileConfiguration view = plugin.getDungeonRegistry().getDungeon(ss.dungeonId());
        if (view == null) return false;

        String pfx = (ss.arenaIndex() <= 1) ? "" : (ss.dungeonId() + "_arena" + ss.arenaIndex() + ".");

        ConfigurationSection leaderSec = view.getConfigurationSection(pfx + "party_leader_spawn");
        List<Map<?, ?>> members = view.getMapList(pfx + "party_member_spawns");

        admin.sendMessage(msg("party_list_header", "&eParty spawns:"));

        if (leaderSec != null) {
            admin.sendMessage(msg("party_leader_value", "&7- &eLeader: &a{loc}",
                    Map.of("loc", fmtLoc(leaderSec.getValues(false)))));
        } else {
            admin.sendMessage(msg("party_leader_missing", "&7- &eLeader: &c(não definido)"));
        }

        int total = (members == null ? 0 : members.size());
        admin.sendMessage(msg("party_members_header", "&7- &eMembers (&f{total}&e):",
                Map.of("total", String.valueOf(total))));

        if (members != null) {
            int i = 1;
            for (Map<?, ?> m : members) {
                admin.sendMessage(msg("party_members_item", "&7  • &f{i}&7) &a{loc}",
                        Map.of("i", String.valueOf(i), "loc", fmtLoc(m))));
                i++;
            }
        }
        return true;
    }

    // =========================
    // RETURN
    // =========================

    public boolean returnSet(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        ConfigurationSection edit = editable(admin);
        if (edit == null) return false;

        String pfx = arenaPrefix(edit, ss.dungeonId(), ss.arenaIndex());
        setLocSection(edit, pfx + "return_spawn", admin.getLocation());

        admin.sendMessage(msg("return_set", "&aReturn spawn definido."));
        return true;
    }

    public boolean returnShow(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) return false;

        FileConfiguration view = plugin.getDungeonRegistry().getDungeon(ss.dungeonId());
        if (view == null) return false;

        String pfx = (ss.arenaIndex() <= 1) ? "" : (ss.dungeonId() + "_arena" + ss.arenaIndex() + ".");
        ConfigurationSection sec = view.getConfigurationSection(pfx + "return_spawn");
        if (sec == null) {
            admin.sendMessage(msg("return_missing", "&creturn_spawn não definido."));
            return true;
        }

        // ✅ FIX: removido 1 ')' sobrando aqui
        admin.sendMessage(msg("return_value", "&eReturn: &a{loc}",
                Map.of("loc", fmtLoc(sec.getValues(false)))));

        return true;
    }

    // =========================
    // SAVE
    // =========================

    public boolean save(Player admin) {
        SetupSession ss = get(admin);
        if (ss == null) {
            admin.sendMessage(msg("save_not_in_setup", "&cVocê não está em setup."));
            return false;
        }

        boolean ok = plugin.getDungeonRegistry().saveDungeon(ss.dungeonId());
        if (!ok) {
            admin.sendMessage(msg("save_failed", "&cFalha ao salvar dungeon."));
            return false;
        }

        plugin.getDungeonRegistry().reload();
        sessions.remove(admin.getUniqueId());

        admin.sendMessage(msg("saved_and_reloaded", "&aDungeon salva e recarregada: &f{dungeon}",
                Map.of("dungeon", ss.dungeonId())));

        return true;
    }
}