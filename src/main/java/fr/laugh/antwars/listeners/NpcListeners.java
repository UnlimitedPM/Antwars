package fr.laugh.antwars.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.npc.ShopNpc;

/**
 * Le NPC de boutique : clic pour ouvrir la boutique, et protection contre les
 * degats et le vol de son equipement. Couvre les deux formes de NPC, l'armor
 * stand comme le vrai NPC Citizens (voir {@link ShopNpc}).
 *
 * <p>Meme regle d'acces que la pancarte [Boutique] (voir {@link MenuListeners}) :
 * la boutique ne s'ouvre que pour un joueur d'une partie en cours. Les deux
 * coexistent volontairement, le NPC etant plus fragile (chunk a charger).
 */
public class NpcListeners implements Listener {

    /**
     * Anti double-declenchement : depuis la 1.9, un clic droit sur une entite
     * leve l'evenement deux fois (main principale + seconde main). On ne peut pas
     * filtrer par {@code getHand()}, API posterieure a la 1.8 (regle 3), donc on
     * ignore deux clics rapproches du meme joueur. Meme remede que dans
     * {@code config/Placements}.
     */
    private static final long DEBOUNCE_MS = 250L;

    private final Main main;
    private final Map<UUID, Long> lastClick = new HashMap<UUID, Long>();

    public NpcListeners(Main main) {
        this.main = main;
    }

    /**
     * Clic droit sur le NPC.
     *
     * <p>On ecoute {@link PlayerInteractEntityEvent} et non sa sous-classe
     * {@code PlayerInteractAtEntityEvent} : les deux partagent la meme liste de
     * handlers, donc ce seul point d'entree recoit aussi bien le clic sur un
     * armor stand que sur un NPC Citizens.
     *
     * <p><b>Pas de {@code ignoreCancelled}</b> : Citizens protege ses NPC en
     * annulant l'interaction, et un handler qui ignore les evenements annules ne
     * serait jamais appele. C'est exactement le piege qui avait rendu la barriere
     * du lobby muette.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent event) {
        openOnRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    /**
     * Certains clics droits sur un armor stand passent par la sous-classe
     * {@link PlayerInteractAtEntityEvent} sans declencher le handler parent.
     * On l'ecoute explicitement : c'est le cas qui manquait sur 1.21.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        openOnRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    /**
     * Clic droit sur un armor stand avec un item en main : Bukkit le traduit en
     * manipulation de l'armor stand. On annule le vol d'equipement et on ouvre
     * la boutique pour que le clic droit marche dans tous les cas.
     */
    private void openOnRightClick(Player player, Entity clicked,
            org.bukkit.event.Cancellable event) {
        if (!ShopNpc.isShopNpc(clicked)) {
            return;
        }
        event.setCancelled(true);
        if (!debounced(player)) {
            openShop(player);
        }
    }

    /**
     * Degats sur le NPC : toujours annules (il est indestructible). Un coup de
     * poing d'un joueur ouvre aussi la boutique, par symetrie avec la pancarte.
     *
     * <p>{@link EntityDamageByEntityEvent} etant une sous-classe de
     * {@link EntityDamageEvent}, ce seul handler couvre les deux : la TNT comme
     * le poing.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!ShopNpc.isShopNpc(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof Player && !debounced((Player) damager)) {
                openShop((Player) damager);
            }
        }
    }

    /** Empeche de retirer la tete ou les habits du NPC (armor stand uniquement). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        openOnRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    /** Vrai si ce joueur vient deja de cliquer il y a moins de {@link #DEBOUNCE_MS}. */
    private boolean debounced(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player.getUniqueId(), Long.valueOf(now));
        return previous != null && now - previous.longValue() < DEBOUNCE_MS;
    }

    private void openShop(Player player) {
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isRunning() || arena.isSpectator(player)) {
            player.sendMessage(Main.PREFIX + Messages.tr("shop.only_during_game"));
            return;
        }
        main.getShopMenu().open(player);
    }
}
