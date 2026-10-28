package fr.laugh.antwars;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import fr.laugh.antwars.compat.Yaml;

/**
 * Chargement, sauvegarde et recherche des arenes, regroupees sous la section
 * {@code arenes:} de arenes.yml (pour ne pas les confondre avec une cle de config).
 */
public class ArenaManager {

    private static final String ROOT = "arenes";

    private final Main main;
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    // Joueurs deconnectes en partie qui n'ont pas pu revenir (lit detruit ou partie
    // finie) : a leur reconnexion ils reapparaissent au milieu d'une map de jeu,
    // il faut les renvoyer au lobby. Memoire seulement (perdue au redemarrage,
    // sans gravite : le reset de connexion s'en charge alors).
    private final Set<UUID> stranded = new HashSet<UUID>();
    // Positions lues dans arenes.yml dont le monde n'etait pas encore charge
    // (monde gere par Multiverse ou autre, charge apres AntWars). Elles sont
    // gardees telles quelles : reecrites a chaque sauvegarde, et appliquees a
    // l'arene des que leur monde se charge. Sans cela, la premiere sauvegarde
    // (au plus tard l'arret du serveur) les effacait du fichier.
    private final List<PendingLocation> pending = new ArrayList<PendingLocation>();

    private File arenasFile;
    private FileConfiguration arenasConfig;

    public ArenaManager(Main main) {
        this.main = main;
        load();
    }

    // ================= Chargement et sauvegarde =================

    public void load() {
        arenas.clear();
        pending.clear();

        arenasFile = new File(main.getDataFolder(), "arenes.yml");
        if (!arenasFile.exists()) {
            arenasFile.getParentFile().mkdirs();
            try {
                main.saveResource("arenes.yml", false);
            } catch (IllegalArgumentException e) {
                // Ressource absente du jar : on part d'un fichier vide plutot
                // que de faire echouer le demarrage du plugin.
                main.getLogger().warning("arenes.yml introuvable dans le jar, creation d'un fichier vide.");
            }
        }
        arenasConfig = Yaml.load(arenasFile);

        ConfigurationSection root = arenasConfig.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String name = section.getString("nom", key);
            int minPlayers = section.getInt("minPlayers", 2);
            int maxPlayers = section.getInt("maxPlayers", 8);
            int teamCount = section.getInt("teamCount", 2);
            int playersPerTeam = section.getInt("playersPerTeam", 4);
            // Temps des bordures propre a l'arene ; a defaut, la valeur globale.
            int borderTime = section.getInt("borderTime", main.getGameDuration());
            boolean allowSpectators = section.getBoolean("allowSpectators", false);
            Location lobby = readLocation(section.getConfigurationSection("lobby"),
                    name, PendingLocation.LOBBY, null);

            Map<String, Location> spawnPoints = new LinkedHashMap<String, Location>();
            ConfigurationSection spawnSection = section.getConfigurationSection("spawnPoints");
            if (spawnSection != null) {
                for (String teamName : Arena.TEAM_NAMES) {
                    Location spawn = readLocation(spawnSection.getConfigurationSection(teamName),
                            name, PendingLocation.SPAWN, teamName);
                    if (spawn != null) {
                        spawnPoints.put(teamName, spawn);
                    }
                }
            }

            // Emplacement des lits : deux moities (pied et tete) par equipe.
            Map<String, Location> bedFeet = new LinkedHashMap<String, Location>();
            Map<String, Location> bedHeads = new LinkedHashMap<String, Location>();
            ConfigurationSection bedSection = section.getConfigurationSection("beds");
            if (bedSection != null) {
                for (String teamName : Arena.TEAM_NAMES) {
                    ConfigurationSection teamBed = bedSection.getConfigurationSection(teamName);
                    if (teamBed == null) {
                        continue;
                    }
                    Location foot = readLocation(teamBed.getConfigurationSection("foot"),
                            name, PendingLocation.BED_FOOT, teamName);
                    Location head = readLocation(teamBed.getConfigurationSection("head"),
                            name, PendingLocation.BED_HEAD, teamName);
                    if (foot != null && head != null) {
                        bedFeet.put(teamName, foot);
                        bedHeads.put(teamName, head);
                    }
                }
            }

            Arena arena = new Arena(main, name, minPlayers, maxPlayers, teamCount,
                    playersPerTeam, lobby, spawnPoints, bedFeet, bedHeads);
            arena.setBorderTime(borderTime);
            arena.setAllowSpectators(allowSpectators);

            // Centre de la bordure de deathmatch, distinct du lobby si besoin.
            ConfigurationSection borderSection = section.getConfigurationSection("borderCenter");
            if (borderSection != null) {
                World borderWorld = Bukkit.getWorld(borderSection.getString("world", "world"));
                if (borderWorld != null) {
                    arena.setBorderCenter(new Location(borderWorld,
                            borderSection.getDouble("x"), 0d, borderSection.getDouble("z")));
                }
            }

            ConfigurationSection npcSection = section.getConfigurationSection("shopNpcs");
            if (npcSection != null) {
                for (String npcKey : npcSection.getKeys(false)) {
                    Location npc = readLocation(npcSection.getConfigurationSection(npcKey),
                            name, PendingLocation.NPC, null);
                    if (npc != null) {
                        arena.addShopNpc(npc);
                    }
                }
            }

            arenas.put(name.toLowerCase(), arena);
        }

