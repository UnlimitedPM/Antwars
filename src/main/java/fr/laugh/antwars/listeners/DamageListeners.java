package fr.laugh.antwars.listeners;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;

/**
 * Gestion des degats.
 *
 * <p>On ne touche pas aux degats des joueurs qui ne participent a aucune partie.
 * {@code EntityDamageByEntityEvent} herite de {@code EntityDamageEvent} : un
 * meme coup passe par les deux gestionnaires, qui ont donc des roles distincts
 * (generique / PvP) pour ne rien traiter deux fois.
 */
public class DamageListeners implements Listener {

    private final Main main;

    public DamageListeners(Main main) {
        this.main = main;
    }

    /** Degats generiques : chute, feu, suffocation, noyade... */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        // Hors phase de jeu (attente, compte a rebours, fin), on protege les
        // participants pour eviter les morts accidentelles au lobby.
        if (!arena.isRunning() || !arena.isAlive(player)) {
            event.setCancelled(true);
            return;
        }
        // Protection de reapparition : aucun degat (sauf /kill), pour ne pas
        // mourir des l'apparition. Elle s'arrete des que le joueur frappe.
        if (arena.hasSpawnProtection(player)
                && event.getCause() != EntityDamageEvent.DamageCause.SUICIDE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }

        Arena arena = main.getArenaManager().getArenaOf(victim);
        if (arena == null) {
            return;
        }

        // Un joueur exterieur ne peut pas frapper un participant, et
        // inversement.
        if (!arena.contains(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (!arena.isRunning() || !arena.isAlive(victim) || !arena.isAlive(attacker)) {
            event.setCancelled(true);
            return;
        }

        String victimTeam = arena.getTeam(victim);
        String attackerTeam = arena.getTeam(attacker);
        if (victimTeam != null && victimTeam.equals(attackerTeam)) {
            event.setCancelled(true);
            attacker.sendMessage(Main.PREFIX + Messages.tr("game.no_friendly_fire"));
        }
    }

    /**
     * Lecture seule : cumule les degats reellement infliges (resume de fin) et
     * marque la victime "en combat" (anti deconnexion en combat). Un joueur qui
     * frappe perd sa propre protection de reapparition : pas de tir gratuit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Arena arena = main.getArenaManager().getArenaOf(attacker);
        if (arena != null && arena.isRunning() && arena.contains(victim)
                && arena.isAlive(attacker) && arena.isAlive(victim)) {
            arena.recordHit(attacker, victim, event.getFinalDamage());
            arena.clearSpawnProtection(attacker);
        }
    }

    /** Retrouve le joueur a l'origine des degats, fleches comprises. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        // Projectile couvre les fleches comme les boules de neige et les
        // perles de l'Ender.
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }
}
