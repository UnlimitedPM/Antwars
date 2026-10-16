package fr.laugh.antwars.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;

import fr.laugh.antwars.Main;

/**
 * Stockage des stats en base MySQL / MariaDB.
 *
 * <p>Le pilote est le connecteur MariaDB embarque dans le jar et relocalise
 * (voir le pom) ; on l'instancie directement ({@code new Driver()}), ce qui
 * evite les soucis de {@code DriverManager} avec le classloader d'un plugin, et
 * la relocalisation du shade-plugin fait pointer la reference vers le bon
 * package a la compilation.
 *
 * <p>Cette classe n'est chargee que si {@code storage.type: mysql} : un serveur
 * en mode local ne touche donc jamais au pilote (utile car un serveur 1.8-1.16
 * tourne en Java 8, ou le pilote doit lui aussi etre du bytecode Java 8 - ce que
 * la verification de compatibilite confirme).
 *
 * <p>Toutes les operations tournent sur l'unique thread d'I/O de
 * {@link StatsManager}, qui possede donc seul la connexion : pas de pool ni de
 * verrou a gerer.
 */
public class MySqlStatsStorage implements StatsStorage {

    private final Main main;
    private final org.mariadb.jdbc.Driver driver = new org.mariadb.jdbc.Driver();

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final String table;

    private Connection connection;

    public MySqlStatsStorage(Main main) {
        this.main = main;
        ConfigurationSection section = main.getConfig().getConfigurationSection("storage.mysql");
        if (section == null) {
            section = main.getConfig().createSection("storage.mysql");
        }
        this.host = section.getString("host", "localhost");
        this.port = section.getInt("port", 3306);
        this.database = section.getString("database", "antwars");
        this.user = section.getString("user", "root");
        this.password = section.getString("password", "");
        this.table = safeTable(section.getString("table", "antwars_stats"));
    }

    @Override
    public void init() throws SQLException {
        Connection conn = connection();
        String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "name VARCHAR(16),"
                + "games INT NOT NULL DEFAULT 0,"
                + "wins INT NOT NULL DEFAULT 0,"
                + "losses INT NOT NULL DEFAULT 0,"
                + "kills INT NOT NULL DEFAULT 0,"
                + "deaths INT NOT NULL DEFAULT 0,"
                + "rating INT NOT NULL DEFAULT 1000)";
        Statement statement = conn.createStatement();
        try {
            statement.executeUpdate(sql);
            // ALTER inoffensif sur une table deja existante creee avant l'ajout du
            // classement ELO : IF NOT EXISTS evite l'erreur si la colonne est deja la.
            statement.executeUpdate("ALTER TABLE `" + table
                    + "` ADD COLUMN IF NOT EXISTS rating INT NOT NULL DEFAULT 1000");
        } finally {
            statement.close();
        }
        main.getLogger().info("Statistiques : stockage MySQL (" + host + ":" + port
                + "/" + database + ", table " + table + ").");
    }

    @Override
    public PlayerStats load(UUID uuid, String name) {
        String sql = "SELECT games,wins,losses,kills,deaths,rating FROM `" + table + "` WHERE uuid=?";
        try {
            Connection conn = connection();
            PreparedStatement statement = conn.prepareStatement(sql);
            try {
                statement.setString(1, uuid.toString());
                ResultSet rs = statement.executeQuery();
                try {
                    if (rs.next()) {
                        return new PlayerStats(uuid, name,
                                rs.getInt("games"), rs.getInt("wins"), rs.getInt("losses"),
                                rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("rating"));
                    }
                } finally {
                    rs.close();
                }
            } finally {
                statement.close();
            }
        } catch (SQLException e) {
            main.getLogger().severe("Stats MySQL (load) : " + e.getMessage());
        }
        // Joueur inconnu ou erreur : on repart de zero plutot que de planter.
        return new PlayerStats(uuid, name);
    }

    @Override
    public PlayerStats loadByName(String name) {
        String sql = "SELECT uuid,games,wins,losses,kills,deaths,rating FROM `" + table
                + "` WHERE name=? LIMIT 1";
        try {
            Connection conn = connection();
            PreparedStatement statement = conn.prepareStatement(sql);
            try {
                statement.setString(1, name);
                ResultSet rs = statement.executeQuery();
                try {
                    if (rs.next()) {
                        return new PlayerStats(UUID.fromString(rs.getString("uuid")), name,
                                rs.getInt("games"), rs.getInt("wins"), rs.getInt("losses"),
                                rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("rating"));
                    }
                } finally {
                    rs.close();
                }
            } finally {
                statement.close();
            }
        } catch (SQLException e) {
            main.getLogger().severe("Stats MySQL (loadByName) : " + e.getMessage());
        }
        return null;
    }

    @Override
    public void save(PlayerStats stats) {
        String sql = "INSERT INTO `" + table + "` (uuid,name,games,wins,losses,kills,deaths,rating)"
                + " VALUES (?,?,?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE name=VALUES(name),games=VALUES(games),"
                + "wins=VALUES(wins),losses=VALUES(losses),kills=VALUES(kills),deaths=VALUES(deaths),"
                + "rating=VALUES(rating)";
        try {
            Connection conn = connection();
            PreparedStatement statement = conn.prepareStatement(sql);
            try {
                statement.setString(1, stats.getUuid().toString());
                statement.setString(2, stats.getName());
                statement.setInt(3, stats.getGames());
                statement.setInt(4, stats.getWins());
                statement.setInt(5, stats.getLosses());
                statement.setInt(6, stats.getKills());
                statement.setInt(7, stats.getDeaths());
                statement.setInt(8, stats.getRating());
                statement.executeUpdate();
            } finally {
                statement.close();
            }
        } catch (SQLException e) {
            main.getLogger().severe("Stats MySQL (save) : " + e.getMessage());
        }
    }

    @Override
    public List<PlayerStats> top(String column, int limit) {
        List<PlayerStats> list = new ArrayList<PlayerStats>();
        // column est deja valide par l'appelant (liste blanche StatsStorage), mais
        // on ne le passe jamais en parametre prepare : c'est un nom de colonne.
        String sql = "SELECT uuid,name,games,wins,losses,kills,deaths,rating FROM `" + table
                + "` ORDER BY `" + column + "` DESC LIMIT ?";
        try {
            Connection conn = connection();
            PreparedStatement statement = conn.prepareStatement(sql);
            try {
                statement.setInt(1, limit);
                ResultSet rs = statement.executeQuery();
                try {
                    while (rs.next()) {
                        list.add(new PlayerStats(UUID.fromString(rs.getString("uuid")),
                                rs.getString("name"),
                                rs.getInt("games"), rs.getInt("wins"), rs.getInt("losses"),
                                rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("rating")));
                    }
                } finally {
                    rs.close();
                }
            } finally {
                statement.close();
            }
        } catch (SQLException e) {
            main.getLogger().severe("Stats MySQL (top) : " + e.getMessage());
        }
        return list;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Fermeture : rien a faire de plus.
            }
            connection = null;
        }
    }

    // ================= Interne =================

    /** Ouvre la connexion si besoin (premiere fois, ou apres une coupure). */
    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
            connection = driver.connect(url, props);
            if (connection == null) {
                throw new SQLException("Le pilote MariaDB refuse l'URL : " + url);
            }
        }
        return connection;
    }

    /** N'accepte qu'un nom de table alphanumerique, pour l'interpoler sans risque. */
    private static String safeTable(String raw) {
        return raw != null && raw.matches("[A-Za-z0-9_]+") ? raw : "antwars_stats";
    }
}
