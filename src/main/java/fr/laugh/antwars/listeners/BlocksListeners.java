package fr.laugh.antwars.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.lang.Messages;

/**
 * Blocs que l'on peut miner et poser.
 *
 * <p>Point important : si le joueur ne participe a aucune partie, le plugin ne
 * s'en mele pas, sans quoi les batisseurs et les administrateurs seraient
 * geles sur tout le serveur.
 */
public class BlocksListeners implements Listener {

    /** Seuls blocs manipulables pendant une partie. */
    private static final AWMaterial[] ALLOWED = {
            AWMaterial.DIRT,
            AWMaterial.TORCH,
            AWMaterial.TNT,
            AWMaterial.CHEST,
            AWMaterial.LADDER,
            // Vendu en boutique : sans cette entree, un joueur ne pourrait pas
            // poser l'end stone qu'il vient d'acheter. Attention : comme tout
            // bloc de jeu, il saute aussi a la TNT (pas de resistance speciale).
            AWMaterial.END_STONE
    };

    private final Main main;

    public BlocksListeners(Main main) {
        this.main = main;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        handle(event.getPlayer(), event.getBlock().getType(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        handle(event.getPlayer(), event.getBlock().getType(), event);
    }

    private void handle(Player player, Material blockType,
            org.bukkit.event.Cancellable event) {
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        if (!arena.isRunning() || !arena.isAlive(player)) {
            event.setCancelled(true);
            return;
        }
        if (!isAllowed(blockType)) {
            event.setCancelled(true);
            player.sendMessage(Main.PREFIX + Messages.tr("game.block_not_allowed"));
        }
    }

    private boolean isAllowed(Material material) {
        return isGameBlock(material);
    }

    /**
     * Blocs qui font partie du jeu : ceux que l'on peut miner et poser, et
     * les seuls qu'une explosion a le droit d'emporter.
     *
     * <p>Les lits sont acceptes ici pour que la pose et la casse aboutissent ;
     * ce sont les {@link BedListeners} qui decident ensuite qui a le droit de
     * faire quoi avec eux.
     */
    public static boolean isGameBlock(Material material) {
        for (AWMaterial allowed : ALLOWED) {
            if (allowed.is(material)) {
                return true;
            }
        }
        return Beds.isBed(material);
    }
}
