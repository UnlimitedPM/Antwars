package fr.laugh.antwars.listeners;

import java.util.Iterator;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.compat.Compat;
import fr.laugh.antwars.lang.Messages;

/**
 * Connexions, deconnexions, morts et reapparitions.
 */
public class PlayerListeners implements Listener {

    /** Les porteurs de cette permission ne sont pas auto-rejoints en mode arene. */
    private static final String ARENA_MODE_BYPASS = "antwars.arenamode.bypass";

    private final Main main;

    public PlayerListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Chargement des stats des la connexion : le jeu (kills, morts...)
        // survient bien plus tard, le chargement asynchrone a le temps d'aboutir.
        main.getStatsManager().load(player);
        main.getQuestManager().load(player);
        main.getQuickBuyManager().load(player);

        // Reconnexion : le joueur avait encore sa place dans une partie en cours
        // (le lit de son equipe existe toujours). Passe avant le mode arene, qui
        // le refuserait ("partie deja en cours").
        if (main.getArenaManager().getRejoinableArena(player.getUniqueId()) != null) {
            scheduleRejoin(player);
            return;
        }
        // Deconnecte qui a perdu sa place (lit detruit ou partie finie) : il
        // reapparait au milieu d'une map de jeu, on le renvoie au lobby.
        boolean stranded = main.getArenaManager().consumeStranded(player.getUniqueId());

        // Mode arene (reseau "1 serveur = 1 partie") : on jette le joueur
        // directement dans la partie, sauf s'il a la permission de contournement.
        if (main.isArenaModeEnabled() && !player.hasPermission(ARENA_MODE_BYPASS)) {
            autoJoinArena(player);
            return;
        }
        if (stranded) {
            sendHome(player);
            return;
        }

