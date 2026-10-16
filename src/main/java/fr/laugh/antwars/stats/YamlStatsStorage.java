package fr.laugh.antwars.stats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import fr.laugh.antwars.Main;

import fr.laugh.antwars.compat.Yaml;

/**
 * Stockage local des stats dans {@code stats.yml}, sur le modele d'arenes.yml :
 * zero dependance, fonctionne sur toutes les versions. Structure :
 *
 * <pre>
 * stats:
 *   &lt;uuid&gt;:
 *     name: ...
 *     games: 0
 *     wins: 0
 *     ...
 * </pre>
 *
 * <p>Toutes les operations tournent sur le thread d'I/O de {@link StatsManager} :
 * le fichier charge en memoire n'est donc touche que par ce seul thread.
 */
public class YamlStatsStorage implements StatsStorage {

    private static final String ROOT = "stats";

    private final Main main;
    private File file;
    private FileConfiguration config;

    public YamlStatsStorage(Main main) {
        this.main = main;
    }

    @Override
    public void init() {
        file = new File(main.getDataFolder(), "stats.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        config = Yaml.load(file);
        main.getLogger().info("Statistiques : stockage local (stats.yml).");
    }

    @Override
    public PlayerStats load(UUID uuid, String name) {
        PlayerStats stats = read(uuid.toString());
        if (stats == null) {
            return new PlayerStats(uuid, name);
        }
        // Le nom peut avoir change depuis la derniere partie.
        stats.setName(name);
        return stats;
    }

    @Override
    public PlayerStats loadByName(String name) {
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) {
            return null;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section != null && name.equalsIgnoreCase(section.getString("name"))) {
                return read(key);
            }
        }
        return null;
    }

    @Override
    public void save(PlayerStats stats) {
        String path = ROOT + "." + stats.getUuid().toString();
        config.set(path + ".name", stats.getName());
        config.set(path + ".games", stats.getGames());
        config.set(path + ".wins", stats.getWins());
        config.set(path + ".losses", stats.getLosses());
        config.set(path + ".kills", stats.getKills());
        config.set(path + ".deaths", stats.getDeaths());
        config.set(path + ".rating", stats.getRating());
        flush();
    }

    @Override
    public List<PlayerStats> top(final String column, int limit) {
        List<PlayerStats> all = new ArrayList<PlayerStats>();
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root != null) {
            for (String key : root.getKeys(false)) {
                PlayerStats stats = read(key);
                if (stats != null) {
                    all.add(stats);
                }
            }
        }
        Collections.sort(all, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats a, PlayerStats b) {
                return Integer.compare(value(b, column), value(a, column));
            }
        });
        return all.subList(0, Math.min(limit, all.size()));
    }

    @Override
    public void close() {
        flush();
    }

    // ================= Interne =================

    private PlayerStats read(String uuidKey) {
        ConfigurationSection section = config.getConfigurationSection(ROOT + "." + uuidKey);
        if (section == null) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new PlayerStats(uuid, section.getString("name", uuidKey),
                section.getInt("games"), section.getInt("wins"), section.getInt("losses"),
                section.getInt("kills"), section.getInt("deaths"), section.getInt("rating", 1000));
    }

    private static int value(PlayerStats stats, String column) {
        if ("wins".equals(column)) {
            return stats.getWins();
        }
        if ("losses".equals(column)) {
            return stats.getLosses();
        }
        if ("kills".equals(column)) {
            return stats.getKills();
        }
        if ("deaths".equals(column)) {
            return stats.getDeaths();
        }
        if ("rating".equals(column)) {
            return stats.getRating();
        }
        return stats.getGames();
    }

    private void flush() {
        try {
            config.save(file);
        } catch (IOException e) {
            main.getLogger().severe("Impossible d'ecrire stats.yml : " + e.getMessage());
        }
    }
}
