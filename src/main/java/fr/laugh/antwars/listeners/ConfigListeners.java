package fr.laugh.antwars.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import fr.laugh.antwars.Main;
import fr.laugh.antwars.config.ConfigHolder;

/**
 * Clics dans les menus de configuration et capture des positions en "mode pose".
 *
 * <p>Comme la boutique, on reconnait un menu de config a son {@link ConfigHolder}
 * et on distingue haut/bas par {@code getRawSlot() < getInventory().getSize()}
 * (jamais d'appel a InventoryView). Contrairement a la boutique, aucun
 * deplacement d'objet n'est jamais permis dans un menu de config : tout clic y
 * est annule.
 */
public class ConfigListeners implements Listener {

    private final Main main;

    public ConfigListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof ConfigHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return; // clic dans le sac du joueur ou hors fenetre : sans effet.
        }
        main.getConfigMenu().handleClick(player, (ConfigHolder) top.getHolder(),
                rawSlot, event.isRightClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof ConfigHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Mode pose : le clic droit dans le monde capture une position. Priorite
     * haute pour passer avant les autres usages du clic droit (pancartes,
     * panneaux de connexion) pendant qu'une session est active.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (main.getPlacements().isPlacing(player) && main.getPlacements().handleInteract(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Nom d'une arene cree depuis le menu : le message de l'admin est capte
     * (jamais diffuse) puis traite sur le thread principal, le chat arrivant
     * sur un thread asynchrone. Priorite LOWEST : on le retire avant que les
     * plugins de chat ne le voient.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        if (!main.getArenaNaming().isNaming(player)) {
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(main, new Runnable() {
            @Override
            public void run() {
                main.getArenaNaming().handleInput(player, message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        main.getPlacements().clear(event.getPlayer());
        main.getArenaNaming().clear(event.getPlayer());
    }
}