        // File d'attente automatique : le joueur est au spawn du serveur et
        // rejoint tout seul l'arene des qu'une place est libre. C'est le mode
        // par defaut du serveur de jeu (pas besoin de /aw join).
        if (main.isAutoQueueEnabled()) {
            Location lobby = main.getServerLobby();
            if (lobby != null && lobby.getWorld() != null) {
                Compat.resetPlayer(player, GameMode.ADVENTURE);
                player.teleport(lobby);
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && main.getAutoQueue() != null) {
                        main.getAutoQueue().queue(player);
                    }
                }
            }.runTaskLater(main, 1L);
            return;
        }

        // Comportement normal : les joueurs ne sont pas ajoutes d'office a une
        // partie, ils entrent par /aw join.
        if (!main.getConfig().getBoolean("lobby-on-join", true)) {
            return;
        }

        Location lobby = main.getServerLobby();
        if (lobby != null && lobby.getWorld() != null) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            player.teleport(lobby);
        }
    }

    /**
     * Fait rejoindre l'arene configuree au joueur, un tick apres la connexion
     * (le temps que celle-ci s'acheve). Si l'arene est introuvable, pleine ou
     * qu'une partie y est deja en cours, on renvoie le joueur vers le hub.
     */
    private void autoJoinArena(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Arena arena = main.getArenaManager().getArena(main.getArenaModeArena());
                String problem = arena == null
                        ? "aucune arene configuree pour ce serveur"
                        : arena.join(player);
                if (problem != null) {
                    player.sendMessage(Main.PREFIX + Messages.tr("player.arena_mode_no_game"));
                    Compat.resetPlayer(player, GameMode.ADVENTURE);
                    main.returnToLobby(player);
                }
            }
        }.runTaskLater(main, 1L);
    }

    /**
     * Remet le joueur dans sa partie un tick apres la connexion (le temps que
     * celle-ci s'acheve). Si le lit a ete detruit ou la partie finie entre-temps,
     * il est traite comme un deconnecte qui a perdu sa place.
     */
    private void scheduleRejoin(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Arena arena = main.getArenaManager().getRejoinableArena(player.getUniqueId());
                if (arena != null) {
                    arena.rejoin(player);
                    return;
                }
                main.getArenaManager().consumeStranded(player.getUniqueId());
                if (main.isArenaModeEnabled() && !player.hasPermission(ARENA_MODE_BYPASS)) {
                    autoJoinArena(player);
                } else {
                    sendHome(player);
                }
            }
        }.runTaskLater(main, 1L);
    }

    /** Ramene au lobby un deconnecte qui a perdu sa place, en le prevenant. */
    private void sendHome(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Compat.resetPlayer(player, GameMode.ADVENTURE);
                player.sendMessage(Main.PREFIX + Messages.tr("player.reconnect_lost"));
                main.returnToLobby(player);
                if (main.isAutoQueueEnabled() && main.getAutoQueue() != null) {
                    main.getAutoQueue().queue(player);
                }
            }
        }.runTaskLater(main, 1L);
    }

    /**
     * Deconnexion : un joueur vivant en pleine partie garde sa place tant que le
     * lit de son equipe existe (voir {@link Arena#disconnect}) ; sinon, ou si la
     * reconnexion est desactivee, c'est un depart definitif.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena != null) {
            if (main.isReconnectionEnabled()) {
                arena.disconnect(player);
            } else {
                // Pas de teleportation : le joueur est deja parti.
                arena.leave(player, false);
            }
        }
        if (main.getAutoQueue() != null) {
            main.getAutoQueue().onQuit(player);
        }
        main.getStatsManager().unload(player);
        main.getQuestManager().unload(player);
        main.getQuickBuyManager().unload(player);
    }

    /**
     * Mort d'un participant : il reapparait s'il s'est lie a un lit encore
     * debout, sinon il est definitivement elimine.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isRunning() || !arena.isAlive(player)) {
            return;
        }

        // Statistiques : une mort pour la victime, un kill pour son tueur s'il
        // joue dans la meme arene, et les series de kills (voir Arena).
        arena.registerDeath(player, player.getKiller());

        // Le lit ne traine jamais au sol : s'il n'etait pas encore pose, il
        // repart directement dans les mains d'un coequipier.
        dropBeds(event.getDrops());
        arena.getBeds().handleCarrierLost(player);

        if (arena.getBeds().canRespawn(player)) {
            player.sendMessage(Main.PREFIX + Messages.tr("player.respawn_at_bed"));
            return;
        }

        // On attend un tick : changer le mode de jeu au beau milieu de
        // l'evenement de mort laisse le joueur dans un etat incoherent
        // (invisible ou bloque sur l'ecran de mort) sur plusieurs versions.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    arena.eliminate(player, reasonFor(arena, player));
                }
            }
        }.runTaskLater(main, 1L);
    }

    /** Explique au joueur pourquoi sa mort est definitive. */
    private String reasonFor(Arena arena, Player player) {
        String team = arena.getTeam(player);
        if (team != null && !arena.getBeds().isUsable(team)) {
            return Messages.tr("player.reason_no_bed");
        }
        return Messages.tr("player.reason_not_bound");
    }

    /**
     * Retire les lits des objets laisses au sol par un joueur mort : un lit ne
     * doit jamais pouvoir etre ramasse par un adversaire.
     */
    private void dropBeds(List<ItemStack> drops) {
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item != null && Beds.isBed(item.getType())) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }

        // Reapparition au lit pour les joueurs qui s'y sont lies, retour au
        // lobby de l'arene pour les elimines qui passent spectateurs.
        Location target = null;
        if (arena.getBeds().canRespawn(player)) {
            target = arena.getBeds().respawnLocation(player);
            // Protection de reapparition : un tick plus tard,
            // le joueur n'etant pas encore reellement reapparu dans l'evenement.
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && arena.isRunning() && arena.isAlive(player)) {
                        arena.protect(player);
                    }
                }
            }.runTaskLater(main, 1L);
        }
        if (target == null) {
            target = arena.getLobby();
        }
        if (target != null && target.getWorld() != null) {
            event.setRespawnLocation(target);
        }
    }
}
