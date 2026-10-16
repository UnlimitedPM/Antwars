package fr.laugh.antwars.config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.antwars.ArenaManager;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;

/**
 * Creation d'une arene depuis le menu : le bouton "Creer une arene" ferme le
 * menu et le prochain message de l'admin dans le chat devient le nom de
 * l'arene (il n'est pas diffuse). L'arene est alors creee et son menu
 * d'edition s'ouvre.
 *
 * <p>Pourquoi le chat plutot qu'une enclume ou un panneau : c'est la seule
 * saisie de texte identique de la 1.8 a la 1.21 sans code specifique a une
 * version.
 *
 * <p>Le chat arrive sur un thread asynchrone : la table des saisies en cours
 * est donc concurrente, et le traitement du nom est renvoye sur le thread
 * principal.
 */
public class ArenaNaming {

    private static final long TIMEOUT_TICKS = 60L * 20L;

    private final Main main;
    /** Saisies en cours, avec la tache qui les abandonne au bout du delai. */
    private final Map<UUID, BukkitRunnable> waiting = new ConcurrentHashMap<UUID, BukkitRunnable>();

    public ArenaNaming(Main main) {
        this.main = main;
    }

    public boolean isNaming(Player player) {
        return waiting.containsKey(player.getUniqueId());
    }

    /** Ferme le menu et demande le nom de la nouvelle arene dans le chat. */
    public void start(Player player) {
        main.getPlacements().clear(player);
        player.closeInventory();
        armTimeout(player);
        player.sendMessage(Main.PREFIX + Messages.tr("config.naming.prompt"));
    }

    /** Abandonne la saisie, avec message. Renvoie faux s'il n'y en avait pas. */
    public boolean cancel(Player player) {
        if (!stop(player)) {
            return false;
        }
        player.sendMessage(Main.PREFIX + Messages.tr("config.naming.cancelled"));
        return true;
    }

    /** Retire une saisie sans message (deconnexion). */
    public void clear(Player player) {
        stop(player);
    }

    /**
     * Traite le message tape par l'admin. A appeler sur le thread principal.
     * Un nom refuse laisse la saisie ouverte : l'admin peut en retaper un.
     */
    public void handleInput(Player player, String input) {
        if (!isNaming(player)) {
            return;
        }
        String name = input.trim();
        if ("annuler".equalsIgnoreCase(name) || "cancel".equalsIgnoreCase(name)) {
            cancel(player);
            main.getConfigMenu().openList(player);
            return;
        }
        if (!ArenaManager.isValidName(name)) {
            player.sendMessage(Main.PREFIX + Messages.tr("command.arena_invalid_name"));
            armTimeout(player);
            return;
        }
        if (!main.getArenaManager().createArena(name)) {
            player.sendMessage(Main.PREFIX + Messages.tr("command.arena_exists"));
            armTimeout(player);
            return;
        }
        stop(player);
        player.sendMessage(Main.PREFIX + Messages.tr("config.naming.created", "name", name));
        main.getConfigMenu().openEdit(player, name);
    }

    /** (Re)lance le delai de saisie : chaque essai rate redonne 60 secondes. */
    private void armTimeout(final Player player) {
        final UUID id = player.getUniqueId();
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // remove(id, this) : n'abandonne que si c'est encore cette tache
                // qui garde la saisie (pas une relancee entre-temps).
                if (waiting.remove(id, this) && player.isOnline()) {
                    player.sendMessage(Main.PREFIX + Messages.tr("config.naming.timeout"));
                }
            }
        };
        BukkitRunnable previous = waiting.put(id, task);
        if (previous != null) {
            previous.cancel();
        }
        task.runTaskLater(main, TIMEOUT_TICKS);
    }

    private boolean stop(Player player) {
        BukkitRunnable task = waiting.remove(player.getUniqueId());
        if (task == null) {
            return false;
        }
        task.cancel();
        return true;
    }
}
