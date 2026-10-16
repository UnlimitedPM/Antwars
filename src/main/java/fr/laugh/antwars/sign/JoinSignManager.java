package fr.laugh.antwars.sign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.State;
import fr.laugh.antwars.lang.Messages;

import fr.laugh.antwars.compat.Yaml;

/**
 * Panneaux de connexion : creation, persistance ({@code joinsigns.yml}),
 * affichage en direct (etat + nombre de joueurs) et clic pour rejoindre.
 *
 * <p>Un panneau ciblant une arene precise affiche son etat live ; un panneau
 * <b>aleatoire</b> envoie vers l'arene joignable la plus remplie, pour concentrer
 * les joueurs sur les parties qui vont bientot demarrer.
 *
 * <p>La detection "est-ce un panneau" passe par {@code instanceof Sign}, jamais
 * par un nom de materiau : c'est ce qui marche du 1.8 (SIGN/WALL_SIGN) au 1.21
 * (OAK_SIGN, etc.) sans distinction.
 */
public class JoinSignManager {

    /** En-tete fixe (marque), non traduit. */
    private static final String HEADER =
            ChatColor.translateAlternateColorCodes('&', "&1&l[AntWars]");

    private final Main main;
    private final Map<String, JoinSign> signs = new LinkedHashMap<String, JoinSign>();

    // Panneaux dont le monde n'est pas encore charge (gestionnaire de mondes qui
    // se charge apres AntWars) : gardes tels quels, reecrits a chaque sauvegarde
    // et actives par resolvePending des que leur monde arrive.
    private final List<Map<String, Object>> pendingSigns = new ArrayList<Map<String, Object>>();

    private File file;
    private FileConfiguration config;

    public JoinSignManager(Main main) {
        this.main = main;
        load();
    }

    // ================= Enregistrement =================

    public void register(Location location, String target) {
        signs.put(key(location), new JoinSign(location, target));
        save();
    }

    public void unregister(Location location) {
        if (signs.remove(key(location)) != null) {
            save();
        }
    }

    /** Panneau de connexion a cette position, ou {@code null}. */
    public JoinSign get(Location location) {
        return signs.get(key(location));
    }

    // ================= Clic : rejoindre =================

    public void join(Player player, JoinSign sign) {
        Arena current = main.getArenaManager().getArenaOf(player);
        if (current != null) {
            player.sendMessage(Main.PREFIX
                    + Messages.tr("command.already_in_arena", "arena", current.getName()));
            return;
        }
        Arena target = sign.isRandom() ? bestRandom() : main.getArenaManager().getArena(sign.getTarget());
        if (target == null) {
            player.sendMessage(Main.PREFIX + Messages.tr("sign.no_game"));
            return;
        }
        String problem = target.join(player);
        if (problem != null) {
            player.sendMessage(Main.PREFIX + Messages.tr("command.join_failed", "reason", problem));
        }
    }

    /** Arene joignable la plus remplie (remplit les parties presque completes d'abord). */
    private Arena bestRandom() {
        Arena best = null;
        int bestCount = -1;
        for (Arena arena : main.getArenaManager().getArenas()) {
            if (arena.isJoinable() && arena.getPlayerCount() > bestCount) {
                best = arena;
                bestCount = arena.getPlayerCount();
            }
        }
        return best;
    }

    // ================= Affichage =================

