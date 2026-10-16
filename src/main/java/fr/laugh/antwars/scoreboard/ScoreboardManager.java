package fr.laugh.antwars.scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.State;
import fr.laugh.antwars.api.PapiBridge;
import fr.laugh.antwars.api.PlaceholderValues;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.task.GameCycle;

/**
 * Gère le scoreboard latéral façon BedWars : un par joueur en partie, rafraîchi
 * chaque seconde, <b>entièrement personnalisable</b>.
 *
 * <p>La mise en page est une liste de lignes, de haut en bas : celle de
 * {@code scoreboard.lines} dans config.yml si elle est renseignée, sinon la
 * disposition par défaut du fichier de langue ({@code scoreboard.lines}). Voir
 * {@link Sidebar} pour la mécanique cross-version.
 *
 * <p>Dans une ligne : les placeholders {@code %antwars_...%} (qui marchent sans
 * PlaceholderAPI, voir {@link PlaceholderValues}), ceux de n'importe quel autre
 * plugin si PAPI est là, et trois lignes spéciales, seules sur leur ligne :
 * {@code {teams}} (une ligne par équipe), {@code {timer}} (compte à rebours des
 * bordures) et {@code {footer}}.
 *
 * <p>Le statut d'une équipe suit une règle voulue par le proprietaire : coche
 * tant que le lit est intact, puis le nombre de survivants une fois le lit
 * détruit, enfin une croix quand l'équipe est éliminée. La couleur du joueur
 * porte la mention « VOUS ».
 *
 * <p>Attache et détache paresseux : chaque tick on (re)crée le scoreboard des
 * joueurs d'une partie en cours et on rend son scoreboard normal à quiconque
 * n'en est plus. Aucun accrochage au cycle de partie n'est donc nécessaire.
 */
public class ScoreboardManager {

    private static final String TEAMS = "{teams}";
    private static final String TIMER = "{timer}";
    private static final String FOOTER = "{footer}";

    private final Main main;
    private final Map<UUID, Sidebar> sidebars = new HashMap<UUID, Sidebar>();

    public ScoreboardManager(Main main) {
        this.main = main;
    }

    /** Rafraîchit tous les scoreboards (appelé chaque seconde). */
    public void updateAll() {
        if (Bukkit.getScoreboardManager() == null) {
            return; // aucun monde charge : rien a afficher.
        }
        if (!main.getConfig().getBoolean("scoreboard.enabled", true)) {
            clearAll();
            return;
        }

        // Modele relu a chaque rafraichissement : un /aw reload s'applique aussitot.
        String titleTemplate = titleTemplate();
        List<String> template = linesTemplate();
        Set<UUID> seen = new HashSet<UUID>();

        for (Arena arena : main.getArenaManager().getArenas()) {
            if (!arena.isRunning()) {
                continue; // isRunning() = PLAYING ou DEATHMATCH.
            }
            // Joueurs ET spectateurs : un spectateur voit la partie qu'il regarde.
            for (Player player : arena.audience()) {
                seen.add(player.getUniqueId());
                String title = resolve(player, titleTemplate);
                Sidebar sidebar = sidebars.get(player.getUniqueId());
                if (sidebar == null) {
                    sidebar = new Sidebar(title);
                    sidebars.put(player.getUniqueId(), sidebar);
                    sidebar.apply(player);
                }
                sidebar.update(title, render(arena, player, template));
            }
        }

        Iterator<Map.Entry<UUID, Sidebar>> it = sidebars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Sidebar> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                resetBoard(Bukkit.getPlayer(entry.getKey()));
                it.remove();
            }
        }
    }

    /** Retire immédiatement le scoreboard d'un joueur (fin de partie, /aw leave). */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        if (sidebars.remove(player.getUniqueId()) != null) {
            resetBoard(player);
        }
    }

    /** Retire tous les scoreboards (arret du plugin, scoreboard desactive). */
    public void clearAll() {
        for (UUID id : sidebars.keySet()) {
            resetBoard(Bukkit.getPlayer(id));
        }
        sidebars.clear();
    }

    private void resetBoard(Player player) {
        if (player != null && player.isOnline() && Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ================= Modèle (config ou langue) =================

    private String titleTemplate() {
        String custom = main.getConfig().getString("scoreboard.title", "");
        return custom == null || custom.trim().isEmpty() ? Messages.tr("scoreboard.title") : custom;
    }

    private List<String> linesTemplate() {
        List<String> custom = main.getConfig().getStringList("scoreboard.lines");
        return custom == null || custom.isEmpty() ? Messages.trList("scoreboard.lines") : custom;
    }

    // ================= Construction des lignes =================

    private List<String> render(Arena arena, Player viewer, List<String> template) {
        List<String> lines = new ArrayList<String>();
        for (String raw : template) {
            String marker = raw == null ? "" : raw.trim();
            if (TEAMS.equalsIgnoreCase(marker)) {
                addTeamLines(lines, arena, viewer);
            } else if (TIMER.equalsIgnoreCase(marker)) {
                if (arena.isState(State.DEATHMATCH)) {
                    lines.add(Messages.tr("scoreboard.border_active"));
                } else {
                    lines.add(Messages.tr("scoreboard.border_time",
                            "time", GameCycle.clock(arena.getDeathmatchTimer())));
                }
            } else if (FOOTER.equalsIgnoreCase(marker)) {
                String footer = main.getConfig().getString("scoreboard.footer", "");
                if (footer != null && !footer.isEmpty()) {
                    lines.add(Messages.tr("scoreboard.footer", "footer", footer));
                }
            } else {
                lines.add(resolve(viewer, raw));
            }
        }
        // Lignes vides en bas (typiquement : footer vide masque) : on les retire
        // pour ne pas laisser un trou sous le scoreboard.
        while (!lines.isEmpty() && ChatColor.stripColor(lines.get(lines.size() - 1)).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void addTeamLines(List<String> lines, Arena arena, Player viewer) {
        String myTeam = arena.getTeam(viewer);
        for (String team : arena.usableTeams()) {
            String you = team.equals(myTeam) ? Messages.tr("scoreboard.you_suffix") : "";
            lines.add(Messages.tr("scoreboard.team_line",
                    "color", Arena.colorOf(team), "letter", team.substring(0, 1),
                    "team", team, "status", status(arena, team), "you", you));
        }
    }

    /** Statut d'une equipe : coche, nombre de survivants, ou croix. */
    private String status(Arena arena, String team) {
        int alive = arena.aliveCount(team);
        if (alive == 0) {
            return Messages.tr("scoreboard.status.dead");
        }
        if (arena.getBeds().isDestroyed(team)) {
            return Messages.tr("scoreboard.status.alive", "count", alive);
        }
        return Messages.tr("scoreboard.status.bed");
    }

    /**
     * Remplit une ligne libre : nos placeholders d'abord (sans PAPI), puis ceux
     * des autres plugins si PlaceholderAPI est la, enfin les couleurs {@code &}
     * (en dernier, pour colorer aussi ce qu'ont renvoye les placeholders).
     */
    private String resolve(Player viewer, String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String text = PlaceholderValues.replace(main, viewer, line);
        if (main.hasPlaceholderApi() && text.indexOf('%') >= 0) {
            // Garde d'isolation : PapiBridge n'est jamais appele sans PAPI.
            text = PapiBridge.apply(viewer, text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
