package fr.laugh.antwars.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Une session de "mode pose" : l'admin a cliqué un bouton du menu qui demande
 * une position dans le monde (lobby, spawn, lit, NPC). Le menu se ferme et il
 * pose en marchant (clic droit a chaque emplacement).
 *
 * <p>Pour les spawns et les lits on peut enchainer plusieurs equipes d'affilee :
 * la file {@link #teams} contient les equipes restantes a placer, dans l'ordre.
 * Pour le lobby et les NPC, elle est vide (une seule capture).
 */
public class PlacementSession {

    /** Ce que l'admin est en train de placer. */
    public enum Kind { LOBBY, SPAWN, BED, NPC }

    private final String arena;
    private final Kind kind;
    private final List<String> teams;

    public PlacementSession(String arena, Kind kind, List<String> teams) {
        this.arena = arena;
        this.kind = kind;
        this.teams = teams == null ? new ArrayList<String>() : new ArrayList<String>(teams);
    }

    public String getArena() {
        return arena;
    }

    public Kind getKind() {
        return kind;
    }

    /** Equipe en cours de placement (tete de file), ou {@code null}. */
    public String currentTeam() {
        return teams.isEmpty() ? null : teams.get(0);
    }

    /** Retire l'equipe qui vient d'etre placee ; renvoie s'il en reste. */
    public boolean advance() {
        if (!teams.isEmpty()) {
            teams.remove(0);
        }
        return !teams.isEmpty();
    }

    /** Nombre d'equipes restant a placer (0 pour lobby/NPC). */
    public int remaining() {
        return teams.size();
    }
}
