package fr.laugh.antwars.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.sign.JoinSign;

/**
 * Panneaux de connexion : creation ({@code [antwars]} + nom d'arene ou
 * {@code random}), retrait (a la casse), et clic droit pour rejoindre.
 *
 * <p>Rien a voir avec la pancarte de boutique ([Boutique], {@link MenuListeners})
 * qui, elle, se reconnait a son contenu au moment du clic : un panneau de
 * connexion est enregistre (position suivie) et son contenu est reecrit en
 * affichage live, donc on l'identifie par sa position, pas par son texte.
 */
public class SignListeners implements Listener {

    private static final String SIGN_TAG = "[antwars]";
    private static final String ADMIN = "antwars.admin";

    private final Main main;

    public SignListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String header = ChatColor.stripColor(event.getLine(0)).trim();
        if (!SIGN_TAG.equalsIgnoreCase(header)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN)) {
            player.sendMessage(Main.PREFIX + Messages.tr("command.no_permission", "permission", ADMIN));
            return;
        }

        String arg = ChatColor.stripColor(event.getLine(1)).trim();
        String target;
        if ("random".equalsIgnoreCase(arg)) {
            target = "";
            player.sendMessage(Main.PREFIX + Messages.tr("sign.created_random"));
        } else {
            Arena arena = main.getArenaManager().getArena(arg);
            if (arena == null) {
                player.sendMessage(Main.PREFIX + Messages.tr("command.arena_not_found", "name", arg));
                return;
            }
            target = arena.getName();
            player.sendMessage(Main.PREFIX + Messages.tr("sign.created", "arena", arena.getName()));
        }

        Location loc = event.getBlock().getLocation();
        main.getJoinSignManager().register(loc, target);

        // Affiche immediatement le panneau formate (sinon il resterait "[antwars]"
        // jusqu'au prochain rafraichissement).
        String[] lines = main.getJoinSignManager().format(main.getJoinSignManager().get(loc));
        for (int i = 0; i < 4; i++) {
            event.setLine(i, lines[i]);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (main.getJoinSignManager().get(loc) != null) {
            main.getJoinSignManager().unregister(loc);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        JoinSign sign = main.getJoinSignManager().get(event.getClickedBlock().getLocation());
        if (sign == null) {
            return;
        }
        event.setCancelled(true);
        main.getJoinSignManager().join(event.getPlayer(), sign);
    }
}