    public void updateAll() {
        List<Location> gone = new ArrayList<Location>();
        for (JoinSign sign : signs.values()) {
            Location loc = sign.getLocation();
            if (loc.getWorld() == null
                    || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            BlockState state = loc.getBlock().getState();
            if (!(state instanceof Sign)) {
                // Le panneau a disparu (bloc support casse, WorldEdit...) : on l'oublie.
                gone.add(loc);
                continue;
            }
            Sign board = (Sign) state;
            String[] lines = format(sign);
            for (int i = 0; i < 4; i++) {
                board.setLine(i, lines[i]);
            }
            board.update(true);
        }
        if (!gone.isEmpty()) {
            for (Location loc : gone) {
                signs.remove(key(loc));
            }
            save();
        }
    }

    public String[] format(JoinSign sign) {
        String[] lines = new String[4];
        lines[0] = HEADER;
        if (sign.isRandom()) {
            int players = 0;
            int max = 0;
            int count = 0;
            for (Arena arena : main.getArenaManager().getArenas()) {
                if (arena.isJoinable()) {
                    players += arena.getPlayerCount();
                    max += arena.getMaxPlayers();
                    count++;
                }
            }
            lines[1] = ChatColor.LIGHT_PURPLE + Messages.tr("sign.random");
            lines[2] = count > 0
                    ? ChatColor.GREEN + Messages.tr("sign.join_action")
                    : ChatColor.GRAY + Messages.tr("sign.state.unavailable");
            lines[3] = count(players, max);
        } else {
            Arena arena = main.getArenaManager().getArena(sign.getTarget());
            if (arena == null) {
                lines[1] = ChatColor.WHITE + sign.getTarget();
                lines[2] = ChatColor.GRAY + Messages.tr("sign.state.unavailable");
                lines[3] = "";
            } else {
                lines[1] = ChatColor.WHITE + arena.getName();
                lines[2] = stateLine(arena);
                lines[3] = count(arena.getPlayerCount(), arena.getMaxPlayers());
            }
        }
        return lines;
    }

    private String stateLine(Arena arena) {
        if (!arena.isConfigured()) {
            return ChatColor.GRAY + Messages.tr("sign.state.unavailable");
        }
        if (arena.isRunning() || arena.isState(State.FINISH)) {
            return ChatColor.RED + Messages.tr("sign.state.running");
        }
        if (arena.isFull()) {
            return ChatColor.RED + Messages.tr("sign.state.full");
        }
        if (arena.isState(State.STARTING)) {
            return ChatColor.YELLOW + Messages.tr("sign.state.starting");
        }
        return ChatColor.GREEN + Messages.tr("sign.state.waiting");
    }

    private String count(int current, int max) {
        return ChatColor.YELLOW.toString() + current + ChatColor.GRAY + "/" + ChatColor.YELLOW + max;
    }

    // ================= Persistance =================

    public void load() {
        signs.clear();
        pendingSigns.clear();
        file = new File(main.getDataFolder(), "joinsigns.yml");
        config = Yaml.load(file);
        ConfigurationSection root = config.getConfigurationSection("signs");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            World world = Bukkit.getWorld(section.getString("world", ""));
            if (world == null) {
                pendingSigns.add(new LinkedHashMap<String, Object>(section.getValues(false)));
                continue;
            }
            Location loc = new Location(world,
                    section.getInt("x"), section.getInt("y"), section.getInt("z"));
            signs.put(key(loc), new JoinSign(loc, section.getString("target", "")));
        }
    }

    public void save() {
        if (config == null || file == null) {
            return;
        }
        config.set("signs", null);
        int i = 0;
        for (JoinSign sign : signs.values()) {
            Location loc = sign.getLocation();
            String path = "signs." + i;
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getBlockX());
            config.set(path + ".y", loc.getBlockY());
            config.set(path + ".z", loc.getBlockZ());
            config.set(path + ".target", sign.getTarget());
            i++;
        }
        for (Map<String, Object> raw : pendingSigns) {
            config.set("signs." + i, raw);
            i++;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            main.getLogger().severe("Impossible d'ecrire joinsigns.yml : " + e.getMessage());
        }
    }

    /** Active les panneaux en attente d'un monde qui vient de se charger. */
    public void resolvePending(World world) {
        for (Iterator<Map<String, Object>> it = pendingSigns.iterator(); it.hasNext();) {
            Map<String, Object> raw = it.next();
            if (!world.getName().equals(String.valueOf(raw.get("world")))) {
                continue;
            }
            it.remove();
            Location loc = new Location(world, number(raw.get("x")), number(raw.get("y")), number(raw.get("z")));
            Object target = raw.get("target");
            signs.put(key(loc), new JoinSign(loc, target == null ? "" : String.valueOf(target)));
        }
    }

    private static int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":"
                + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
