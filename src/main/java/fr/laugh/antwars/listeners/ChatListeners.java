package fr.laugh.antwars.listeners;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;

/**
 * Chat des parties.
 *
 * <p>Pendant une partie, un message normal n'est lu que par l'equipe de
 * l'auteur ; un message qui commence par {@code chat.all-prefix} ({@code !}) est
 * lu par tout le monde. Avant le lancement et a l'ecran de fin, le chat est
 * general a l'arene. Un spectateur lit tout, mais ses messages ne sont lus que
 * par les autres spectateurs.
 *
 * <p>"Tout le monde" depend de {@code chat.scope} : {@code arena} (les joueurs et
 * spectateurs de la partie ; les joueurs en partie ne recoivent plus le chat du
 * hub, ni l'inverse) ou {@code world} (les joueurs du monde de l'auteur).
 *
 * <p>On ne fait qu'ajouter une etiquette devant le format existant et filtrer
 * les destinataires : les grades ajoutes par un autre plugin de chat restent.
 *
 * <p>L'evenement arrive sur un thread asynchrone alors que l'etat des arenes
 * appartient au thread principal : la decision y est donc prise (voir
 * {@link #decide}), l'evenement ne fait qu'appliquer le resultat.
 */
public class ChatListeners implements Listener {

    /** Attente maximale de la decision : au-dela, le message passe tel quel. */
    private static final long DECISION_TIMEOUT_MS = 2000L;

    private final Main main;
    /** Joueurs dont le chat par defaut est "tout le monde" (/aw chat all). */
    private final Set<UUID> defaultAll = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    public ChatListeners(Main main) {
        this.main = main;
    }

    /** Choisit le chat par defaut d'un joueur : equipe ou tout le monde. */
    public void setDefaultAll(Player player, boolean all) {
        if (all) {
            defaultAll.add(player.getUniqueId());
        } else {
            defaultAll.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!main.isChatEnabled()) {
            return;
        }
        final Player player = event.getPlayer();
        final String message = event.getMessage();

        Decision decision;
        if (Bukkit.isPrimaryThread()) {
            decision = decide(player, message);
        } else {
            try {
                decision = Bukkit.getScheduler().callSyncMethod(main, new Callable<Decision>() {
                    @Override
                    public Decision call() {
                        return decide(player, message);
                    }
                }).get(DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return; // serveur surcharge ou arrete : le message passe tel quel.
            }
        }
        if (decision == null) {
            return;
        }

        if (decision.cancel) {
            event.setCancelled(true);
            return;
        }
        if (!decision.message.equals(message)) {
            event.setMessage(decision.message);
        }
        if (decision.recipients != null) {
            Iterator<Player> it = event.getRecipients().iterator();
            while (it.hasNext()) {
                if (!decision.recipients.contains(it.next().getUniqueId())) {
                    it.remove();
                }
            }
        }
        if (decision.tag != null && !decision.tag.isEmpty()) {
            // Le format est un modele String.format : un % de l'etiquette doit etre double.
            event.setFormat(decision.tag.replace("%", "%%") + event.getFormat());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        defaultAll.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Qui lit ce message, avec quelle etiquette. A appeler sur le thread
     * principal. {@code null} = le chat n'est pas touche.
     */
    private Decision decide(Player sender, String message) {
        boolean world = "world".equals(main.getChatScope());
        Arena arena = main.getArenaManager().getArenaOf(sender);
        Decision decision = new Decision(message);

        if (arena == null) {
            if (world) {
                return null;
            }
            // Hors partie : ni lu par ceux qui jouent, ni ne les lit.
            Set<UUID> outside = new HashSet<UUID>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (main.getArenaManager().getArenaOf(online) == null) {
                    outside.add(online.getUniqueId());
                }
            }
            decision.recipients = outside;
            return decision;
        }

        if (arena.isSpectator(sender)) {
            decision.recipients = ids(arena.onlineSpectators(), sender);
            decision.tag = Messages.tr("chat.tag.spectator");
            return decision;
        }

        String team = arena.getTeam(sender);
        if (team == null || !arena.isRunning()) {
            // Attente, compte a rebours, ecran de fin : chat general de l'arene.
            decision.recipients = everyone(arena, sender, world);
            decision.tag = team == null ? "" : allTag(team);
            return decision;
        }

        String prefix = main.getChatAllPrefix();
        boolean prefixed = message.startsWith(prefix);
        if (prefixed) {
            String stripped = message.substring(prefix.length()).trim();
            if (stripped.isEmpty()) {
                decision.cancel = true;
                return decision;
            }
            decision.message = stripped;
        }
        // Le prefixe inverse le chat par defaut du joueur.
        boolean toAll = prefixed != defaultAll.contains(sender.getUniqueId());
        if (toAll) {
            decision.recipients = everyone(arena, sender, world);
            decision.tag = allTag(team);
        } else {
            Set<UUID> readers = new HashSet<UUID>();
            for (Player player : arena.onlinePlayers()) {
                if (team.equals(arena.getTeam(player))) {
                    readers.add(player.getUniqueId());
                }
            }
            for (Player spectator : arena.onlineSpectators()) {
                readers.add(spectator.getUniqueId());
            }
            readers.add(sender.getUniqueId());
            decision.recipients = readers;
            decision.tag = Messages.tr("chat.tag.team", "color", Arena.colorOf(team), "team", team);
        }
        return decision;
    }

    private String allTag(String team) {
        return Messages.tr("chat.tag.all", "color", Arena.colorOf(team), "team", team);
    }

    /** Destinataires d'un message "a tout le monde" selon {@code chat.scope}. */
    private Set<UUID> everyone(Arena arena, Player sender, boolean world) {
        if (world) {
            return ids(sender.getWorld().getPlayers(), sender);
        }
        return ids(arena.audience(), sender);
    }

    private static Set<UUID> ids(Iterable<Player> players, Player sender) {
        Set<UUID> ids = new HashSet<UUID>();
        for (Player player : players) {
            ids.add(player.getUniqueId());
        }
        ids.add(sender.getUniqueId());
        return ids;
    }

    /** Resultat de la decision, calcule sur le thread principal. */
    private static final class Decision {
        String message;
        /** {@code null} = tous les destinataires d'origine. */
        Set<UUID> recipients;
        String tag;
        boolean cancel;

        Decision(String message) {
            this.message = message;
        }
    }
}
