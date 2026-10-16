package fr.laugh.antwars.map;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * Regeneration de la map par suivi des changements : on memorise l'etat
 * d'origine de chaque bloc modifie pendant la partie, puis on le rejoue en fin
 * de manche pour remettre le terrain a neuf.
 *
 * <p>Meme technique que les lits : un instantane {@link BlockState} par bloc,
 * restaure via {@link BlockState#update(boolean, boolean)}. Zero dependance,
 * aucune API posterieure a la 1.8, et ca marche a l'identique sur toutes les
 * versions.
 *
 * <p><b>Premiere ecriture gagnante</b> : si un bloc change plusieurs fois (mine
 * puis re-pose, par exemple), seul son tout premier etat compte - c'est le vrai
 * etat d'origine. Les enregistrements doivent donc capturer l'etat AVANT chaque
 * modification (voir les listeners qui alimentent ce suivi).
 */
public class MapTracker {

    private final Map<String, BlockState> originals = new LinkedHashMap<String, BlockState>();

    /** Memorise l'etat actuel d'un bloc (a appeler AVANT sa modification). */
    public void record(Block block) {
        if (block != null) {
            record(block.getState());
        }
    }

    /**
     * Memorise un etat de bloc deja capture, par exemple
     * {@code BlockPlaceEvent.getBlockReplacedState()} (l'etat d'avant la pose).
     */
    public void record(BlockState state) {
        if (state == null || state.getWorld() == null) {
            return;
        }
        String key = key(state.getWorld().getName(), state.getX(), state.getY(), state.getZ());
        if (!originals.containsKey(key)) {
            originals.put(key, state);
        }
    }

    /**
     * Vrai si ce bloc est deja suivi. Sert au chainage de l'ecoulement de l'eau :
     * un bloc dans lequel de l'eau suivie s'ecoule est lui-meme ajoute au suivi.
     */
    public boolean contains(Block block) {
        return block != null && originals.containsKey(
                key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    public int size() {
        return originals.size();
    }

    public void clear() {
        originals.clear();
    }

    /**
     * Remet tous les blocs suivis a leur etat d'origine.
     *
     * <p>Physique desactivee ({@code update(true, false)}) : on ne veut aucun
     * effet en cascade pendant la restauration (pas de bloc qui se casse parce
     * qu'un voisin n'est pas encore repose). L'ordre n'importe donc pas.
     *
     * <p>Restauration synchrone : sur une partie tres creusee cela peut faire un
     * bref pic (l'arene est vide a ce moment). Si besoin, on l'etalera sur
     * plusieurs ticks.
     */
    public void restore() {
        for (BlockState state : originals.values()) {
            if (state != null && state.getWorld() != null) {
                state.update(true, false);
            }
        }
        originals.clear();
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
