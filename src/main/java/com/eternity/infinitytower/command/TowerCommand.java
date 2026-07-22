package com.eternity.infinitytower.command;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.manager.RankingManager;
import com.eternity.infinitytower.party.Party;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public final class TowerCommand implements CommandExecutor, TabCompleter {

    private final InfinityTower plugin;

    // adminUuid -> setup context
    private final Map<UUID, SetupContext> setups = new HashMap<>();

    private static final class SetupContext {
        final String dungeonId;
        final int arenaIndex;
        final String prefix; // "" or "dungeon_arena2."
        final File file;
        final YamlConfiguration cfg;

        SetupContext(String dungeonId, int arenaIndex, String prefix, File file, YamlConfiguration cfg) {
            this.dungeonId = dungeonId;
            this.arenaIndex = arenaIndex;
            this.prefix = prefix;
            this.file = file;
            this.cfg = cfg;
        }
    }

    public TowerCommand(InfinityTower plugin) {
        this.plugin = plugin;
    }

    // =========================
    // COMMAND
    // =========================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        boolean isPlayer = sender instanceof Player;
        boolean isAdmin = sender.hasPermission("infinitytower.admin");
        boolean isUser = sender.hasPermission("infinitytower.player") || isAdmin;

        if (!isUser) {
            sender.sendMessage(lang("messages.no_permission", "&cSem permissão."));
            return true;
        }

        if (args.length == 0) {
            if (isPlayer) sendHelp((Player) sender);
            else sender.sendMessage(colorPlainHelpConsole());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {

            case "help" -> {
                if (isPlayer) sendHelp((Player) sender);
                else sender.sendMessage(colorPlainHelpConsole());
                return true;
            }

            // =========================
            // MENU PRINCIPAL (PLAYER)
            // =========================

            case "menu" -> {
                if (!isPlayer) {
                    sender.sendMessage(lang("messages.only_players", "&cApenas jogadores podem usar este comando."));
                    return true;
                }

                Player pSender = (Player) sender;

                // /tower menu  -> abre menu principal
                if (args.length == 1) {
                    plugin.getMenuManager().openMainMenu(pSender);
                    return true;
                }

                // Daqui pra baixo: mantém o admin menu antigo
                if (!isAdmin) {
                    sender.sendMessage(lang("messages.no_permission", "&cSem permissão."));
                    return true;
                }

                // /tower menu <solo|party> [player]
                String which = args[1].toLowerCase(Locale.ROOT);
                if (!which.equals("solo") && !which.equals("party")) {
                    sender.sendMessage(lang("usage.menu", "&cUse: &f/tower menu <solo|party> [player]"));
                    return true;
                }

                Player target = pSender;

                if (args.length >= 3) {
                    Player p = Bukkit.getPlayerExact(args[2]);
                    if (p == null || !p.isOnline()) {
                        pSender.sendMessage(apply(lang("messages.player_offline", "&cJogador offline: &f{player}"),
                                Map.of("{player}", args[2])));
                        return true;
                    }
                    target = p;
                }

                if (which.equals("solo")) plugin.getMenuManager().openSoloMenu(target);
                else plugin.getMenuManager().openPartyMenu(target);

                return true;
            }

            // =========================
            // STATS (PLAYER)
            // =========================

            case "stats" -> {
                if (!isPlayer) {
                    sender.sendMessage(lang("messages.only_players", "&cApenas jogadores podem usar este comando."));
                    return true;
                }

                Player p = (Player) sender;

                try {
                    Object mm = plugin.getMenuManager();
                    var m = mm.getClass().getMethod("openStatsMenu", Player.class);
                    m.invoke(mm, p);
                } catch (NoSuchMethodException e) {
                    p.sendMessage(lang("messages.stats_menu_missing",
                            "&cMenu de stats ainda não foi instalado. (Faltando: MenuManager#openStatsMenu)"));
                } catch (Exception e) {
                    p.sendMessage(lang("messages.stats_menu_error",
                            "&cErro ao abrir menu de stats. Veja o console."));
                    plugin.getLogger().warning("Falha ao abrir stats menu: " + e.getMessage());
                }

                return true;
            }

            // =========================
            // PARTY (NOVO PADRÃO)
            // =========================

            case "party" -> {
                if (!isPlayer) {
                    sender.sendMessage(lang("messages.only_players", "&cApenas jogadores podem usar este comando."));
                    return true;
                }

                Player player = (Player) sender;

                if (args.length < 2) {
                    player.sendMessage(lang("usage.party_root",
                            "&cUse: &f/tower party <invite|accept|leave|disband|promote|kick>"));
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);

                switch (action) {

                    case "invite" -> {
                        if (args.length < 3) {
                            player.sendMessage(lang("usage.party_invite", "&cUse: &f/tower party invite <player>"));
                            return true;
                        }

                        Player target = Bukkit.getPlayerExact(args[2]);
                        if (target == null || !target.isOnline()) {
                            player.sendMessage(apply(lang("messages.player_offline", "&cJogador offline: &f{player}"),
                                    Map.of("{player}", args[2])));
                            return true;
                        }

                        plugin.getPartyManager().invite(player, target);
                        return true;
                    }

                    case "accept" -> {
                        plugin.getPartyManager().accept(player);
                        return true;
                    }

                    case "leave" -> {
                        // ✅ sair da PARTY (não cancela dungeon)
                        plugin.getPartyManager().leave(player);
                        return true;
                    }

                    case "disband" -> {
                        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
                        if (party == null) {
                            player.sendMessage(lang("messages.party_not_in_party", "&cVocê não está em party."));
                            return true;
                        }
                        if (!party.isLeader(player.getUniqueId())) {
                            player.sendMessage(lang("messages.party_only_leader_disband", "&cApenas o líder pode desfazer a party."));
                            return true;
                        }

                        plugin.getPartyManager().disbandParty(party);
                        return true;
                    }

                    case "promote" -> {
                        if (args.length < 3) {
                            player.sendMessage(lang("usage.party_promote", "&cUse: &f/tower party promote <player>"));
                            return true;
                        }
                        // ✅ comando novo: promote
                        plugin.getPartyManager().transferLeader(player, args[2]);
                        return true;
                    }

                    case "kick" -> {
                        if (args.length < 3) {
                            player.sendMessage(lang("usage.party_kick", "&cUse: &f/tower party kick <player>"));
                            return true;
                        }
                        plugin.getPartyManager().kick(player, args[2]);
                        return true;
                    }

                    default -> {
                        player.sendMessage(lang("usage.party_root",
                                "&cUse: &f/tower party <invite|accept|leave|disband|promote|kick>"));
                        return true;
                    }
                }
            }

            // =========================
            // ALIASES ANTIGOS (mantém)
            // =========================

            case "invite" -> {
                if (!isPlayer) {
                    sender.sendMessage(lang("messages.only_players", "&cApenas jogadores podem usar este comando."));
                    return true;
                }

                Player player = (Player) sender;

                if (args.length < 2) {
                    player.sendMessage(lang("usage.party_invite", "&cUse: &f/tower party invite <player>"));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(apply(lang("messages.player_offline", "&cJogador offline: &f{player}"),
                            Map.of("{player}", args[1])));
                    return true;
                }

                plugin.getPartyManager().invite(player, target);
                return true;
            }

            case "accept" -> {
                if (!isPlayer) {
                    sender.sendMessage(lang("messages.only_players", "&cApenas jogadores podem usar este comando."));
                    return true;
                }

                Player player = (Player) sender;
                plugin.getPartyManager().accept(player);
                return true;
            }

            // =========================
            // LEAVE (SAIR DA DUNGEON)
            // =========================

            case "leave" -> {
                if (!isPlayer) {
                    sender.sendMessage(lang("messages.only_players", "&cApenas jogadores podem usar este comando."));
                    return true;
                }

                Player player = (Player) sender;
                plugin.getInfinityTowerManager().leave(player);
                return true;
            }

            // =========================
            // GIVE / GIVEALL (ADMIN)
            // =========================

            case "give" -> {
                if (!isAdmin) {
                    sender.sendMessage(lang("messages.no_permission", "&cSem permissão."));
                    return true;
                }

                if (args.length < 2 || !args[1].equalsIgnoreCase("key")) {
                    sender.sendMessage(lang("usage.give_key", "&cUse: &f/tower give key <player> <dungeonId> [amount]"));
                    return true;
                }

                if (args.length < 4) {
                    sender.sendMessage(lang("usage.give_key", "&cUse: &f/tower give key <player> <dungeonId> [amount]"));
                    return true;
                }

                String playerName = args[2];
                String dungeonId = args[3].toLowerCase(Locale.ROOT);

                Player target = Bukkit.getPlayerExact(playerName);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(apply(lang("messages.player_offline", "&cJogador offline: &f{player}"),
                            Map.of("{player}", playerName)));
                    return true;
                }

                if (plugin.getDungeonRegistry().getDungeon(dungeonId) == null) {
                    sender.sendMessage(apply(lang("messages.dungeon_not_found", "&cDungeon não existe: &f{dungeon}"),
                            Map.of("{dungeon}", dungeonId)));
                    return true;
                }

                int amount = 1;
                if (args.length >= 5) {
                    try { amount = Math.max(1, Integer.parseInt(args[4])); } catch (Exception ignored) {}
                }

                ItemStack key = plugin.getKeyManager().buildKeyItem(dungeonId, amount);
                if (key == null) {
                    sender.sendMessage(apply(lang("messages.key_not_configured", "&cEssa dungeon não tem access.key configurado: &f{dungeon}"),
                            Map.of("{dungeon}", dungeonId)));
                    return true;
                }

                var leftover = target.getInventory().addItem(key);
                if (!leftover.isEmpty()) {
                    leftover.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
                }

                sender.sendMessage(apply(lang("messages.key_gave_sender",
                                "&aChave entregue para &f{player} &a(dungeon &f{dungeon}&a, x{amount})"),
                        Map.of("{player}", target.getName(), "{dungeon}", dungeonId, "{amount}", String.valueOf(amount))));

                target.sendMessage(apply(lang("messages.key_received",
                                "&aVocê recebeu &fx{amount} &achave(s) de &f{dungeon}&a."),
                        Map.of("{dungeon}", dungeonId, "{amount}", String.valueOf(amount))));

                return true;
            }

            case "giveall" -> {
                if (!isAdmin) {
                    sender.sendMessage(lang("messages.no_permission", "&cSem permissão."));
                    return true;
                }

                if (args.length < 3 || !args[1].equalsIgnoreCase("key")) {
                    sender.sendMessage(lang("usage.giveall_key", "&cUse: &f/tower giveall key <dungeonId> [amount]"));
                    return true;
                }

                String dungeonId = args[2].toLowerCase(Locale.ROOT);

                if (plugin.getDungeonRegistry().getDungeon(dungeonId) == null) {
                    sender.sendMessage(apply(lang("messages.dungeon_not_found", "&cDungeon não existe: &f{dungeon}"),
                            Map.of("{dungeon}", dungeonId)));
                    return true;
                }

                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (Exception ignored) {}
                }

                ItemStack key = plugin.getKeyManager().buildKeyItem(dungeonId, amount);
                if (key == null) {
                    sender.sendMessage(apply(lang("messages.key_not_configured", "&cEssa dungeon não tem access.key configurado: &f{dungeon}"),
                            Map.of("{dungeon}", dungeonId)));
                    return true;
                }

                int delivered = 0;

                for (Player p : Bukkit.getOnlinePlayers()) {
                    ItemStack give = key.clone();
                    var leftover = p.getInventory().addItem(give);
                    if (!leftover.isEmpty()) {
                        leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                    }

                    p.sendMessage(apply(lang("messages.key_received",
                                    "&aVocê recebeu &fx{amount} &achave(s) de &f{dungeon}&a."),
                            Map.of("{dungeon}", dungeonId, "{amount}", String.valueOf(amount))));

                    delivered++;
                }

                sender.sendMessage(apply(lang("messages.key_gaveall_sender",
                                "&aChave entregue para &f{count} &ajogador(es) online &7(dungeon &f{dungeon}&7, x{amount})"),
                        Map.of("{count}", String.valueOf(delivered), "{dungeon}", dungeonId, "{amount}", String.valueOf(amount))));

                return true;
            }

            // =========================
            // RANKINGS (PLAYER)
            // =========================

            case "top" -> {
                if (args.length < 3) {
                    sender.sendMessage(lang("usage.top", "&cUse: &f/tower top <wins|time> <dungeonId> [limit]"));
                    return true;
                }

                String type = args[1].toLowerCase(Locale.ROOT);
                String dungeonId = args[2].toLowerCase(Locale.ROOT);

                FileConfiguration dungeon = plugin.getDungeonRegistry().getDungeon(dungeonId);
                if (dungeon == null) {
                    sender.sendMessage(apply(lang("messages.dungeon_not_found", "&cDungeon não existe: &f{dungeon}"),
                            Map.of("{dungeon}", dungeonId)));
                    return true;
                }

                int limit = 10;
                if (args.length >= 4) {
                    try { limit = Integer.parseInt(args[3]); } catch (Exception ignored) {}
                }
                if (limit < 1) limit = 1;
                if (limit > 50) limit = 50;

                String display = colorVar(dungeon.getString("display", dungeonId));

                RankingManager rm = plugin.getRankingManager();
                if (rm == null) {
                    sender.sendMessage(lang("messages.ranking_not_initialized", "&cRankingManager não inicializado."));
                    return true;
                }

                if (type.equals("wins")) {
                    var top = rm.getTopWins(dungeonId, limit);

                    sender.sendMessage(apply(lang("messages.top_wins_header",
                                    "&b&lTOP VITÓRIAS &7- &f{display} &7(&f{dungeon}&7)"),
                            Map.of("{display}", display, "{dungeon}", dungeonId)));

                    if (top.isEmpty()) {
                        sender.sendMessage(lang("messages.no_records", "&7Nenhum registro ainda."));
                        return true;
                    }

                    int pos = 1;
                    for (var e : top) {
                        String name = rm.nameOf(e.uuid());
                        sender.sendMessage(apply(lang("messages.top_wins_line",
                                        "&f#{pos} &b{name} &7- &a{wins} &7vitórias"),
                                Map.of("{pos}", String.valueOf(pos), "{name}", name, "{wins}", String.valueOf(e.value()))));
                        pos++;
                    }
                    return true;
                }

                if (type.equals("time")) {
                    var top = rm.getTopBestTime(dungeonId, limit);

                    sender.sendMessage(apply(lang("messages.top_time_header",
                                    "&d&lTOP TEMPO &7- &f{display} &7(&f{dungeon}&7)"),
                            Map.of("{display}", display, "{dungeon}", dungeonId)));

                    if (top.isEmpty()) {
                        sender.sendMessage(lang("messages.no_records", "&7Nenhum recorde ainda."));
                        return true;
                    }

                    int pos = 1;
                    for (var e : top) {
                        String name = rm.nameOf(e.uuid());
                        String time = RankingManager.formatDuration(e.value());
                        sender.sendMessage(apply(lang("messages.top_time_line",
                                        "&f#{pos} &d{name} &7- &f{time}"),
                                Map.of("{pos}", String.valueOf(pos), "{name}", name, "{time}", time)));
                        pos++;
                    }
                    return true;
                }

                sender.sendMessage(lang("usage.top", "&cUse: &f/tower top <wins|time> <dungeonId> [limit]"));
                return true;
            }

            case "record" -> {
                if (args.length < 2) {
                    sender.sendMessage(lang("usage.record", "&cUse: &f/tower record <dungeonId>"));
                    return true;
                }

                String dungeonId = args[1].toLowerCase(Locale.ROOT);

                FileConfiguration dungeon = plugin.getDungeonRegistry().getDungeon(dungeonId);
                if (dungeon == null) {
                    sender.sendMessage(apply(lang("messages.dungeon_not_found", "&cDungeon não existe: &f{dungeon}"),
                            Map.of("{dungeon}", dungeonId)));
                    return true;
                }

                String display = colorVar(dungeon.getString("display", dungeonId));

                RankingManager rm = plugin.getRankingManager();
                if (rm == null) {
                    sender.sendMessage(lang("messages.ranking_not_initialized", "&cRankingManager não inicializado."));
                    return true;
                }

                var bestPlayer = rm.getBestPlayerRecord(dungeonId);
                var bestParty = rm.getBestPartyRecord(dungeonId);

                sender.sendMessage(apply(lang("messages.record_header",
                                "&6&lRECORDES &7- &f{display} &7(&f{dungeon}&7)"),
                        Map.of("{display}", display, "{dungeon}", dungeonId)));

                if (bestPlayer == null) {
                    sender.sendMessage(lang("messages.record_player_none", "&7• &ePlayer: &7sem recorde"));
                } else {
                    String name = rm.nameOf(bestPlayer.uuid());
                    String time = RankingManager.formatDuration(bestPlayer.timeMs());
                    sender.sendMessage(apply(lang("messages.record_player_line",
                                    "&7• &ePlayer: &f{name} &7- &f{time}"),
                            Map.of("{name}", name, "{time}", time)));
                }

                if (bestParty == null) {
                    sender.sendMessage(lang("messages.record_party_none", "&7• &bParty: &7sem recorde"));
                } else {
                    String leaderName = rm.nameOf(bestParty.leaderUuid());
                    String time = RankingManager.formatDuration(bestParty.timeMs());
                    String members = (bestParty.membersCsv() == null ? "" : bestParty.membersCsv());
                    if (members.isBlank()) members = leaderName;

                    sender.sendMessage(apply(lang("messages.record_party_line",
                                    "&7• &bParty: &f{leader} &7- &f{time}"),
                            Map.of("{leader}", leaderName, "{time}", time)));

                    sender.sendMessage(apply(lang("messages.record_party_members",
                                    "&8  membros: &7{members}"),
                            Map.of("{members}", members)));
                }

                return true;
            }

            // =========================
            // ADMIN GROUP
            // =========================

            case "admin" -> {
                if (!isAdmin) {
                    sender.sendMessage(lang("messages.no_permission", "&cSem permissão."));
                    return true;
                }

                if (args.length < 2) {
                    if (sender instanceof Player p) sendAdminHelp(p);
                    else sender.sendMessage(colorPlainAdminHelpConsole());
                    return true;
                }

                String a = args[1].toLowerCase(Locale.ROOT);

                switch (a) {

                    case "reload" -> {
                        plugin.getDungeonRegistry().reload();
                        sender.sendMessage(apply(lang("messages.admin_reloaded",
                                        "&aDungeons recarregadas. Total: &f{count}"),
                                Map.of("{count}", String.valueOf(plugin.getDungeonRegistry().getDungeonIds().size()))));
                        return true;
                    }

                    case "list" -> {
                        var ids = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
                        Collections.sort(ids);

                        List<String> names = new ArrayList<>();
                        for (String id : ids) {
                            FileConfiguration d = plugin.getDungeonRegistry().getDungeon(id);
                            String display = (d == null) ? id : d.getString("display", id);
                            names.add(colorVar(display) + "&7(&f" + id + "&7)");
                        }

                        sender.sendMessage(apply(
                                lang("messages.admin_list", "&aDungeons (&f{count}&a): &f{list}"),
                                Map.of(
                                        "{count}", String.valueOf(names.size()),
                                        "{list}", TextUtil.color(String.join("&7, &f", names))
                                )
                        ));
                        return true;
                    }

                    case "create" -> {
                        if (args.length < 3) {
                            sender.sendMessage(lang("usage.admin_create", "&cUse: &f/tower admin create <dungeonId>"));
                            return true;
                        }

                        String id = args[2].trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
                        if (id.isBlank()) {
                            sender.sendMessage(lang("messages.invalid_id", "&cID inválido."));
                            return true;
                        }

                        boolean ok = plugin.getDungeonRegistry().createFromTemplate(id);
                        if (ok) {
                            plugin.getDungeonRegistry().reload();
                            sender.sendMessage(apply(lang("messages.admin_created",
                                            "&aDungeon criada: &f{id}"),
                                    Map.of("{id}", id)));
                        } else {
                            sender.sendMessage(lang("messages.admin_create_failed", "&cNão foi possível criar. Veja o console."));
                        }
                        return true;
                    }

                    case "menu" -> {
                        if (args.length < 3) {
                            sender.sendMessage(lang("usage.admin_menu", "&cUse: &f/tower admin menu <solo|party> [player]"));
                            return true;
                        }

                        String[] forwarded = new String[args.length - 1];
                        forwarded[0] = "menu";
                        System.arraycopy(args, 2, forwarded, 1, args.length - 2);
                        return onCommand(sender, cmd, label, forwarded);
                    }

                    // =========================
                    // ✅ SETUP SYSTEM
                    // =========================

                    case "setup" -> {
                        if (!(sender instanceof Player p)) {
                            sender.sendMessage("&cApenas jogadores podem usar setup.");
                            return true;
                        }
                        if (args.length < 3) {
                            p.sendMessage("&cUse: &f/tower admin setup <dungeonId> [arena]");
                            return true;
                        }

                        String dungeonId = args[2].trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
                        if (dungeonId.isBlank()) {
                            p.sendMessage("&cID inválido.");
                            return true;
                        }

                        File dungeonsFolder = new File(plugin.getDataFolder(), "dungeons");
                        File file = new File(dungeonsFolder, dungeonId + ".yml");
                        if (!file.exists()) {
                            p.sendMessage(apply(lang("messages.dungeon_not_found", "&cDungeon não encontrada: &f{dungeon}"),
                                    Map.of("{dungeon}", dungeonId)));
                            return true;
                        }

                        int arena = 1;
                        if (args.length >= 4) {
                            arena = parseArenaIndex(args[3]);
                        }
                        if (arena < 1) arena = 1;

                        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

                        String prefix = "";
                        if (arena > 1) {
                            String key = dungeonId + "_arena" + arena;
                            if (cfg.getConfigurationSection(key) == null) {
                                cfg.createSection(key);
                            }
                            prefix = key + ".";
                        }

                        setups.put(p.getUniqueId(), new SetupContext(dungeonId, arena, prefix, file, cfg));

                        p.sendMessage(TextUtil.color("&aSetup iniciado: &f" + dungeonId + " &7(arena " + arena + ")"));
                        p.sendMessage(TextUtil.color("&7Use: &f/tower admin setspawn solo|leader &7| &f/tower admin addspawn members|solo"));
                        p.sendMessage(TextUtil.color("&7Use: &f/tower admin mobspawn add|set|clear <floor> &7| &f/tower admin setreturn"));
                        p.sendMessage(TextUtil.color("&7Finalize: &f/tower admin save &7ou &f/tower admin cancel"));
                        return true;
                    }

                    case "where" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup. Use: &f/tower admin setup <dungeonId> [arena]");
                            return true;
                        }
                        p.sendMessage(TextUtil.color("&aSetup atual: &f" + sc.dungeonId + " &7(arena " + sc.arenaIndex + ")"));
                        return true;
                    }

                    case "cancel" -> {
                        if (!(sender instanceof Player p)) return true;
                        if (setups.remove(p.getUniqueId()) != null) {
                            p.sendMessage("&eSetup cancelado (não salvou).");
                        } else {
                            p.sendMessage("&cVocê não está em setup.");
                        }
                        return true;
                    }

                    case "save" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup.");
                            return true;
                        }

                        try {
                            sc.cfg.save(sc.file);
                            plugin.getDungeonRegistry().reload();
                            setups.remove(p.getUniqueId());
                            p.sendMessage("&aSetup salvo e dungeons recarregadas.");
                        } catch (Exception e) {
                            p.sendMessage("&cFalha ao salvar: &f" + e.getMessage());
                        }
                        return true;
                    }

                    case "setreturn" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup.");
                            return true;
                        }

                        setLocationSection(sc.cfg, sc.prefix + "return_spawn", p.getLocation());
                        p.sendMessage(TextUtil.color("&aReturn spawn setado. &7(" + fmt(p.getLocation()) + ")"));
                        return true;
                    }

                    case "setspawn" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup.");
                            return true;
                        }

                        if (args.length < 3) {
                            p.sendMessage("&cUse: &f/tower admin setspawn <solo|leader>");
                            return true;
                        }

                        String which = args[2].toLowerCase(Locale.ROOT);

                        if (which.equals("solo")) {
                            List<Map<String, Object>> list = new ArrayList<>();
                            list.add(locMap(p.getLocation()));
                            sc.cfg.set(sc.prefix + "player_spawns", list);
                            p.sendMessage(TextUtil.color("&aSpawn SOLO setado. &7(" + fmt(p.getLocation()) + ")"));
                            return true;
                        }

                        if (which.equals("leader")) {
                            setLocationSection(sc.cfg, sc.prefix + "party_leader_spawn", p.getLocation());
                            p.sendMessage(TextUtil.color("&aSpawn do LEADER setado. &7(" + fmt(p.getLocation()) + ")"));
                            return true;
                        }

                        p.sendMessage("&cUse: &f/tower admin setspawn <solo|leader>");
                        return true;
                    }

                    case "addspawn" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup.");
                            return true;
                        }

                        if (args.length < 3) {
                            p.sendMessage("&cUse: &f/tower admin addspawn <solo|members>");
                            return true;
                        }

                        String which = args[2].toLowerCase(Locale.ROOT);

                        if (which.equals("solo")) {
                            List<Map<?, ?>> raw = sc.cfg.getMapList(sc.prefix + "player_spawns");
                            List<Map<String, Object>> list = new ArrayList<>();
                            for (Map<?, ?> m : raw) list.add(new HashMap<>((Map<String, Object>) m));
                            list.add(locMap(p.getLocation()));
                            sc.cfg.set(sc.prefix + "player_spawns", list);
                            p.sendMessage(TextUtil.color("&aSpawn SOLO adicionado. &7(" + fmt(p.getLocation()) + ")"));
                            return true;
                        }

                        if (which.equals("members")) {
                            List<Map<?, ?>> raw = sc.cfg.getMapList(sc.prefix + "party_member_spawns");
                            List<Map<String, Object>> list = new ArrayList<>();
                            for (Map<?, ?> m : raw) list.add(new HashMap<>((Map<String, Object>) m));
                            list.add(locMap(p.getLocation()));
                            sc.cfg.set(sc.prefix + "party_member_spawns", list);
                            p.sendMessage(TextUtil.color("&aSpawn MEMBERS adicionado. &7(" + fmt(p.getLocation()) + ")"));
                            return true;
                        }

                        p.sendMessage("&cUse: &f/tower admin addspawn <solo|members>");
                        return true;
                    }

                    case "clearspawns" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup.");
                            return true;
                        }

                        if (args.length < 3) {
                            p.sendMessage("&cUse: &f/tower admin clearspawns <solo|members>");
                            return true;
                        }

                        String which = args[2].toLowerCase(Locale.ROOT);

                        if (which.equals("solo")) {
                            sc.cfg.set(sc.prefix + "player_spawns", new ArrayList<>());
                            p.sendMessage("&eSpawns SOLO limpos.");
                            return true;
                        }

                        if (which.equals("members")) {
                            sc.cfg.set(sc.prefix + "party_member_spawns", new ArrayList<>());
                            p.sendMessage("&eSpawns MEMBERS limpos.");
                            return true;
                        }

                        p.sendMessage("&cUse: &f/tower admin clearspawns <solo|members>");
                        return true;
                    }

                    case "mobspawn" -> {
                        if (!(sender instanceof Player p)) return true;
                        SetupContext sc = setups.get(p.getUniqueId());
                        if (sc == null) {
                            p.sendMessage("&cVocê não está em setup.");
                            return true;
                        }

                        if (args.length < 4) {
                            p.sendMessage("&cUse: &f/tower admin mobspawn <add|set|clear> <floor>");
                            return true;
                        }

                        String action = args[2].toLowerCase(Locale.ROOT);

                        int floor;
                        try { floor = Integer.parseInt(args[3]); }
                        catch (Exception e) {
                            p.sendMessage("&cFloor inválido.");
                            return true;
                        }
                        if (floor < 1) floor = 1;

                        String mobsPath = sc.prefix + "floors." + floor + ".mobs";
                        List<Map<?, ?>> mobs = sc.cfg.getMapList(mobsPath);

                        if (mobs == null || mobs.isEmpty()) {
                            p.sendMessage("&cEsse andar não tem mobs configurados ainda (&f" + floor + "&c).");
                            return true;
                        }

                        List<Map<String, Object>> spawns = new ArrayList<>();
                        Object firstSpawnsObj = mobs.get(0).get("spawns");
                        if (firstSpawnsObj instanceof List<?> l) {
                            for (Object o : l) {
                                if (o instanceof Map<?, ?> mm) spawns.add(new HashMap<>((Map<String, Object>) mm));
                            }
                        }

                        if (action.equals("clear")) {
                            spawns.clear();
                        } else if (action.equals("set")) {
                            spawns.clear();
                            spawns.add(locMap(p.getLocation()));
                        } else if (action.equals("add")) {
                            spawns.add(locMap(p.getLocation()));
                        } else {
                            p.sendMessage("&cUse: &f/tower admin mobspawn <add|set|clear> <floor>");
                            return true;
                        }

                        List<Map<String, Object>> newMobList = new ArrayList<>();
                        for (Map<?, ?> mob : mobs) {
                            Map<String, Object> copy = new LinkedHashMap<>();
                            for (var e : mob.entrySet()) {
                                copy.put(String.valueOf(e.getKey()), e.getValue());
                            }
                            copy.put("spawns", spawns);
                            newMobList.add(copy);
                        }
                        sc.cfg.set(mobsPath, newMobList);

                        p.sendMessage(TextUtil.color("&aMob spawns atualizados no floor &f" + floor + "&a: &f" + spawns.size() + " &aponto(s)."));
                        return true;
                    }

                    default -> {
                        if (sender instanceof Player p) sendAdminHelp(p);
                        else sender.sendMessage(colorPlainAdminHelpConsole());
                        return true;
                    }
                }
            }

            default -> {
                if (sender instanceof Player p) sendHelp(p);
                else sender.sendMessage(colorPlainHelpConsole());
                return true;
            }
        }
    }

    // =========================
    // HELP
    // =========================

    private void sendHelp(Player p) {
        p.sendMessage(lang("help.header", "&b&lInfinityTower &7- comandos:"));
        p.sendMessage(lang("help.menu", "&f/tower menu &7- abrir menu principal"));
        p.sendMessage(lang("help.stats", "&f/tower stats &7- ver suas estatísticas"));

        // ✅ Party
        p.sendMessage(lang("help.party_header", "&b&lParty &7- comandos:"));
        p.sendMessage(lang("help.party_invite", "&f/tower party invite <player> &7- convidar para party"));
        p.sendMessage(lang("help.party_accept", "&f/tower party accept &7- aceitar convite"));
        p.sendMessage(lang("help.party_leave", "&f/tower party leave &7- sair da party"));
        p.sendMessage(lang("help.party_disband", "&f/tower party disband &7- desfazer party (líder)"));
        p.sendMessage(lang("help.party_promote", "&f/tower party promote <player> &7- passar liderança (líder)"));
        p.sendMessage(lang("help.party_kick", "&f/tower party kick <player> &7- expulsar (líder)"));

        // Dungeon
        p.sendMessage(lang("help.leave", "&f/tower leave &7- sair da dungeon"));
        p.sendMessage(lang("help.top", "&f/tower top <wins|time> <dungeonId> [limit] &7- ranking da torre"));
        p.sendMessage(lang("help.record", "&f/tower record <dungeonId> &7- recordes (player e party)"));

        if (p.hasPermission("infinitytower.admin")) {
            p.sendMessage(lang("help.admin_separator", "&8--- &cAdmin&8 ---"));
            p.sendMessage(lang("help.admin_reload", "&f/tower admin reload"));
            p.sendMessage(lang("help.admin_list", "&f/tower admin list"));
            p.sendMessage(lang("help.admin_create", "&f/tower admin create <dungeonId>"));

            // ✅ antes era “cru” (sem cor), agora força cor
            p.sendMessage(TextUtil.color("&eSetup:"));
            p.sendMessage(TextUtil.color("&f/tower admin setup <dungeonId> [arena]"));
            p.sendMessage(TextUtil.color("&f/tower admin setspawn solo|leader"));
            p.sendMessage(TextUtil.color("&f/tower admin addspawn solo|members"));
            p.sendMessage(TextUtil.color("&f/tower admin clearspawns solo|members"));
            p.sendMessage(TextUtil.color("&f/tower admin setreturn"));
            p.sendMessage(TextUtil.color("&f/tower admin mobspawn add|set|clear <floor>"));
            p.sendMessage(TextUtil.color("&f/tower admin save &7/ &f/tower admin cancel"));

            p.sendMessage(lang("help.admin_menu", "&f/tower menu <solo|party> [player]"));
            p.sendMessage(lang("help.admin_give", "&f/tower give key <player> <dungeonId> [amount]"));
            p.sendMessage(lang("help.admin_giveall", "&f/tower giveall key <dungeonId> [amount]"));
        }
    }

    private void sendAdminHelp(Player p) {
        p.sendMessage(lang("help.admin_title", "&cAdmin:"));
        p.sendMessage(lang("help.admin_reload", "&f/tower admin reload"));
        p.sendMessage(lang("help.admin_list", "&f/tower admin list"));
        p.sendMessage(lang("help.admin_create", "&f/tower admin create <dungeonId>"));

        p.sendMessage(TextUtil.color("&eSetup:"));
        p.sendMessage(TextUtil.color("&f/tower admin setup <dungeonId> [arena]"));
        p.sendMessage(TextUtil.color("&f/tower admin setspawn solo|leader"));
        p.sendMessage(TextUtil.color("&f/tower admin addspawn solo|members"));
        p.sendMessage(TextUtil.color("&f/tower admin clearspawns solo|members"));
        p.sendMessage(TextUtil.color("&f/tower admin setreturn"));
        p.sendMessage(TextUtil.color("&f/tower admin mobspawn add|set|clear <floor>"));
        p.sendMessage(TextUtil.color("&f/tower admin save &7/ &f/tower admin cancel"));

        p.sendMessage(lang("help.admin_menu", "&f/tower menu <solo|party> [player]"));
        p.sendMessage(lang("help.admin_give", "&f/tower give key <player> <dungeonId> [amount]"));
        p.sendMessage(lang("help.admin_giveall", "&f/tower giveall key <dungeonId> [amount]"));
    }

    private String colorPlainHelpConsole() {
        return lang("help.console",
                "&bInfinityTower &7- console: "
                        + "&f/tower admin <reload|list|create|menu|setup|save|cancel> "
                        + "&7| &f/tower top <wins|time> <dungeonId> [limit] "
                        + "&7| &f/tower record <dungeonId> "
                        + "&7| &f/tower give key <player> <dungeonId> [amount] "
                        + "&7| &f/tower giveall key <dungeonId> [amount]"
                        + "&7| &f/tower stats");
    }

    private String colorPlainAdminHelpConsole() {
        return lang("help.admin_console",
                "&cAdmin(console): &f/tower admin reload | list | create <id> | menu <solo|party> <player> | setup <dungeon> [arena] | save | cancel | give key <player> <dungeon> [amount] | giveall key <dungeon> [amount]");
    }

    // =========================
    // TAB COMPLETE
    // =========================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        boolean admin = sender.hasPermission("infinitytower.admin");
        boolean user = sender.hasPermission("infinitytower.player") || admin;
        if (!user) return List.of();

        if (args.length == 1) {
            List<String> root = new ArrayList<>();
            root.add("help");
            root.add("menu");
            root.add("stats");
            root.add("party");
            root.add("invite"); // alias
            root.add("accept"); // alias
            root.add("leave");  // dungeon leave
            root.add("top");
            root.add("record");
            if (admin) {
                root.add("admin");
                root.add("give");
                root.add("giveall");
            }
            return filter(root, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // ✅ party
        if (sub.equals("party")) {
            if (args.length == 2) {
                return filter(List.of("invite", "accept", "leave", "disband", "promote", "kick"), args[1]);
            }
            if (args.length == 3) {
                String a = args[1].toLowerCase(Locale.ROOT);
                if (a.equals("invite") || a.equals("promote") || a.equals("kick")) {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                    return filter(names, args[2]);
                }
            }
            return List.of();
        }

        // ======= RESTO DO SEU TAB COMPLETE ORIGINAL (intacto) =======

        if (sub.equals("top")) {
            if (args.length == 2) return filter(List.of("wins", "time"), args[1]);

            if (args.length == 3) {
                List<String> ids = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
                Collections.sort(ids);
                return filter(ids, args[2]);
            }

            if (args.length == 4) {
                return filter(List.of("5", "10", "15", "20", "30", "50"), args[3]);
            }

            return List.of();
        }

        if (sub.equals("record")) {
            if (args.length == 2) {
                List<String> ids = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
                Collections.sort(ids);
                return filter(ids, args[1]);
            }
            return List.of();
        }

        if (sub.equals("menu") && admin) {
            if (args.length == 2) return filter(List.of("solo", "party"), args[1]);
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                return filter(names, args[2]);
            }
        }

        if (sub.equals("give") && admin) {
            if (args.length == 2) return filter(List.of("key"), args[1]);

            if (args.length >= 2 && args[1].equalsIgnoreCase("key")) {
                if (args.length == 3) {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                    return filter(names, args[2]);
                }

                if (args.length == 4) {
                    List<String> ids = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
                    Collections.sort(ids);
                    return filter(ids, args[3]);
                }

                if (args.length == 5) {
                    return filter(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[4]);
                }
            }
            return List.of();
        }

        if (sub.equals("giveall") && admin) {
            if (args.length == 2) return filter(List.of("key"), args[1]);

            if (args.length >= 2 && args[1].equalsIgnoreCase("key")) {
                if (args.length == 3) {
                    List<String> ids = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
                    Collections.sort(ids);
                    return filter(ids, args[2]);
                }

                if (args.length == 4) {
                    return filter(List.of("1", "2", "3", "5", "10", "16", "32", "64"), args[3]);
                }
            }
            return List.of();
        }

        if (sub.equals("invite") && args.length == 2) {
            if (!(sender instanceof Player p)) return List.of();
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getName().equalsIgnoreCase(p.getName())) names.add(online.getName());
            }
            return filter(names, args[1]);
        }

        if (sub.equals("admin") && admin) {
            if (args.length == 2) {
                return filter(List.of(
                        "reload", "list", "create", "menu",
                        "setup", "where", "setspawn", "addspawn", "clearspawns",
                        "setreturn", "mobspawn", "save", "cancel"
                ), args[1]);
            }

            if (args.length == 3) {
                String a = args[1].toLowerCase(Locale.ROOT);

                if (a.equals("menu")) return filter(List.of("solo", "party"), args[2]);
                if (a.equals("create")) return filter(List.of("my_tower", "solo_10"), args[2]);

                if (a.equals("setup")) {
                    List<String> ids = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
                    Collections.sort(ids);
                    return filter(ids, args[2]);
                }

                if (a.equals("setspawn")) return filter(List.of("solo", "leader"), args[2]);
                if (a.equals("addspawn")) return filter(List.of("solo", "members"), args[2]);
                if (a.equals("clearspawns")) return filter(List.of("solo", "members"), args[2]);

                if (a.equals("mobspawn")) return filter(List.of("add", "set", "clear"), args[2]);
            }

            if (args.length == 4) {
                String a = args[1].toLowerCase(Locale.ROOT);

                if (a.equals("menu")) {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                    return filter(names, args[3]);
                }

                if (a.equals("setup")) {
                    return filter(List.of("1", "2", "3", "arena2", "arena3"), args[3]);
                }

                if (a.equals("mobspawn")) {
                    return filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[3]);
                }
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> base, String token) {
        if (token == null || token.isBlank()) return base;
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : base) {
            if (s.toLowerCase(Locale.ROOT).startsWith(t)) out.add(s);
        }
        return out;
    }

    // =========================
    // SETUP HELPERS
    // =========================

    private int parseArenaIndex(String raw) {
        if (raw == null) return 1;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("arena")) s = s.substring("arena".length());
        try { return Math.max(1, Integer.parseInt(s)); }
        catch (Exception ignored) { return 1; }
    }

    private void setLocationSection(YamlConfiguration cfg, String path, Location loc) {
        if (cfg == null || loc == null) return;
        cfg.set(path + ".world", loc.getWorld() == null ? "world" : loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", loc.getYaw());
        cfg.set(path + ".pitch", loc.getPitch());
    }

    private Map<String, Object> locMap(Location loc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", loc.getWorld() == null ? "world" : loc.getWorld().getName());
        m.put("x", loc.getX());
        m.put("y", loc.getY());
        m.put("z", loc.getZ());
        m.put("yaw", (double) loc.getYaw());
        m.put("pitch", (double) loc.getPitch());
        return m;
    }

    private String fmt(Location loc) {
        if (loc == null) return "null";
        String w = (loc.getWorld() == null ? "world" : loc.getWorld().getName());
        return w + " " + round(loc.getX()) + " " + round(loc.getY()) + " " + round(loc.getZ());
    }

    private String round(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    // =========================
    // LANG HELPERS
    // =========================

    private String lang(String path, String def) {
        return TextUtil.color(plugin.getLang().getString(path, def));
    }

    private String apply(String msg, Map<String, String> vars) {
        if (msg == null) return "";
        String out = msg;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }

    private String colorVar(String s) {
        return TextUtil.color(s == null ? "" : s);
    }
}