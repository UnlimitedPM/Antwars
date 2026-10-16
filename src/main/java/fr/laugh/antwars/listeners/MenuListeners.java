package fr.laugh.antwars.listeners;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.shop.ShopHolder;

/**
 * Ouverture de la boutique et clics a l'interieur.
 */
public class MenuListeners implements Listener {

    private static final String SHOP_TAG = "[Boutique]";

    private final Main main;

    public MenuListeners(Main main) {
        this.main = main;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!isShopSign(sign)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isRunning() || arena.isSpectator(player)) {
            player.sendMessage(Main.PREFIX + Messages.tr("shop.only_during_game"));
            return;
        }
        main.getShopMenu().open(player);
    }

    /**
     * {@code getLines()} est deprecie depuis la 1.20 au profit des faces de
     * panneau, mais il reste present et lit la face avant : c'est le seul
     * appel qui fonctionne aussi bien en 1.8 qu'en 1.21.
     */
    @SuppressWarnings("deprecation")
    private boolean isShopSign(Sign sign) {
        String[] lines = sign.getLines();
        return lines.length > 0 && SHOP_TAG.equalsIgnoreCase(stripColors(lines[0]).trim());
    }

    private String stripColors(String text) {
        return text == null ? "" : org.bukkit.ChatColor.stripColor(text);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof ShopHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getWhoClicked();

        // getRawSlot < taille de l'inventaire du haut = emplacement de la vitrine.
        // C'est la maniere de distinguer haut et bas qui marche sur toutes les
        // versions, sans jamais appeler une methode d'InventoryView (interdit).
        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();

        // Clic sur la vitrine : jamais de manipulation, c'est un achat (ou, en
        // mode personnalisation de l'Achat Rapide, une bascule de favori).
        if (rawSlot >= 0 && rawSlot < topSize) {
            event.setCancelled(true);
            ShopHolder holder = (ShopHolder) top.getHolder();
            if (holder.isCustomize()) {
                main.getShopMenu().handleCustomizeClick(player, rawSlot, holder.getCategory());
            } else {
                main.getShopMenu().handleClick(player, rawSlot, holder.getCategory());
            }
            return;
        }

        // Clic dans le sac du joueur (bas) ou hors fenetre : on laisse le joueur
        // reorganiser son sac, mais on n'autorise QUE les actions qui restent a
        // l'interieur. Tout le reste - shift-clic, double-clic "collect", drop,
        // clone, touches 1-9 hors sac - pourrait faire entrer un objet dans la
        // vitrine ou en tirer/dupliquer un article expose : bloque.
        if (!isInternalMove(event.getAction())) {
            event.setCancelled(true);
        }
    }

    /**
     * Vrai pour les actions qui deplacent un objet uniquement a l'interieur du
     * sac du joueur (liste blanche : ce qui n'y figure pas est refuse pendant
     * que la boutique est ouverte).
     */
    private boolean isInternalMove(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    /**
     * Un glisser qui touche ne serait-ce qu'un emplacement de la vitrine est
     * refuse ; un glisser entierement dans le sac du joueur est autorise.
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof ShopHolder)) {
            return;
        }
        int topSize = top.getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
