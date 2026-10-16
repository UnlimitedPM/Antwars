package fr.laugh.antwars.api;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.game.GameStats;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.quest.QuestProgress;
import fr.laugh.antwars.quest.QuestType;
import fr.laugh.antwars.stats.PlayerStats;
import fr.laugh.antwars.task.GameCycle;

/**
 * Valeur des placeholders {@code %antwars_...%}, SANS aucune reference a
 * PlaceholderAPI : c'est ce qui permet aux lignes du scoreboard de s'en servir
 * meme sur un serveur qui n'a pas PAPI. L'expansion PAPI ({@link Placeholders})
 * n'est qu'une fine couche qui renvoie ici.
 *
 * <p>Placeholders (apres le prefixe {@code antwars_}) :
 * <ul>
 *   <li>carriere : {@code kills deaths wins losses games rating kd}</li>
 *   <li>quetes : {@code quests_done quests_total quest_win_games quest_get_kills
 *       quest_destroy_beds} (progression "x/y")</li>
 *   <li>partie : {@code arena state team team_color players maxplayers time ingame}</li>
 *   <li>cette partie : {@code game_kills game_deaths game_beds}</li>
 *   <li>divers : {@code date}</li>
 * </ul>
 */
public final class PlaceholderValues {

    private static final Pattern PLACEHOLDER = Pattern.compile("%antwars_([A-Za-z0-9_]+)%");

    private PlaceholderValues() {
    }

    /**
     * Remplace tous les {@code %antwars_...%} d'un texte. Un placeholder
     * inconnu est laisse tel quel (comme le fait PlaceholderAPI).
     */
    public static String replace(Main main, Player player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        // StringBuffer et non StringBuilder : Matcher.appendReplacement(StringBuilder)
        // n'existe qu'a partir de Java 9.
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = resolve(main, player, matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Valeur d'un placeholder (la partie apres {@code antwars_}), ou {@code null}
     * s'il est inconnu.
     */
    public static String resolve(Main main, OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);

        if ("date".equals(key)) {
            // Nouvelle instance a chaque appel : SimpleDateFormat n'est pas
            // thread-safe, et PAPI peut nous appeler depuis un thread asynchrone
            // (plugins de tab ou de scoreboard).
            return new SimpleDateFormat("dd/MM/yy").format(new Date());
        }

        // --- Statistiques de carriere (cache) ---
        PlayerStats stats = main.getStatsManager().getCached(player.getUniqueId());
        if ("kd".equals(key)) {
            return stats == null ? "0.00" : String.format(Locale.US, "%.2f", stats.kd());
        }
        if ("kills".equals(key)) {
            return stats == null ? "0" : String.valueOf(stats.getKills());
        }
        if ("deaths".equals(key)) {
            return stats == null ? "0" : String.valueOf(stats.getDeaths());
        }
        if ("wins".equals(key)) {
            return stats == null ? "0" : String.valueOf(stats.getWins());
        }
        if ("losses".equals(key)) {
            return stats == null ? "0" : String.valueOf(stats.getLosses());
        }
        if ("games".equals(key)) {
            return stats == null ? "0" : String.valueOf(stats.getGames());
        }
        if ("rating".equals(key)) {
            return stats == null ? "1000" : String.valueOf(stats.getRating());
        }

        // --- Quetes du jour ---
        if ("quests_done".equals(key) || "quests_total".equals(key) || key.startsWith("quest_")) {
            return questValue(main, player, key);
        }

        // --- Partie en cours (joueur connecte) ---
        Player online = player.getPlayer();
        Arena arena = online == null ? null : main.getArenaManager().getArenaOf(online);
        String team = arena == null ? null : arena.getTeam(online);

        if ("ingame".equals(key)) {
            return yesNo(arena != null && arena.isRunning());
        }
        if (arena == null) {
            // Toutes les cles restantes concernent une arene : vide hors partie,
            // mais null si la cle n'existe pas du tout.
            return isArenaKey(key) ? "" : null;
        }

        if ("arena".equals(key)) {
            return arena.getName();
        }
        if ("state".equals(key)) {
            return arena.getState().name();
        }
        if ("team".equals(key)) {
            return team == null ? "" : team;
        }
        if ("team_color".equals(key)) {
            return team == null ? "" : Arena.colorOf(team) + team;
        }
        if ("players".equals(key)) {
            return String.valueOf(arena.getPlayerCount());
        }
        if ("maxplayers".equals(key)) {
            return String.valueOf(arena.getMaxPlayers());
        }
        if ("time".equals(key)) {
            return GameCycle.clock(arena.getDeathmatchTimer());
        }
        if ("game_kills".equals(key) || "game_deaths".equals(key) || "game_beds".equals(key)) {
            GameStats game = arena.getGameStats(online);
            if (game == null) {
                return "0";
            }
            int value = "game_kills".equals(key) ? game.getKills()
                    : "game_deaths".equals(key) ? game.getDeaths() : game.getBeds();
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * Progression des quetes du jour. Uniquement sur le thread principal : lire
     * la progression la remet a zero si le jour a change, ce qui n'est pas sur
     * depuis le thread asynchrone que PAPI peut utiliser (valeur vide alors).
     */
    private static String questValue(Main main, OfflinePlayer player, String key) {
        boolean known = "quests_done".equals(key) || "quests_total".equals(key) || questOf(key) != null;
        if (!known) {
            return null;
        }
        Player online = player.getPlayer();
        if (online == null || !main.isQuestsEnabled() || !Bukkit.isPrimaryThread()) {
            return "";
        }
        QuestProgress progress = main.getQuestManager().getProgress(online);
        if ("quests_total".equals(key)) {
            return String.valueOf(QuestType.values().length);
        }
        if ("quests_done".equals(key)) {
            int done = 0;
            for (QuestType type : QuestType.values()) {
                if (progress.isComplete(type)) {
                    done++;
                }
            }
            return String.valueOf(done);
        }
        QuestType type = questOf(key);
        return progress.get(type) + "/" + type.getTarget();
    }

    private static QuestType questOf(String key) {
        for (QuestType type : QuestType.values()) {
            if (key.equals("quest_" + type.name().toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }

    /** "oui" / "non" dans la langue du serveur (cles placeholder.yes / placeholder.no). */
    private static String yesNo(boolean value) {
        return Messages.tr(value ? "placeholder.yes" : "placeholder.no");
    }

    private static boolean isArenaKey(String key) {
        return "arena".equals(key) || "state".equals(key) || "team".equals(key)
                || "team_color".equals(key) || "players".equals(key) || "maxplayers".equals(key)
                || "time".equals(key) || "game_kills".equals(key) || "game_deaths".equals(key)
                || "game_beds".equals(key);
    }
}
