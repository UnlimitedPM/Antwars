package fr.laugh.antwars.stats;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Main;

/**
 * Orchestration des statistiques : cache en memoire + persistance asynchrone.
 *
 * <p><b>Modele de threading</b>, le point sensible :
 * <ul>
 *   <li>les compteurs ne sont modifies que sur le <b>thread principal</b> (les
 *       evenements de jeu) ;</li>
 *   <li>toute I/O (fichier ou base) passe par un <b>unique thread d'I/O</b>, ce
 *       qui serialise les acces et evite tout gel du serveur ;</li>
 *   <li>ce qui traverse vers ce thread est toujours une {@link PlayerStats#copy()
 *       copie}, jamais l'objet vivant : aucune donnee mutable n'est partagee
 *       entre les deux threads.</li>
 * </ul>
 *
 * <p>Les stats se chargent a la connexion et se sauvent a la deconnexion, en fin
 * de partie, et a l'arret du plugin. Les evenements de jeu (kill, mort,
 * victoire) survenant bien apres la connexion, le chargement asynchrone a
 * toujours eu le temps d'aboutir.
 */
public class StatsManager {

    private final Main main;
    private final StatsStorage storage;
    private final ExecutorService io;
    private final ConcurrentHashMap<UUID, PlayerStats> cache =
            new ConcurrentHashMap<UUID, PlayerStats>();

    public StatsManager(Main main) {
        this.main = main;
        this.storage = createStorage();
        this.io = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AntWars-Stats");
                thread.setDaemon(true);
                return thread;
            }
        });
        io.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    storage.init();
                } catch (Exception e) {
                    main.getLogger().severe("Statistiques : initialisation du stockage impossible : "
                            + e.getMessage() + ". Les stats ne seront pas enregistrees.");
                }
            }
        });
    }

    private StatsStorage createStorage() {
        String type = main.getConfig().getString("storage.type", "local");
        // new MySqlStatsStorage n'est atteint qu'ici : un serveur en mode local
        // ne charge donc jamais cette classe ni le pilote MariaDB.
        if ("mysql".equalsIgnoreCase(type)) {
            return new MySqlStatsStorage(main);
        }
        return new YamlStatsStorage(main);
    }

    // ================= Chargement / dechargement =================

    /** Charge les stats d'un joueur a sa connexion (asynchrone). */
    public void load(Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        io.submit(new Runnable() {
            @Override
            public void run() {
                cache.put(uuid, storage.load(uuid, name));
            }
        });
    }

    public void unload(Player player) {
        save(player.getUniqueId());
        cache.remove(player.getUniqueId());
    }

    // ================= Compteurs (thread principal) =================

    private PlayerStats live(Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        PlayerStats stats = cache.get(uuid);
        if (stats == null) {
            stats = new PlayerStats(uuid, name);
            cache.put(uuid, stats);
        }
        return stats;
    }

    public void addGame(Player player) {
        live(player).addGame();
    }

    public void addWin(Player player) {
        live(player).addWin();
    }

    public void addLoss(Player player) {
        live(player).addLoss();
    }

    public void addKill(Player player) {
        live(player).addKill();
    }

    public void addDeath(Player player) {
        live(player).addDeath();
    }

    /** Ajuste la note ELO d'un joueur (positif ou negatif). Voir {@link Elo}. */
    public void addRating(Player player, int delta) {
        live(player).addRating(delta);
    }

    /** Stats en cache d'un joueur connecte (valeurs vives), ou {@code null}. */
    public PlayerStats getCached(UUID uuid) {
        return cache.get(uuid);
    }

    // ================= Persistance =================

    /** Programme la sauvegarde d'un joueur (copie remise au thread d'I/O). */
    public void save(UUID uuid) {
        PlayerStats stats = cache.get(uuid);
        if (stats == null) {
            return;
        }
        final PlayerStats copy = stats.copy();
        io.submit(new Runnable() {
            @Override
            public void run() {
                storage.save(copy);
            }
        });
    }

    // ================= Lectures asynchrones (commandes) =================

    /** Retrouve des stats par nom et rend le resultat sur le thread principal. */
    public void lookupByName(final String name, final Consumer<PlayerStats> callback) {
        io.submit(new Runnable() {
            @Override
            public void run() {
                final PlayerStats stats = storage.loadByName(name);
                Bukkit.getScheduler().runTask(main, new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(stats);
                    }
                });
            }
        });
    }

    /** Classement pour une stat, rendu sur le thread principal. */
    public void top(final String column, final int limit, final Consumer<List<PlayerStats>> callback) {
        io.submit(new Runnable() {
            @Override
            public void run() {
                final List<PlayerStats> list = storage.top(column, limit);
                Bukkit.getScheduler().runTask(main, new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(list);
                    }
                });
            }
        });
    }

    // ================= Arret =================

    /**
     * Sauve tout le cache puis ferme le stockage. Appele a la desactivation du
     * plugin : on attend la fin des ecritures avant de rendre la main.
     */
    public void shutdown() {
        for (PlayerStats stats : cache.values()) {
            final PlayerStats copy = stats.copy();
            io.submit(new Runnable() {
                @Override
                public void run() {
                    storage.save(copy);
                }
            });
        }
        io.submit(new Runnable() {
            @Override
            public void run() {
                storage.close();
            }
        });
        io.shutdown();
        try {
            io.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