        main.getLogger().info(arenas.size() + " arene(s) chargee(s).");
        if (!pending.isEmpty()) {
            Set<String> worlds = new HashSet<String>();
            for (PendingLocation p : pending) {
                worlds.add(p.world);
            }
            main.getLogger().warning("Monde(s) pas encore charge(s) : " + worlds + ". " + pending.size()
                    + " position(s) conservee(s), reprises des que le monde sera charge.");
        }
    }

    /**
     * Applique les positions en attente d'un monde qui vient de se charger.
     * Une position redefinie entre-temps par l'admin reste prioritaire.
     */
    public void resolvePending(World world) {
        int resolved = 0;
        // Copie : un lit retire deux entrees a la fois (pied et tete).
        for (PendingLocation p : new ArrayList<PendingLocation>(pending)) {
            if (!p.world.equals(world.getName()) || !pending.contains(p)) {
                continue;
            }
            Arena arena = getArena(p.arena);
            if (PendingLocation.BED_HEAD.equals(p.kind)) {
                // Traitee avec son pied : une tete orpheline (pied absent du fichier)
                // reste simplement en attente.
                continue;
            }
            if (PendingLocation.BED_FOOT.equals(p.kind)) {
                // Un lit n'existe que s'il a ses DEUX moities : on cherche la tete.
                PendingLocation head = findPending(p.arena, PendingLocation.BED_HEAD, p.team, p.world);
                if (head == null) {
                    continue;
                }
                pending.remove(p);
                pending.remove(head);
                if (arena != null && !arena.hasBed(p.team)) {
                    arena.setBed(p.team, p.toLocation(world), head.toLocation(world));
                    resolved++;
                }
                continue;
            }
            pending.remove(p);
            if (arena == null) {
                continue;
            }
            Location location = p.toLocation(world);
            if (PendingLocation.LOBBY.equals(p.kind)) {
                if (arena.getLobby() == null) {
                    arena.setLobby(location);
                    resolved++;
                }
            } else if (PendingLocation.SPAWN.equals(p.kind)) {
                if (!arena.getSpawnPoints().containsKey(p.team)) {
                    arena.setSpawnPoint(p.team, location);
                    resolved++;
                }
            } else if (PendingLocation.NPC.equals(p.kind)) {
                arena.addShopNpc(location);
                resolved++;
            }
        }
        if (resolved > 0) {
            main.getLogger().info(resolved + " position(s) reprise(s) dans le monde " + world.getName() + ".");
        }
    }

    private PendingLocation findPending(String arena, String kind, String team, String world) {
        for (PendingLocation p : pending) {
            if (p.arena.equalsIgnoreCase(arena) && p.kind.equals(kind) && p.world.equals(world)
                    && (team == null || team.equals(p.team))) {
                return p;
            }
        }
        return null;
    }

    /**
     * Oublie les NPC de boutique en attente d'une arene : l'admin vient de les
     * vider, ils ne doivent pas revenir quand leur monde se chargera.
     */
    public void forgetPendingNpcs(String arenaName) {
        for (Iterator<PendingLocation> it = pending.iterator(); it.hasNext();) {
            PendingLocation p = it.next();
            if (p.arena.equalsIgnoreCase(arenaName) && PendingLocation.NPC.equals(p.kind)) {
                it.remove();
            }
        }
    }

    public void save() {
        if (arenasConfig == null || arenasFile == null) {
            return;
        }
        // On repart d'une section vierge pour que la suppression d'une arene
        // ou d'un point d'apparition soit reellement repercutee sur le disque.
        arenasConfig.set(ROOT, null);

        for (Arena arena : arenas.values()) {
            String path = ROOT + "." + arena.getName();
            arenasConfig.set(path + ".nom", arena.getName());
            arenasConfig.set(path + ".minPlayers", arena.getMinPlayers());
            arenasConfig.set(path + ".maxPlayers", arena.getMaxPlayers());
            arenasConfig.set(path + ".teamCount", arena.getTeamCount());
            arenasConfig.set(path + ".playersPerTeam", arena.getPlayersPerTeam());
            arenasConfig.set(path + ".borderTime", arena.getBorderTime());
            arenasConfig.set(path + ".allowSpectators", arena.isAllowSpectators());

            writeLocation(path + ".lobby", arena.getLobby());
            if (arena.getBorderCenter() != null) {
                writeLocation(path + ".borderCenter", arena.getBorderCenter());
            }
            for (Map.Entry<String, Location> entry : arena.getSpawnPoints().entrySet()) {
                writeLocation(path + ".spawnPoints." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Location> entry : arena.getBedFeet().entrySet()) {
                String teamName = entry.getKey();
                writeLocation(path + ".beds." + teamName + ".foot", entry.getValue());
                writeLocation(path + ".beds." + teamName + ".head", arena.getBedHead(teamName));
            }
            int npcIndex = 0;
            for (Location npc : arena.getShopNpcLocations()) {
                writeLocation(path + ".shopNpcs." + npcIndex, npc);
                npcIndex++;
            }

            // Positions dont le monde n'est pas charge : recopiees telles quelles,
            // sauf si l'admin a defini entre-temps une position qui les remplace.
            for (PendingLocation p : pending) {
                if (!p.arena.equalsIgnoreCase(arena.getName())) {
                    continue;
                }
                if (PendingLocation.LOBBY.equals(p.kind)) {
                    if (arena.getLobby() == null) {
                        p.write(arenasConfig, path + ".lobby");
                    }
                } else if (PendingLocation.SPAWN.equals(p.kind)) {
                    if (!arena.getSpawnPoints().containsKey(p.team)) {
                        p.write(arenasConfig, path + ".spawnPoints." + p.team);
                    }
                } else if (PendingLocation.BED_FOOT.equals(p.kind)) {
                    if (!arena.hasBed(p.team)) {
                        p.write(arenasConfig, path + ".beds." + p.team + ".foot");
                    }
                } else if (PendingLocation.BED_HEAD.equals(p.kind)) {
                    if (!arena.hasBed(p.team)) {
                        p.write(arenasConfig, path + ".beds." + p.team + ".head");
                    }
                } else if (PendingLocation.NPC.equals(p.kind)) {
                    p.write(arenasConfig, path + ".shopNpcs." + npcIndex);
                    npcIndex++;
                }
            }
        }

        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            main.getLogger().severe("Impossible d'ecrire arenes.yml : " + e.getMessage());
        }
    }

    // ================= Acces =================

    public Arena getArena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public List<String> getArenaNames() {
        List<String> names = new ArrayList<String>();
        for (Arena arena : arenas.values()) {
            names.add(arena.getName());
        }
        return names;
    }

    public Arena getArenaOf(Player player) {
        for (Arena arena : arenas.values()) {
            if (arena.contains(player)) {
                return arena;
            }
        }
        return null;
    }

    /** Arene en cours ou ce joueur deconnecte peut reprendre sa place, ou {@code null}. */
    public Arena getRejoinableArena(UUID id) {
        for (Arena arena : arenas.values()) {
            if (arena.canRejoin(id)) {
                return arena;
            }
        }
        return null;
    }

    /** Signale un deconnecte qui a perdu sa place : a sa reconnexion, retour au lobby. */
    public void markStranded(UUID id) {
        stranded.add(id);
    }

    /** Vrai (une seule fois) si ce joueur doit etre renvoye au lobby a sa connexion. */
    public boolean consumeStranded(UUID id) {
        return stranded.remove(id);
    }

    /**
     * Nom d'arene autorise pour la saisie depuis le menu : lettres, chiffres, _ et
     * -, 16 caracteres maximum. Un point casserait le chemin YAML de l'arene, et
     * 16 caracteres tiennent sur un panneau et dans un scoreboard.
     */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,16}");
    }

    public boolean createArena(String name) {
        if (getArena(name) != null) {
            return false;
        }
        // Defaut : 4 equipes de 4 (max 16). L'admin descend a 2 equipes pour un
        // duel via le menu de configuration (bouton "Nombre d'equipes").
        Arena arena = new Arena(main, name, 2, 16, 4, 4, null, null, null, null);
        arena.setBorderTime(main.getGameDuration());
        arenas.put(name.toLowerCase(), arena);
        save();
        return true;
    }

    public boolean deleteArena(String name) {
        Arena arena = getArena(name);
        if (arena == null) {
            return false;
        }
        arena.reset();
        arenas.remove(name.toLowerCase());
        for (Iterator<PendingLocation> it = pending.iterator(); it.hasNext();) {
            if (it.next().arena.equalsIgnoreCase(name)) {
                it.remove();
            }
        }
        save();
        return true;
    }

    public void leaveArena(Player player, boolean teleportToLobby) {
        Arena arena = getArenaOf(player);
        if (arena != null) {
            arena.leave(player, teleportToLobby);
        }
    }

    /** Remet toutes les arenes a zero, typiquement a l'arret du plugin. */
    public void resetAll() {
        for (Arena arena : arenas.values()) {
            arena.reset();
        }
    }

    // ================= Serialisation des positions =================

    /**
     * Lit une position. Si son monde n'est pas (encore) charge, elle est mise
     * en attente dans {@link #pending} et la methode renvoie {@code null}.
     */
    private Location readLocation(ConfigurationSection section, String arena, String kind, String team) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        PendingLocation raw = new PendingLocation(arena, kind, team, worldName,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            pending.add(raw);
            return null;
        }
        return raw.toLocation(world);
    }

    private void writeLocation(String path, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        arenasConfig.set(path + ".world", location.getWorld().getName());
        arenasConfig.set(path + ".x", location.getX());
        arenasConfig.set(path + ".y", location.getY());
        arenasConfig.set(path + ".z", location.getZ());
        arenasConfig.set(path + ".yaw", location.getYaw());
        arenasConfig.set(path + ".pitch", location.getPitch());
    }

    /** Position brute (nom du monde + coordonnees) en attente de son monde. */
    private static final class PendingLocation {
        static final String LOBBY = "lobby";
        static final String SPAWN = "spawn";
        static final String BED_FOOT = "bedfoot";
        static final String BED_HEAD = "bedhead";
        static final String NPC = "npc";

        final String arena;
        final String kind;
        final String team;
        final String world;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        PendingLocation(String arena, String kind, String team, String world,
                double x, double y, double z, float yaw, float pitch) {
            this.arena = arena;
            this.kind = kind;
            this.team = team;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        Location toLocation(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }

        void write(FileConfiguration config, String path) {
            config.set(path + ".world", world);
            config.set(path + ".x", x);
            config.set(path + ".y", y);
            config.set(path + ".z", z);
            config.set(path + ".yaw", yaw);
            config.set(path + ".pitch", pitch);
        }
    }
}
