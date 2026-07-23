package com.eternity.infinitytower.placeholder;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.database.PlayerStatsRepository;
import com.eternity.infinitytower.database.TowerStatsRepository;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

public final class InfinityTowerExpansion extends PlaceholderExpansion {

    private final InfinityTower plugin;

    public InfinityTowerExpansion(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "infinitytower";
    }

    @Override
    public String getAuthor() {
        return "Eternity";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer p, String params) {
        if (params == null) return "";

        params = params.toLowerCase(Locale.ROOT).trim();

        // =========================
        // PLAYER GLOBAL STATS
        // =========================
        if (params.equals("solo_runs") || params.equals("solo_wins") || params.equals("solo_losses") ||
            params.equals("party_runs") || params.equals("party_wins") || params.equals("party_losses") ||
            params.equals("runs") || params.equals("wins") || params.equals("losses")) {

            if (p == null) return "0";

            PlayerStatsRepository repo = plugin.getPlayerStatsRepository();
            if (repo == null) return "0";

            PlayerStatsRepository.PlayerStats st = repo.getStats(p.getUniqueId());

            long soloRuns = st.soloRuns;
            long soloWins = st.soloWins;
            long soloLosses = st.soloLosses;

            long partyRuns = st.partyRuns;
            long partyWins = st.partyWins;
            long partyLosses = st.partyLosses;

            long runs = soloRuns + partyRuns;
            long wins = soloWins + partyWins;
            long losses = soloLosses + partyLosses;

            return switch (params) {
                case "solo_runs" -> String.valueOf(soloRuns);
                case "solo_wins" -> String.valueOf(soloWins);
                case "solo_losses" -> String.valueOf(soloLosses);
                case "party_runs" -> String.valueOf(partyRuns);
                case "party_wins" -> String.valueOf(partyWins);
                case "party_losses" -> String.valueOf(partyLosses);
                case "runs" -> String.valueOf(runs);
                case "wins" -> String.valueOf(wins);
                case "losses" -> String.valueOf(losses);
                default -> "0";
            };
        }

        // =========================
        // TOP WINS / TOP FLOOR placeholders (AJLeaderboards / hologramas)
        // Formatos:
        // %infinitytower_top_wins_<pos>_<dungeon>_name%
        // %infinitytower_top_wins_<pos>_<dungeon>_value%
        // %infinitytower_top_floor_<pos>_<dungeon>_name%
        // %infinitytower_top_floor_<pos>_<dungeon>_value%
        // Ex: %infinitytower_top_wins_1_solo_10_name%
        //
        // ⚠ dungeonId pode ter underscore (ex: solo_10), então o parsing
        // isola só o <pos> (primeiro underscore) e o <field> (último underscore)
        // e deixa tudo que sobrar no meio como dungeonId.
        // =========================
        if (params.startsWith("top_wins_") || params.startsWith("top_floor_")) {
            boolean isFloor = params.startsWith("top_floor_");
            String rest = params.substring(isFloor ? "top_floor_".length() : "top_wins_".length());

            int firstUnderscore = rest.indexOf('_');
            if (firstUnderscore < 0) return "";
            String posStr = rest.substring(0, firstUnderscore);

            String afterPos = rest.substring(firstUnderscore + 1);
            int lastUnderscore = afterPos.lastIndexOf('_');
            if (lastUnderscore < 0) return "";

            String dungeonId = afterPos.substring(0, lastUnderscore);
            String field = afterPos.substring(lastUnderscore + 1);
            if (dungeonId.isBlank()) return "";

            int pos;
            try {
                pos = Integer.parseInt(posStr);
            } catch (Exception e) {
                return "";
            }

            TowerStatsRepository repo = plugin.getTowerStatsRepository();
            if (repo == null) return "";

            List<com.eternity.infinitytower.manager.RankingManager.Entry> top = isFloor
                    ? repo.selectTopBestFloor(dungeonId, Math.max(1, pos))
                    : repo.selectTopWins(dungeonId, Math.max(1, pos));

            if (top.size() < pos) {
                return field.equals("value") ? "0" : "-";
            }

            var entry = top.get(pos - 1);

            if (field.equals("value")) {
                return String.valueOf(entry.value());
            }

            // name
            String name = Bukkit.getOfflinePlayer(entry.uuid()).getName();
            return (name == null || name.isBlank()) ? entry.uuid().toString() : name;
        }

        // =========================
        // BEST RECORD placeholders (seu RankingManager usa isso)
        // %infinitytower_best_player_<dungeon>_name%
        // %infinitytower_best_player_<dungeon>_time_ms%
        // %infinitytower_best_party_<dungeon>_time_ms%
        // %infinitytower_best_party_<dungeon>_leader%
        //
        // ⚠ dungeonId pode ter underscore, e o campo "time_ms" também tem
        // underscore — por isso o field é resolvido tentando os sufixos
        // conhecidos, nunca por split() por posição fixa.
        // =========================
        if (params.startsWith("best_player_")) {
            String rest = params.substring("best_player_".length());

            String dungeonId;
            String field;
            if (rest.endsWith("_time_ms")) {
                field = "time_ms";
                dungeonId = rest.substring(0, rest.length() - "_time_ms".length());
            } else if (rest.endsWith("_name")) {
                field = "name";
                dungeonId = rest.substring(0, rest.length() - "_name".length());
            } else {
                return "";
            }
            if (dungeonId.isBlank()) return "";

            TowerStatsRepository repo = plugin.getTowerStatsRepository();
            if (repo == null) return "";

            var rec = repo.selectBestPlayerRecord(dungeonId);
            if (rec == null) return field.equals("time_ms") ? "0" : "-";

            return switch (field) {
                case "name" -> {
                    String name = Bukkit.getOfflinePlayer(rec.uuid()).getName();
                    yield (name == null || name.isBlank()) ? rec.uuid().toString() : name;
                }
                case "time_ms" -> String.valueOf(rec.timeMs());
                default -> "";
            };
        }

        if (params.startsWith("best_party_")) {
            String rest = params.substring("best_party_".length());

            String dungeonId;
            String field;
            if (rest.endsWith("_time_ms")) {
                field = "time_ms";
                dungeonId = rest.substring(0, rest.length() - "_time_ms".length());
            } else if (rest.endsWith("_leader")) {
                field = "leader";
                dungeonId = rest.substring(0, rest.length() - "_leader".length());
            } else {
                return "";
            }
            if (dungeonId.isBlank()) return "";

            TowerStatsRepository repo = plugin.getTowerStatsRepository();
            if (repo == null) return "";

            var rec = repo.selectBestPartyRecord(dungeonId);
            if (rec == null) return field.equals("time_ms") ? "0" : "-";

            return switch (field) {
                case "time_ms" -> String.valueOf(rec.timeMs());
                case "leader" -> {
                    String name = Bukkit.getOfflinePlayer(rec.leaderUuid()).getName();
                    yield (name == null || name.isBlank()) ? rec.leaderUuid().toString() : name;
                }
                default -> "";
            };
        }

        return null;
    }
}