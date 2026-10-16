package fr.laugh.antwars.scoreboard;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import fr.laugh.antwars.compat.MCVersion;

/**
 * Un scoreboard latéral (sidebar) propre à un joueur, mis à jour sans
 * scintillement, compatible de la 1.8 à la 1.21.
 *
 * <p>Contrainte 1.8 : une ligne de score (le "faux joueur") est limitée à 16
 * caractères, et deux lignes identiques (ex. deux lignes vides) fusionneraient.
 * La technique universelle contourne les deux : chaque ligne est une
 * {@link Team} distincte dont l'<b>entrée</b> est un code couleur unique et
 * invisible (donc jamais de collision, même entre lignes vides), et le texte
 * visible est porté par le <b>préfixe</b> (≤16) + le <b>suffixe</b> (≤16) de la
 * team — soit jusqu'à 32 caractères affichés, la couleur de fin de préfixe étant
 * reportée sur le suffixe. Cette approche marche a l'identique sur toutes les
 * versions ; les limites plus larges des versions recentes (64 par partie des
 * la 1.13, voir partLimit) sont gerees a part.
 *
 * <p>Méthodes de scoreboard <b>dépréciées mais toujours présentes en 1.21</b>
 * (comme {@code Sign.getLines} ailleurs dans le projet) : la signature
 * {@code registerNewObjective(String, String)} — la variante à base de
 * {@code Criteria} n'existe pas en 1.8 — et {@code setDisplayName/setPrefix/
 * setSuffix(String)}, remplacées par des {@code Component} côté Paper mais dont
 * la version {@code String} reste servie. Tout est isolé ici : un seul endroit à
 * corriger si l'une disparaît.
 *
 * <p>Limite assumée sans NMS : les petits numéros rouges à droite (la valeur du
 * score) restent visibles avant la 1.20.5. Les masquer imposerait des paquets
 * propres à chaque version (interdit ici).
 */
public class Sidebar {

    private static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private final String[] entries = new String[MAX_LINES];
    private int shownLines;

    @SuppressWarnings("deprecation")
    public Sidebar(String title) {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("antwars", "dummy");
        objective.setDisplayName(cut(title, titleLimit()));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Une team + une entrée invisible unique par ligne, créées d'avance.
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            entries[i] = entry;
            Team team = board.registerNewTeam("line" + i);
            team.addEntry(entry);
        }
    }

    public void apply(Player player) {
        player.setScoreboard(board);
    }

    /** Met à jour le titre et les lignes (de haut en bas). */
    @SuppressWarnings("deprecation")
    public void update(String title, List<String> lines) {
        objective.setDisplayName(cut(title, titleLimit()));

        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            Team team = board.getTeam("line" + i);
            if (team == null) {
                continue;
            }
            setLine(team, lines.get(i));
            // Score décroissant : la premiere ligne (i=0) est tout en haut.
            objective.getScore(entries[i]).setScore(count - i);
        }
        // Lignes en trop depuis le dernier rendu : on les retire de l'affichage.
        for (int i = count; i < shownLines; i++) {
            board.resetScores(entries[i]);
        }
        shownLines = count;
    }

    /**
     * Répartit une ligne sur préfixe (≤16) et suffixe (≤16) sans couper un code
     * couleur en deux, et en reportant la couleur courante sur le suffixe.
     */
    @SuppressWarnings("deprecation")
    private void setLine(Team team, String line) {
        if (line == null) {
            line = "";
        }
        String prefix;
        String suffix;
        int part = partLimit();
        if (line.length() <= part) {
            prefix = line;
            suffix = "";
        } else {
            int at = part;
            // Ne pas scinder au milieu d'un code couleur (le caractere section
            // ne doit jamais finir seul un prefixe).
            if (line.charAt(part - 1) == ChatColor.COLOR_CHAR) {
                at = part - 1;
            }
            prefix = line.substring(0, at);
            suffix = ChatColor.getLastColors(prefix) + line.substring(at);
            if (suffix.length() > part) {
                suffix = cut(suffix, part);
            }
        }
        team.setPrefix(prefix);
        team.setSuffix(suffix);
    }

    /**
     * Longueur maximale d'un prefixe ou d'un suffixe de team : 16 jusqu'en 1.12,
     * 64 a partir de la 1.13. Sur les versions recentes, une ligne peut donc
     * afficher jusqu'a 128 caracteres au lieu de 32 : utile pour les lignes
     * personnalisees remplies par les placeholders d'autres plugins.
     */
    private static int partLimit() {
        return MCVersion.atLeast(13) ? 64 : 16;
    }

    /** Longueur maximale du titre : 32 jusqu'en 1.12, 128 a partir de la 1.13. */
    private static int titleLimit() {
        return MCVersion.atLeast(13) ? 128 : 32;
    }

    /** Coupe a {@code max} caracteres, sans laisser de code couleur orphelin a la fin. */
    private String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        return cut.charAt(cut.length() - 1) == ChatColor.COLOR_CHAR ? cut.substring(0, cut.length() - 1) : cut;
    }
}
