package fr.laugh.antwars.api;

import java.util.UUID;

import org.bukkit.entity.Player;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.State;
import fr.laugh.antwars.stats.PlayerStats;

/**
 * Point d'entree public d'AntWars pour les autres plugins.
 *
 * <p>Usage cote plugin tiers (avec un {@code softdepend: [AntWars]}) :
 * <pre>
 * PlayerStats s = AntWarsAPI.get().getStats(uuid);
 * boolean playing = AntWarsAPI.get().isInGame(player);
 * </pre>
 *
 * <p>Contrat de stabilite : on ne rend jamais nos objets internes vivants. Les
 * statistiques sortent en {@link PlayerStats#copy() copie} (modifiables sans
 * effet chez nous), l'etat d'arene sort en valeurs simples (nom, {@link State},
 * equipe). Rien ici ne laisse un plugin tiers casser une partie en cours.
 *
 * <p>Les stats renvoyees sont celles du <b>cache</b> (joueurs connectes) : le
 * chargement etant asynchrone, un joueur hors ligne renvoie {@code null}.
 */
public final class AntWarsAPI {

    private static AntWarsAPI instance;

    private final Main main;

    private AntWarsAPI(Main main) {
        this.main = main;
    }

    public static void init(Main main) {
        instance = new AntWarsAPI(main);
    }

    /** Instance de l'API, ou {@code null} si AntWars n'est pas encore active. */
    public static AntWarsAPI get() {
        return instance;
    }

    // ================= Statistiques =================

    /**
     * Copie des stats d'un joueur connecte, ou {@code null} s'il est hors ligne
     * (stats non chargees en cache).
     */
    public PlayerStats getStats(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerStats stats = main.getStatsManager().getCached(uuid);
        return stats == null ? null : stats.copy();
    }

    public PlayerStats getStats(Player player) {
        return player == null ? null : getStats(player.getUniqueId());
    }

    // ================= Etat de jeu =================

    /** Vrai si le joueur est dans une arene dont la partie est en cours. */
    public boolean isInGame(Player player) {
        Arena arena = arenaOf(player);
        return arena != null && arena.isRunning();
    }

    /** Nom de l'arene du joueur, ou {@code null} s'il n'est dans aucune. */
    public String getArenaName(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? null : arena.getName();
    }

    /** Etat de l'arene du joueur, ou {@code null} s'il n'est dans aucune. */
    public State getArenaState(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? null : arena.getState();
    }

    /** Equipe du joueur dans sa partie ({@code Rouge}...), ou {@code null}. */
    public String getTeam(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? null : arena.getTeam(player);
    }

    private Arena arenaOf(Player player) {
        return player == null ? null : main.getArenaManager().getArenaOf(player);
    }
}
