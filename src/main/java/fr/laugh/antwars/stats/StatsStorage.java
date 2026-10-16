package fr.laugh.antwars.stats;

import java.util.List;
import java.util.UUID;

/**
 * Stockage des statistiques, abstrait pour rester interchangeable entre le
 * fichier local (YAML) et une base MySQL. Toutes ces methodes sont appelees
 * exclusivement sur le thread d'I/O de {@link StatsManager} : une seule
 * implementation, un seul thread, donc aucune synchronisation a prevoir ici.
 */
public interface StatsStorage {

    /** Les colonnes suivies, ordre stable. Sert aussi de liste blanche. */
    String[] COLUMNS = { "games", "wins", "losses", "kills", "deaths", "rating" };

    /** Vrai si {@code column} est une colonne de stat connue (anti-injection). */
    static boolean isColumn(String column) {
        for (String c : COLUMNS) {
            if (c.equals(column)) {
                return true;
            }
        }
        return false;
    }

    /** Prepare le stockage (fichier ou table). Peut lever en cas de probleme. */
    void init() throws Exception;

    /** Stats d'un joueur, ou des stats a zero si le joueur est inconnu. */
    PlayerStats load(UUID uuid, String name);

    /** Stats d'un joueur retrouve par son nom, ou {@code null} s'il est inconnu. */
    PlayerStats loadByName(String name);

    /** Ecrit (insere ou met a jour) les stats d'un joueur. */
    void save(PlayerStats stats);

    /**
     * Meilleurs joueurs pour une colonne donnee, ordre decroissant.
     *
     * @param column une des {@link #COLUMNS} (deja validee par l'appelant)
     */
    List<PlayerStats> top(String column, int limit);

    /** Ferme les ressources (connexion, fichier). */
    void close();
}
