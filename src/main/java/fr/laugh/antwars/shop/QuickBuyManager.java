package fr.laugh.antwars.shop;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Main;

import fr.laugh.antwars.compat.Yaml;

/**
 * Selection personnelle d'un joueur pour l'onglet Achat Rapide.
 *
 * <p>Chaque joueur peut remplacer, pour lui seul, la selection par defaut de
 * l'admin (les articles marques {@code quick: true} en config) par ses propres
 * favoris. Un article est identifie par {@link ShopItem#getLabel()} : une cle
 * deja stable et unique par article ({@code item.<id>} ou {@code potion.<nom>}),
 * donc rien de nouveau a ajouter a {@link ShopItem} pour ca.
 *
 * <p>Meme discipline de threading que {@code stats.StatsManager} : le cache
 * n'est modifie que sur le thread principal, toute I/O passe par un unique
 * thread dedie. Stockage toujours en YAML ({@code quickbuy.yml}), meme si
 * {@code storage.type: mysql} est choisi pour les statistiques : c'est une
 * preference de confort, pas une donnee a partager entre serveurs.
 */
public class QuickBuyManager {

    private static final String ROOT = "players";

    private final Main main;
    private final ExecutorService io;
    private final ConcurrentHashMap<UUID, Set<String>> cache =
            new ConcurrentHashMap<UUID, Set<String>>();

    private File file;
    private FileConfiguration config;

    public QuickBuyManager(Main main) {
        this.main = main;
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AntWars-QuickBuy");
                thread.setDaemon(true);
                return thread;
            }
        });
        io.submit(new Runnable() {
            @Override
            public void run() {
                file = new File(main.getDataFolder(), "quickbuy.yml");
                config = Yaml.load(file);
            }
        });
    }

    // ================= Chargement / dechargement =================

    /** Charge les favoris d'un joueur a sa connexion (asynchrone). */
    public void load(Player player) {
        final UUID uuid = player.getUniqueId();
        io.submit(new Runnable() {
            @Override
            public void run() {
                cache.put(uuid, read(uuid));
            }
        });
    }

    /** Sauve puis oublie un joueur a sa deconnexion. */
    public void unload(Player player) {
        save(player.getUniqueId());
        cache.remove(player.getUniqueId());
    }

    // ================= Lecture et bascule (thread principal) =================

    /** Favoris actuels d'un joueur connecte. Vide = le joueur n'a jamais personnalise. */
    public Set<String> getFavorites(Player player) {
        Set<String> favorites = cache.get(player.getUniqueId());
        return favorites == null ? Collections.<String>emptySet() : favorites;
    }

    /**
     * Ajoute ou retire un article des favoris d'un joueur.
     *
     * @return vrai si l'article vient d'etre ajoute, faux s'il vient d'etre retire
     */
    public boolean toggle(Player player, String itemKey) {
        Set<String> favorites = live(player.getUniqueId());
        if (favorites.remove(itemKey)) {
            return false;
        }
        favorites.add(itemKey);
        return true;
    }

    private Set<String> live(UUID uuid) {
        Set<String> favorites = cache.get(uuid);
        if (favorites == null) {
            favorites = new LinkedHashSet<String>();
            cache.put(uuid, favorites);
        }
        return favorites;
    }

    // ================= Persistance =================

    /** Programme la sauvegarde d'un joueur (copie remise au thread d'I/O). */
    public void save(final UUID uuid) {
        Set<String> favorites = cache.get(uuid);
        if (favorites == null) {
            return;
        }
        final Set<String> copy = new LinkedHashSet<String>(favorites);
        io.submit(new Runnable() {
            @Override
            public void run() {
                write(uuid, copy);
            }
        });
    }

    /** Sauve tout le cache. Appele a la desactivation du plugin. */
    public void shutdown() {
        for (UUID uuid : cache.keySet()) {
            save(uuid);
        }
        io.shutdown();
        try {
            io.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ================= Interne (thread d'I/O uniquement) =================

    private Set<String> read(UUID uuid) {
        Set<String> favorites = new LinkedHashSet<String>();
        if (config == null) {
            return favorites;
        }
        List<String> stored = config.getStringList(ROOT + "." + uuid);
        favorites.addAll(stored);
        return favorites;
    }

    private void write(UUID uuid, Set<String> favorites) {
        if (config == null || file == null) {
            return;
        }
        config.set(ROOT + "." + uuid, new java.util.ArrayList<String>(favorites));
        try {
            config.save(file);
        } catch (IOException e) {
            main.getLogger().severe("Impossible d'ecrire quickbuy.yml : " + e.getMessage());
        }
    }
}
