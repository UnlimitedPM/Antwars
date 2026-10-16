package fr.laugh.antwars.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;

/**
 * Alimente le suivi de {@link fr.laugh.antwars.map.MapTracker} : enregistre
 * l'etat d'ORIGINE de chaque bloc modifie pendant une partie (minage, pose,
 * seau d'eau, ecoulement), pour pouvoir tout restaurer en fin de manche.
 *
 * <p>Priorite {@code MONITOR} + {@code ignoreCancelled} : on n'observe que les
 * changements qui aboutissent reellement (jamais un evenement annule par un
 * autre listener), et les blocs sont encore a leur etat d'origine a ce stade
 * (leur modification n'intervient qu'apres le traitement de l'evenement).
 *
 * <p>La TNT est traitee a part, dans {@link BedListeners} qui connait deja
 * l'arene concernee par l'explosion.
 */
public class MapListeners implements Listener {

    private final Main main;

    public MapListeners(Main main) {
        this.main = main;
    }

    // ================= Changements attribues a un joueur =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            // L'etat d'avant la pose : le restaurer revient a retirer le bloc pose.
            arena.getMapTracker().record(event.getBlockReplacedState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            Block target = event.getBlockClicked().getRelative(event.getBlockFace());
            arena.getMapTracker().record(target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlockClicked());
        }
    }

    // ================= Ecoulement de l'eau (sans joueur) =================

    /**
     * L'eau versee au seau s'ecoule : chaque bloc dans lequel une eau DEJA
     * suivie coule est ajoute au suivi (chainage). Comme la source est deja
     * enregistree, on retrouve l'arene sans avoir besoin d'une region definie.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        if (!main.isMapRegenerationEnabled()) {
            return;
        }
        Block source = event.getBlock();
        for (Arena arena : main.getArenaManager().getArenas()) {
            if (arena.isRunning() && arena.getMapTracker().contains(source)) {
                arena.getMapTracker().record(event.getToBlock());
                return;
            }
        }
    }

    // ================= Utilitaire =================

    /** Arene a suivre pour ce joueur, ou {@code null} (hors partie / regen off). */
    private Arena trackingArena(Player player) {
        if (!main.isMapRegenerationEnabled()) {
            return null;
        }
        Arena arena = main.getArenaManager().getArenaOf(player);
        return arena != null && arena.isRunning() ? arena : null;
    }
}
