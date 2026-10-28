package fr.laugh.antwars.queue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.State;

/**
 * File d'attente automatique du serveur de jeu.
 *
 * <p>Objectif : un joueur qui se connecte n'a jamais besoin de taper
 * {@code /aw join}. S'il n'y a pas de partie en cours, il est place dans la
 * file de l'arene ; s'il y a deja une partie, il attend au lobby et rejoint
 * automatiquement des que l'arene redevient disponible.
 *
 * <p>Les arenes sont essayees dans l'ordre de {@code auto-queue.arenas}. On ne
 * demarre jamais deux arenes dans le meme monde en meme temps : la bordure de
 * deathmatch est globale au monde et les terrains se chevaucheraient.
 */
public final class AutoQueue {

    private final Main main;
    private final Set<UUID> waiting = new HashSet<UUID>();
    /** Joueurs qui ont fait /aw leave : on ne les remet pas d'office. */
    private final Set<UUID> disabled = new HashSet<UUID>();
    private BukkitTask task;

    public AutoQueue(Main main) {
        this.main = main;
    }

    public void start() {
        stop();
        if (!main.isAutoQueueEnabled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(main, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        waiting.clear();
        disabled.clear();
    }

    /** Ajoute un joueur a la file et tente une entree immediate. */
    public void queue(Player player) {
        if (player == null || !main.isAutoQueueEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (disabled.contains(id)) {
            return;
        }
        waiting.add(id);
        tick();
    }

    /** Le joueur a quitte volontairement : on ne le remet pas dans la file. */
    public void disable(Player player) {
        if (player == null) {
            return;
        }
        waiting.remove(player.getUniqueId());
        disabled.add(player.getUniqueId());
    }

    /** Reautorise la file (utilise par /aw join manuel). */
    public void enable(Player player) {
        if (player != null) {
            disabled.remove(player.getUniqueId());
        }
    }

    public void remove(Player player) {
        if (player != null) {
            waiting.remove(player.getUniqueId());
        }
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        waiting.remove(id);
        // Un joueur qui se deconnecte n'a pas "quitte" : a sa reconnexion, il
        // doit retrouver la file automatiquement.
        disabled.remove(id);
    }

    /** Un tick de file : essaie de placer chaque joueur en attente. */
    public void tick() {
        if (!main.isAutoQueueEnabled()) {
            return;
        }
        List<Arena> candidates = candidates();
        int online = Bukkit.getOnlinePlayers().size();
        for (UUID id : new ArrayList<UUID>(waiting)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                waiting.remove(id);
                continue;
            }
            if (main.getArenaManager().getArenaOf(player) != null) {
                waiting.remove(id);
                continue;
            }
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            for (Arena arena : candidates) {
                if (!arena.isJoinable() || arena.isFull() || worldBusy(arena)) {
                    continue;
                }
                // Choisit un mode assez grand pour les joueurs presents : 2 a 4
                // joueurs restent en 2x2, 5 a 8 passent en 4 equipes de 2, etc.
                if (arena.getMaxPlayers() < online) {
                    continue;
                }
                String problem = arena.join(player);
                if (problem == null) {
                    waiting.remove(id);
                    break;
                }
            }
        }
    }

    private List<Arena> candidates() {
        List<Arena> arenas = new ArrayList<Arena>();
        for (String name : main.getConfig().getStringList("auto-queue.arenas")) {
            Arena arena = main.getArenaManager().getArena(name);
            if (arena != null && !arenas.contains(arena)) {
                arenas.add(arena);
            }
        }
        return arenas;
    }

    /**
     * Vrai si une autre arene du meme monde est deja en cours (ou sur le point
     * de demarrer). Deux jeux simultanes dans le meme monde partageraient la
     * meme bordure et le meme terrain.
     */
    private boolean worldBusy(Arena candidate) {
        if (candidate.getLobby() == null || candidate.getLobby().getWorld() == null) {
            return true;
        }
        for (Arena other : main.getArenaManager().getArenas()) {
            if (other == candidate || other.getState() == State.WAITING) {
                continue;
            }
            if (other.getLobby() != null && other.getLobby().getWorld() != null
                    && candidate.getLobby().getWorld().equals(other.getLobby().getWorld())) {
                return true;
            }
        }
        return false;
    }
}
