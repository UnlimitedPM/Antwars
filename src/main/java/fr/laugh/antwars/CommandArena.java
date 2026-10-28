package fr.laugh.antwars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.quest.QuestProgress;
import fr.laugh.antwars.quest.QuestType;
import fr.laugh.antwars.stats.PlayerStats;
import fr.laugh.antwars.stats.StatsStorage;

/**
 * Commande {@code /aw} et sa completion automatique. Les permissions sont
 * verifiees sous-commande par sous-commande, pas sur la commande entiere.
 */
public class CommandArena implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "antwars.admin";
    private static final String JOIN = "antwars.join";
    private static final String SPECTATE = "antwars.spectate";

    private static final List<String> PLAYER_SUBCOMMANDS =
            Arrays.asList("join", "leave", "list", "stats", "top", "quests", "quickbuy",
                    "spectate", "chat", "help");

    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "edit", "cancel", "create", "delete", "setlobby", "setarenalobby", "setspawn",
            "setbed", "shopnpc", "setminplayers", "setmaxplayers", "setteams", "setplayersperteam",
            "spectators", "save", "info", "start", "stop", "reload");

    private final Main main;
    private final ArenaManager arenaManager;

    public CommandArena(Main main, ArenaManager arenaManager) {
        this.main = main;
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Sans argument : un admin en jeu ouvre le menu de configuration,
            // tout le monde d'autre (ou la console) recoit l'aide.
            if (sender instanceof Player && sender.hasPermission(ADMIN)) {
                main.getConfigMenu().openList((Player) sender);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subcommand = args[0].toLowerCase();

        if ("help".equals(subcommand)) {
            sendHelp(sender);
            return true;
        }

        if ("list".equals(subcommand)) {
            listArenas(sender);
            return true;
        }

        if ("stats".equals(subcommand)) {
            String target = args.length >= 2 ? args[1]
                    : (sender instanceof Player ? sender.getName() : null);
            if (target == null) {
                send(sender, usage("stats " + Messages.tr("command.args.player")));
            } else {
                showStats(sender, target);
            }
            return true;
        }

        if ("top".equals(subcommand)) {
            showTop(sender, args.length >= 2 ? args[1].toLowerCase() : "wins");
            return true;
        }

        if ("reload".equals(subcommand)) {
            if (!checkPermission(sender, ADMIN)) {
                return true;
            }
            main.reloadConfig();
            main.getMessages().load();
            main.buildShopNpcHead();
            arenaManager.resetAll();
            arenaManager.load();
            send(sender, Messages.tr("command.reloaded"));
            return true;
        }

        // Toutes les sous-commandes suivantes agissent depuis une position ou
        // sur un joueur : elles n'ont pas de sens depuis la console.
        if (!(sender instanceof Player)) {
            send(sender, Messages.tr("command.ingame_only"));
            return true;
        }
        Player player = (Player) sender;

        if ("join".equals(subcommand)) {
            if (!checkPermission(player, JOIN)) {
                return true;
            }
            if (args.length != 2) {
                send(player, usage("join " + Messages.tr("command.args.arena")));
                return true;
            }
            joinArena(player, args[1]);
            return true;
        }

        if ("leave".equals(subcommand)) {
            Arena current = arenaManager.getArenaOf(player);
            if (current == null) {
                send(player, Messages.tr("command.not_in_arena"));
            } else {
                current.leave(player, true);
                if (main.getAutoQueue() != null) {
                    main.getAutoQueue().disable(player);
                }
                send(player, Messages.tr("command.left_arena", "arena", current.getName()));
            }
            return true;
        }

        if ("quests".equals(subcommand)) {
            showQuests(player);
            return true;
        }

        if ("spectate".equals(subcommand)) {
            if (!checkPermission(player, SPECTATE)) {
                return true;
            }
            if (args.length != 2) {
                send(player, usage("spectate " + Messages.tr("command.args.arena")));
                return true;
            }
            Arena watched = arenaManager.getArena(args[1]);
            if (watched == null) {
                send(player, Messages.tr("command.arena_not_found", "name", args[1]));
                return true;
            }
            Arena inside = arenaManager.getArenaOf(player);
            if (inside != null) {
                send(player, Messages.tr("command.already_in_arena", "arena", inside.getName()));
                return true;
            }
            String problem = watched.spectate(player);
            if (problem != null) {
                send(player, Messages.tr("command.join_failed", "reason", problem));
            } else {
                send(player, Messages.tr("spectator.joined", "arena", watched.getName()));
            }
            return true;
        }

        if ("chat".equals(subcommand)) {
            if (args.length != 2 || !("team".equalsIgnoreCase(args[1]) || "all".equalsIgnoreCase(args[1]))) {
                send(player, usage("chat <team|all>"));
                return true;
            }
            boolean all = "all".equalsIgnoreCase(args[1]);
            main.getChatListeners().setDefaultAll(player, all);
            send(player, Messages.tr(all ? "chat.mode_all" : "chat.mode_team", "prefix", main.getChatAllPrefix()));
            return true;
        }

        if ("quickbuy".equals(subcommand)) {
            Arena current = arenaManager.getArenaOf(player);
            if (current != null && current.isRunning()) {
                send(player, Messages.tr("shop.customize_in_game"));
            } else {
                main.getShopMenu().openCustomize(player);
            }
            return true;
        }

        if (!checkPermission(player, ADMIN)) {
            return true;
        }

        // Menu graphique de configuration : sans nom d'arene il ouvre la liste,
        // avec un nom il edite directement cette arene.
        if ("edit".equals(subcommand)) {
            if (args.length >= 2) {
                Arena target = arenaManager.getArena(args[1]);
                if (target == null) {
                    send(player, Messages.tr("command.arena_not_found", "name", args[1]));
                } else {
                    main.getConfigMenu().openEdit(player, target.getName());
                }
            } else {
                main.getConfigMenu().openList(player);
            }
            return true;
        }

        if ("cancel".equals(subcommand)) {
            if (main.getPlacements().isPlacing(player)) {
                main.getPlacements().cancel(player);
            } else if (main.getArenaNaming().cancel(player)) {
                // Saisie du nom d'une arene annulee (message deja envoye).
                return true;
            } else {
                send(player, Messages.tr("config.place.not_placing"));
            }
            return true;
        }

        if ("setlobby".equals(subcommand)) {
            main.setServerLobby(player.getLocation());
            send(player, Messages.tr("command.server_lobby_set"));
            return true;
        }

        if ("create".equals(subcommand)) {
            if (args.length != 2) {
                send(player, usage("create " + Messages.tr("command.args.arena")));
            } else if (!arenaManager.createArena(args[1])) {
                send(player, Messages.tr("command.arena_exists"));
            } else {
                send(player, Messages.tr("command.arena_created", "name", args[1]));
            }
            return true;
        }

        if ("delete".equals(subcommand) || "deletearena".equals(subcommand)) {
            if (args.length != 2) {
                send(player, usage("delete " + Messages.tr("command.args.arena")));
            } else if (!arenaManager.deleteArena(args[1])) {
                send(player, Messages.tr("command.arena_none_named"));
            } else {
                send(player, Messages.tr("command.arena_deleted", "name", args[1]));
            }
            return true;
        }

        // Les sous-commandes restantes prennent toutes un nom d'arene.
        if (args.length < 2) {
            send(player, usage(subcommand + " " + Messages.tr("command.args.arena_etc")));
            return true;
        }
        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            send(player, Messages.tr("command.arena_not_found", "name", args[1]));
            return true;
        }

        if ("spectators".equals(subcommand)) {
            if (args.length != 3 || !("on".equalsIgnoreCase(args[2]) || "off".equalsIgnoreCase(args[2]))) {
                send(player, usage("spectators " + Messages.tr("command.args.arena") + " <on|off>"));
                return true;
            }
            arena.setAllowSpectators("on".equalsIgnoreCase(args[2]));
            arenaManager.save();
            send(player, Messages.tr(arena.isAllowSpectators() ? "command.spectators_on" : "command.spectators_off",
                    "arena", arena.getName()));
            return true;
        }

        if ("setarenalobby".equals(subcommand)) {
            arena.setLobby(player.getLocation());
            arenaManager.save();
            send(player, Messages.tr("command.arena_lobby_set", "arena", arena.getName()));
            return true;
        }

        if ("setspawn".equals(subcommand)) {
            if (args.length != 3) {
                send(player, usage("setspawn " + Messages.tr("command.args.arena_team")));
                return true;
            }
            String teamName = Arena.matchTeamName(args[2]);
            if (teamName == null) {
                send(player, Messages.tr("command.unknown_team",
                        "teams", Arrays.toString(Arena.TEAM_NAMES)));
                return true;
            }
            arena.setSpawnPoint(teamName, player.getLocation());
            arenaManager.save();
            send(player, Messages.tr("command.spawn_set",
                    "color", Arena.colorOf(teamName), "team", teamName));
            return true;
        }

        if ("setbed".equals(subcommand)) {
            if (args.length != 3) {
                send(player, usage("setbed " + Messages.tr("command.args.arena_team")));
                return true;
            }
            String teamName = Arena.matchTeamName(args[2]);
            if (teamName == null) {
                send(player, Messages.tr("command.unknown_team",
                        "teams", Arrays.toString(Arena.TEAM_NAMES)));
                return true;
            }
            // L'admin pose un vrai lit puis se place dessus : on enregistre ses
            // deux moities. Capturer un lit reel nous donne son orientation
            // exacte, qu'on ne saurait pas reconstruire par code sur 1.13+.
            Block foot = Beds.findBedNear(player);
            if (foot == null) {
                send(player, Messages.tr("command.no_bed_found"));
                return true;
            }
            Block head = Beds.otherHalf(foot);
            if (head == null) {
                send(player, Messages.tr("command.bed_incomplete"));
                return true;
            }
            arena.setBed(teamName, foot.getLocation(), head.getLocation());
            arena.getBeds().captureHome(teamName, foot, head);
            arenaManager.save();
            send(player, Messages.tr("command.bed_set",
                    "color", Arena.colorOf(teamName), "team", teamName));
            return true;
        }

        if ("shopnpc".equals(subcommand)) {
            if (args.length == 3 && "clear".equalsIgnoreCase(args[2])) {
                arena.clearShopNpcs();
                arenaManager.forgetPendingNpcs(arena.getName());
                arenaManager.save();
                send(player, Messages.tr("command.shopnpc_cleared", "arena", arena.getName()));
                return true;
            }
            arena.addShopNpc(player.getLocation());
            arenaManager.save();
            // Apercu immediat si une partie tourne (le spawn dedoublonne).
            if (arena.isRunning()) {
                arena.spawnShopNpcs();
            }
            send(player, Messages.tr("command.shopnpc_added", "arena", arena.getName()));
            return true;
        }

        if ("setminplayers".equals(subcommand) || "setmaxplayers".equals(subcommand)
                || "setteams".equals(subcommand) || "setplayersperteam".equals(subcommand)) {
            if (args.length != 3) {
                send(player, usage(subcommand + " " + Messages.tr("command.args.arena_number")));
                return true;
            }
            Integer value = parseInt(player, args[2]);
            if (value == null) {
                return true;
            }
            applyNumericSetting(player, arena, subcommand, value.intValue());
            return true;
        }

        if ("save".equals(subcommand)) {
            String problem = arena.validate();
            if (problem != null) {
                send(player, Messages.tr("command.arena_incomplete", "problem", problem));
                return true;
            }
            arenaManager.save();
            send(player, Messages.tr("command.arena_saved", "arena", arena.getName()));
            return true;
        }

        if ("info".equals(subcommand)) {
            sendInfo(player, arena);
            return true;
        }

        if ("start".equals(subcommand)) {
            startArena(player, arena);
            return true;
        }

        if ("stop".equals(subcommand)) {
            if (!arena.isRunning() && !arena.isState(State.STARTING)) {
                send(player, Messages.tr("command.no_game_running"));
            } else {
                arena.end(null);
                send(player, Messages.tr("command.game_stopped"));
            }
            return true;
        }

        send(player, Messages.tr("command.unknown_subcommand"));
        return true;
    }

    // ================= Actions =================

    private void joinArena(Player player, String arenaName) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            send(player, Messages.tr("command.arena_not_found", "name", arenaName));
            return;
        }
        Arena current = arenaManager.getArenaOf(player);
        if (current != null) {
            send(player, Messages.tr("command.already_in_arena", "arena", current.getName()));
            return;
        }
        if (main.getAutoQueue() != null) {
            main.getAutoQueue().enable(player);
            main.getAutoQueue().remove(player);
        }
        String problem = arena.join(player);
        if (problem != null) {
            send(player, Messages.tr("command.join_failed", "reason", problem));
        } else {
            send(player, Messages.tr("command.joined_arena", "arena", arena.getName()));
        }
    }

    private void startArena(Player player, Arena arena) {
        if (arena.isRunning() || arena.isState(State.FINISH)) {
            send(player, Messages.tr("command.game_already_running"));
            return;
        }
        String problem = arena.validate();
        if (problem != null) {
            send(player, Messages.tr("command.arena_incomplete", "problem", problem));
            return;
        }
        if (arena.getPlayerCount() < 2) {
            send(player, Messages.tr("command.need_two_players"));
            return;
        }
        arena.start();
        send(player, Messages.tr("command.game_started", "arena", arena.getName()));
    }

    private void applyNumericSetting(Player player, Arena arena, String subcommand, int value) {
        if (value <= 0) {
            send(player, Messages.tr("command.value_positive"));
            return;
        }
        if ("setminplayers".equals(subcommand)) {
            arena.setMinPlayers(value);
            send(player, Messages.tr("command.min_players_set", "value", value));
        } else if ("setmaxplayers".equals(subcommand)) {
            arena.setMaxPlayers(value);
            send(player, Messages.tr("command.max_players_set", "value", value));
        } else if ("setteams".equals(subcommand)) {
            if (value < 2 || value > 4) {
                send(player, Messages.tr("command.teams_range"));
                return;
            }
            arena.setTeamCount(value);
            send(player, Messages.tr("command.teams_set", "value", value));
        } else {
            arena.setPlayersPerTeam(value);
            send(player, Messages.tr("command.players_per_team_set", "value", value));
        }
        arenaManager.save();
    }

    private void listArenas(CommandSender sender) {
        List<String> names = arenaManager.getArenaNames();
        if (names.isEmpty()) {
            send(sender, Messages.tr("command.no_arenas"));
            return;
        }
        send(sender, Messages.tr("command.arenas_header", "count", names.size()));
        for (Arena arena : arenaManager.getArenas()) {
            String status = arena.isConfigured()
                    ? ChatColor.GREEN + arena.getState().name()
                    : ChatColor.RED + Messages.tr("command.status_incomplete");
            sender.sendMessage(Messages.tr("command.arena_list_entry",
                    "name", arena.getName(), "status", status,
                    "current", arena.getPlayerCount(), "max", arena.getMaxPlayers()));
        }
    }

    private void sendInfo(Player player, Arena arena) {
        send(player, Messages.tr("command.info_header", "arena", arena.getName()));
        player.sendMessage(Messages.tr("command.info_state", "state", arena.getState()));
        player.sendMessage(Messages.tr("command.info_players",
                "current", arena.getPlayerCount(), "max", arena.getMaxPlayers(),
                "min", arena.getMinPlayers()));
        player.sendMessage(Messages.tr("command.info_teams",
                "teams", arena.getTeamCount(), "per", arena.getPlayersPerTeam()));

        Location lobby = arena.getLobby();
        player.sendMessage(Messages.tr("command.info_lobby", "value", lobby == null
                ? ChatColor.RED + Messages.tr("command.undefined")
                : ChatColor.WHITE + format(lobby)));

        for (String teamName : Arena.TEAM_NAMES) {
            Map<String, Location> spawns = arena.getSpawnPoints();
            Location spawn = spawns.get(teamName);
            String bedState = arena.hasBed(teamName)
                    ? Messages.tr("command.info_bed_ok")
                    : Messages.tr("command.info_bed_undefined");
            player.sendMessage(Messages.tr("command.info_spawn",
                    "color", Arena.colorOf(teamName), "team", teamName,
                    "value", spawn == null
                            ? ChatColor.DARK_GRAY + Messages.tr("command.undefined")
                            : ChatColor.WHITE + format(spawn),
                    "bed", bedState));
        }

        player.sendMessage(Messages.tr("command.info_npc",
                "count", arena.getShopNpcLocations().size()));

        String problem = arena.validate();
        player.sendMessage(problem == null
                ? Messages.tr("command.info_ready")
                : Messages.tr("command.info_problem", "problem", problem));
    }

    private String format(Location location) {
        return location.getWorld().getName() + " " + Math.round(location.getX()) + ", "
                + Math.round(location.getY()) + ", " + Math.round(location.getZ());
    }

    // ================= Statistiques =================

    private void showStats(final CommandSender sender, String name) {
        // Un joueur connecte : on prefere les valeurs vives du cache (la partie
        // en cours n'est ecrite qu'a la fin).
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerStats cached = main.getStatsManager().getCached(online.getUniqueId());
            if (cached != null) {
                displayStats(sender, cached);
                return;
            }
        }
        main.getStatsManager().lookupByName(name, new Consumer<PlayerStats>() {
            @Override
            public void accept(PlayerStats stats) {
                if (stats == null) {
                    send(sender, Messages.tr("command.no_stats_player"));
                } else {
                    displayStats(sender, stats);
                }
            }
        });
    }

    private void displayStats(CommandSender sender, PlayerStats stats) {
        send(sender, Messages.tr("command.stats_header", "name", stats.getName()));
        sender.sendMessage(Messages.tr("command.stats_games", "games", stats.getGames()));
        sender.sendMessage(Messages.tr("command.stats_wins_losses",
                "wins", stats.getWins(), "losses", stats.getLosses()));
        sender.sendMessage(Messages.tr("command.stats_kd",
                "kills", stats.getKills(), "deaths", stats.getDeaths(),
                "kd", formatKd(stats.kd())));
        sender.sendMessage(Messages.tr("command.stats_rating", "rating", stats.getRating()));
    }

    // ================= Quetes du jour =================

    private void showQuests(Player player) {
        if (!main.isQuestsEnabled()) {
            send(player, Messages.tr("command.quests_disabled"));
            return;
        }
        QuestProgress progress = main.getQuestManager().getProgress(player);
        send(player, Messages.tr("command.quests_header"));
        for (QuestType type : QuestType.values()) {
            boolean done = progress.isComplete(type);
            String mark = done ? Messages.tr("command.quests_done_mark")
                    : Messages.tr("command.quests_todo_mark");
            player.sendMessage(Messages.tr("command.quests_line",
                    "mark", mark, "quest", Messages.tr(type.getLabel()),
                    "current", progress.get(type), "target", type.getTarget()));
        }
    }

    private void showTop(final CommandSender sender, final String stat) {
        if (!StatsStorage.isColumn(stat)) {
            send(sender, Messages.tr("command.unknown_stat",
                    "choices", Arrays.toString(StatsStorage.COLUMNS)));
            return;
        }
        main.getStatsManager().top(stat, 10, new Consumer<List<PlayerStats>>() {
            @Override
            public void accept(List<PlayerStats> list) {
                if (list.isEmpty()) {
                    send(sender, Messages.tr("command.no_stats"));
                    return;
                }
                send(sender, Messages.tr("command.top_header", "stat", stat));
                int rank = 1;
                for (PlayerStats stats : list) {
                    sender.sendMessage(Messages.tr("command.top_entry",
                            "rank", rank, "name", stats.getName(),
                            "value", statValue(stats, stat)));
                    rank++;
                }
            }
        });
    }

    private static int statValue(PlayerStats stats, String column) {
        if ("wins".equals(column)) {
            return stats.getWins();
        }
        if ("losses".equals(column)) {
            return stats.getLosses();
        }
        if ("kills".equals(column)) {
            return stats.getKills();
        }
        if ("deaths".equals(column)) {
            return stats.getDeaths();
        }
        if ("rating".equals(column)) {
            return stats.getRating();
        }
        return stats.getGames();
    }

    /** Formate le K/D en deux decimales, point decimal force (pas la virgule FR). */
    private static String formatKd(double kd) {
        return String.format(java.util.Locale.US, "%.2f", kd);
    }

    private void sendHelp(CommandSender sender) {
        send(sender, Messages.tr("command.help_header"));
        sender.sendMessage(Messages.tr("command.help_join"));
        sender.sendMessage(Messages.tr("command.help_leave"));
        sender.sendMessage(Messages.tr("command.help_list"));
        sender.sendMessage(Messages.tr("command.help_stats"));
        sender.sendMessage(Messages.tr("command.help_top"));
        sender.sendMessage(Messages.tr("command.help_quests"));
        sender.sendMessage(Messages.tr("command.help_quickbuy"));
        sender.sendMessage(Messages.tr("command.help_spectate"));
        sender.sendMessage(Messages.tr("command.help_chat"));
        if (!sender.hasPermission(ADMIN)) {
            return;
        }
        sender.sendMessage(Messages.tr("command.help_admin_header"));
        sender.sendMessage(Messages.tr("command.help_edit"));
        sender.sendMessage(Messages.tr("command.help_setlobby"));
        sender.sendMessage(Messages.tr("command.help_create_delete"));
        sender.sendMessage(Messages.tr("command.help_setarenalobby"));
        sender.sendMessage(Messages.tr("command.help_setspawn"));
        sender.sendMessage(Messages.tr("command.help_setbed"));
        sender.sendMessage(Messages.tr("command.help_shopnpc"));
        sender.sendMessage(Messages.tr("command.help_setteams"));
        sender.sendMessage(Messages.tr("command.help_spectators"));
        sender.sendMessage(Messages.tr("command.help_setplayers"));
        sender.sendMessage(Messages.tr("command.help_save"));
        sender.sendMessage(Messages.tr("command.help_reload"));
    }

    // ================= Utilitaires =================

    private void send(CommandSender sender, String message) {
        sender.sendMessage(Main.PREFIX + message);
    }

    private String usage(String form) {
        return Messages.tr("command.usage", "form", form);
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, Messages.tr("command.no_permission", "permission", permission));
        return false;
    }

    /**
     * Analyse un entier sans jamais lever d'exception : une faute de frappe ne
     * doit pas remplir la console d'erreurs.
     */
    private Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.valueOf(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            send(sender, Messages.tr("command.not_integer", "value", raw));
            return null;
        }
    }

    // ================= Completion automatique =================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission(ADMIN)) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList(StatsStorage.COLUMNS), args[1]);
        }

        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return filter(onlinePlayerNames(), args[1]);
        }

        if (args.length == 2 && !"setlobby".equalsIgnoreCase(args[0])
                && !"create".equalsIgnoreCase(args[0])) {
            return filter(arenaManager.getArenaNames(), args[1]);
        }

        if (args.length == 3 && ("setspawn".equalsIgnoreCase(args[0])
                || "setbed".equalsIgnoreCase(args[0]))) {
            return filter(Arrays.asList(Arena.TEAM_NAMES), args[2]);
        }

        if (args.length == 3 && "shopnpc".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("clear"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> matches = new ArrayList<String>();
        String lower = prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                matches.add(option);
            }
        }
        Collections.sort(matches);
        return matches;
    }
}
