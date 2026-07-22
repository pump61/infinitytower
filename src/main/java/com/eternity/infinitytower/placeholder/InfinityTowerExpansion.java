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
        // TOP WINS placeholders (AJLeaderboards / hologramas)
        // Formatos:
        // %infinitytower_top_wins_<pos>_<dungeon>_name%
        // %infinitytower_top_wins_<pos>_<dungeon>_value%
        // Ex: %infinitytower_top_wins_1_torre1_name%
        // =========================
        if (params.startsWith("top_wins_")) {
            // top_wins_<pos>_<dungeon>_(name|value)
            String[] parts = params.split("_", 5);
            // [0]=top [1]=wins [2]=pos [3]=dungeon [4]=name/value
            if (parts.length < 5) return "";

            int pos;
            try {
                pos = Integer.parseInt(parts[2]);
            } catch (Exception e) {
                return "";
            }

            String dungeonId = parts[3];
            String field = parts[4];

            TowerStatsRepository repo = plugin.getTowerStatsRepository();
            if (repo == null) return "";

            List<com.eternity.infinitytower.manager.RankingManager.Entry> top = repo.selectTopWins(dungeonId, Math.max(1, pos));
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
        // =========================
        if (params.startsWith("best_player_")) {
            String[] parts = params.split("_", 4);
            if (parts.length < 4) return "";
            String dungeonId = parts[2];
            String field = parts[3];

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
            String[] parts = params.split("_", 4);
            if (parts.length < 4) return "";
            String dungeonId = parts[2];
            String field = parts[3];

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