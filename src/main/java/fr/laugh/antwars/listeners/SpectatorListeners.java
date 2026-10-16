package fr.laugh.antwars.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.game.SpectatorHolder;
import fr.laugh.antwars.lang.Messages;

/**
 * Tout ce qu'un spectateur ne doit pas pouvoir faire (toucher aux blocs, aux
 * coffres, deplacer ou jeter ses objets), et l'usage de ses deux objets : la
 * boussole (liste des joueurs) et la barriere (quitter).
 *
 * <p>Il n'est pas en mode "spectateur" de Minecraft mais en aventure : seuls ses
 * clics sont donc a bloquer ici. Degats, faim et pose de blocs sont deja couverts
 * par les autres listeners (un spectateur n'est pas "en vie" dans la partie) et
 * par {@code Compat.makeSpectator}.
 */
public class SpectatorListeners implements Listener {

    private final Main main;

    public SpectatorListeners(Main main) {
        this.main = main;
    }

    /** L'arene que ce joueur regarde, ou {@code null} s'il n'est pas spectateur. */
    private Arena spectatorArena(Player player) {
        Arena arena = main.getArenaManager().getArenaOf(player);
        return arena != null && arena.isSpectator(player) ? arena : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Arena arena = spectatorArena(player);
        if (arena == null) {
            return;
        }
        // Rien n'est jamais actionne par un spectateur : coffre, porte, levier,
        // plaque de pression, panneau de boutique.
        event.setCancelled(true);
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (AWMaterial.COMPASS.is(item.getType())) {
            main.getSpectatorMenu().open(player, arena);
        } else if (AWMaterial.BARRIER.is(item.getType())) {
            arena.leave(player, true);
            player.sendMessage(Main.PREFIX + Messages.tr("command.left_arena", "arena", arena.getName()));
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (spectatorArena(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    /** Le menu des joueurs reagit au clic ; partout ailleurs, aucun objet ne bouge. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof SpectatorHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            if (rawSlot >= 0 && rawSlot < top.getSize()) {
                main.getSpectatorMenu().handleClick(player, (SpectatorHolder) top.getHolder(), rawSlot);
            }
            return;
        }
        if (spectatorArena(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getInventory();
        if ((top != null && top.getHolder() instanceof SpectatorHolder) || spectatorArena(player) != null) {
            event.setCancelled(true);
        }
    }

    /** Aucun conteneur ne s'ouvre pour un spectateur, hormis son propre menu. */
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (inventory != null && inventory.getHolder() instanceof SpectatorHolder) {
            return;
        }
        if (spectatorArena((Player) event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (spectatorArena(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }
}
