package fr.laugh.antwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import fr.laugh.antwars.bed.BedManager;
import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.compat.Compat;
import fr.laugh.antwars.game.GameStats;
import fr.laugh.antwars.compat.PlayerSnapshot;
import fr.laugh.antwars.compat.Sounds;
import fr.laugh.antwars.compat.Titles;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.lobby.SpectatorItems;
import fr.laugh.antwars.lobby.LobbyItems;
import fr.laugh.antwars.map.MapTracker;
import fr.laugh.antwars.npc.ShopNpc;
import fr.laugh.antwars.quest.QuestType;
import fr.laugh.antwars.stats.Elo;
import fr.laugh.antwars.stats.PlayerStats;
import fr.laugh.antwars.task.Countdown;
import fr.laugh.antwars.task.GameCycle;

/**
 * Une arene AntWars : sa configuration, son etat et le deroulement de sa partie.
 *
 * <p>Les joueurs sont references par leur {@link UUID} et non par des objets
 * {@link Player} : garder une reference sur un Player deconnecte empeche le
 * ramasse-miettes de liberer son entite, ce qui fuit a chaque partie.
 */
public class Arena {

    /** Les quatre equipes possibles, dans l'ordre d'attribution. */
    public static final String[] TEAM_NAMES = { "Rouge", "Bleu", "Jaune", "Vert" };

    /** Un coup recu dans les 10 dernieres secondes = le joueur est "en combat". */
    private static final long COMBAT_TAG_MS = 10000L;
    /** Fenetre (8 s) pour enchainer des kills en serie (double, triple...). */
    private static final long STREAK_WINDOW_MS = 8000L;

    /** Un joueur deconnecte en pleine partie : son nom (il n'est plus la pour le donner) et son etat. */
    private static final class Absent {
        private final String name;
        private final PlayerSnapshot snapshot;

        private Absent(String name, PlayerSnapshot snapshot) {
            this.name = name;
            this.snapshot = snapshot;
        }
    }

    private final Main main;

    // ----- Configuration persistante -----

    private final String name;
    private int minPlayers;
    private int maxPlayers;
    private int teamCount;
    private int playersPerTeam;
    // Temps avant l'apparition des bordures (deathmatch), en secondes, propre a
    // chaque arene. Defaut 1200 (20 min) ; overridable via le menu de config.
    private int borderTime = 1200;
    /**
     * Centre de la bordure de deathmatch. {@code null} = centre du lobby.
     *
     * <p>Un lobby peut etre loin de l'arene (salle d'attente, hub local) : sans
     * ce reglage, la bordure se centrerait sur le lobby et laisserait les
     * joueurs hors de la zone de jeu. Les arenes de la map fournie ont un
     * lobby commun et un centre de bordure propre.
     */
    private Location borderCenter;
    private Location lobby;
    // Spectateurs exterieurs autorises sur cette arene (/aw spectate), eteint par
    // defaut : a activer pour les arenes d'evenement.
    private boolean allowSpectators;
    private final Map<String, Location> spawnPoints = new LinkedHashMap<String, Location>();
    // Emplacement du lit de chaque equipe, en deux blocs (pied et tete). Le lit
    // est pose d'avance sur la map via /aw setbed, puis restaure a cet endroit
    // au lancement de chaque partie. On conserve les deux moities car, si le
    // vrai lit a disparu du monde (demenage ou detruit lors d'une manche
    // precedente), il faut savoir ou reposer chacune d'elles.
    private final Map<String, Location> bedFeet = new LinkedHashMap<String, Location>();
    private final Map<String, Location> bedHeads = new LinkedHashMap<String, Location>();
    // Emplacements des NPC de boutique (armor stand ou Citizens). Poses au
    // lancement de la partie, retires a la fin ; ils ne persistent donc pas
    // entre deux manches. Alternative a la pancarte [Boutique].
    private final List<Location> shopNpcs = new ArrayList<Location>();

    // ----- Etat de la partie en cours -----

    private State state = State.WAITING;
    private final List<UUID> players = new ArrayList<UUID>();
    // Joueurs qui regardent sans jouer : ni equipe, ni stats, ni "en vie".
    private final Set<UUID> spectators = new LinkedHashSet<UUID>();
    private final Set<UUID> alive = new LinkedHashSet<UUID>();
    private final Set<UUID> startVotes = new LinkedHashSet<UUID>();
    private final Map<UUID, String> teamOf = new HashMap<UUID, String>();
    // Joueurs vivants deconnectes en pleine partie : ils ne sont plus dans
    // "players" (donc plus "en ligne" pour l'arene) mais restent dans "alive" et
    // "teamOf", et gardent leur place tant que le lit de leur equipe n'est pas
    // detruit. Equipement mis de cote en attendant leur retour.
    private final Map<UUID, Absent> disconnected = new LinkedHashMap<UUID, Absent>();
    // Compteurs de LA partie en cours (resume de fin, series, combat en cours).
    private final Map<UUID, GameStats> gameStats = new LinkedHashMap<UUID, GameStats>();
    // Protection de reapparition : instant (ms) jusqu'auquel un joueur est invulnerable.
    private final Map<UUID, Long> protectedUntil = new HashMap<UUID, Long>();
    private final BedManager beds = new BedManager(this);
    private final MapTracker mapTracker = new MapTracker();
    private BukkitTask countdownTask;
    private BukkitTask cycleTask;
    // Secondes restantes avant le deathmatch (apparition des bordures), poussees
    // par GameCycle a chaque tick pour l'affichage du scoreboard. 0 hors partie.
    private int deathmatchTimer;

    public Arena(Main main, String name, int minPlayers, int maxPlayers, int teamCount,
            int playersPerTeam, Location lobby, Map<String, Location> spawnPoints,
            Map<String, Location> bedFeet, Map<String, Location> bedHeads) {
        this.main = main;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.teamCount = teamCount;
        this.playersPerTeam = playersPerTeam;
        this.lobby = lobby;
        if (spawnPoints != null) {
            // On ne conserve que les points reellement definis : une entree a
            // null se terminerait en NullPointerException au moment de la
            // teleportation, au lancement de la partie.
            for (Map.Entry<String, Location> entry : spawnPoints.entrySet()) {
                if (entry.getValue() != null) {
                    this.spawnPoints.put(entry.getKey(), entry.getValue());
                }
            }
        }
        // On n'enregistre un lit que si ses DEUX moities sont connues : une
        // moitie seule ne permettrait ni de le reposer ni de le detecter.
        if (bedFeet != null && bedHeads != null) {
            for (Map.Entry<String, Location> entry : bedFeet.entrySet()) {
                Location head = bedHeads.get(entry.getKey());
                if (entry.getValue() != null && head != null) {
                    this.bedFeet.put(entry.getKey(), entry.getValue());
                    this.bedHeads.put(entry.getKey(), head);
                }
            }
        }
    }

    // ================= Configuration =================

    public String getName() {
        return name;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public void setTeamCount(int teamCount) {
        this.teamCount = teamCount;
    }

    public int getPlayersPerTeam() {
        return playersPerTeam;
    }

    public void setPlayersPerTeam(int playersPerTeam) {
        this.playersPerTeam = playersPerTeam;
    }

    /** Temps avant l'apparition des bordures (deathmatch) pour cette arene, en secondes. */
    public int getBorderTime() {
        return borderTime;
    }

    public void setBorderTime(int seconds) {
        this.borderTime = Math.max(10, seconds);
    }

    public Location getBorderCenter() {
        return borderCenter;
    }

    public void setBorderCenter(Location borderCenter) {
        this.borderCenter = borderCenter;
    }

    /**
     * Vrai si cette equipe (par son nom) fait partie des {@code teamCount}
     * premieres, donc reellement utilisee par le format courant. Sert au menu de
     * configuration a distinguer les equipes actives des equipes grisees.
     */
    public boolean isTeamActive(String teamName) {
        for (int i = 0; i < TEAM_NAMES.length && i < teamCount; i++) {
            if (TEAM_NAMES[i].equals(teamName)) {
                return true;
            }
        }
        return false;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public Map<String, Location> getSpawnPoints() {
        return Collections.unmodifiableMap(spawnPoints);
    }

    public void setSpawnPoint(String teamName, Location spawnPoint) {
        spawnPoints.put(teamName, spawnPoint);
    }

    public Map<String, Location> getBedFeet() {
        return Collections.unmodifiableMap(bedFeet);
    }

    public Map<String, Location> getBedHeads() {
        return Collections.unmodifiableMap(bedHeads);
    }

    /** Pied du lit configure pour une equipe, ou {@code null}. */
    public Location getBedFoot(String teamName) {
        return bedFeet.get(teamName);
    }

    /** Tete du lit configure pour une equipe, ou {@code null}. */
    public Location getBedHead(String teamName) {
        return bedHeads.get(teamName);
    }

    /** Vrai si l'emplacement du lit de cette equipe est defini (deux moities). */
    public boolean hasBed(String teamName) {
        return bedFeet.containsKey(teamName) && bedHeads.containsKey(teamName);
    }

    public void setBed(String teamName, Location foot, Location head) {
        bedFeet.put(teamName, foot);
        bedHeads.put(teamName, head);
    }

    public List<Location> getShopNpcLocations() {
        return Collections.unmodifiableList(shopNpcs);
    }

    public void addShopNpc(Location location) {
        shopNpcs.add(location);
    }

    /** Retire tous les NPC de boutique (config et exemplaires deja poses). */
    public void clearShopNpcs() {
        despawnShopNpcs();
        shopNpcs.clear();
    }

    public void spawnShopNpcs() {
        for (Location location : shopNpcs) {
            ShopNpc.spawn(location, main.getShopNpcHead());
        }
    }

    public void despawnShopNpcs() {
        for (Location location : shopNpcs) {
            ShopNpc.clearAt(location);
        }
    }

    /**
     * Equipes reellement utilisables : celles qui ont un point d'apparition
     * defini, dans la limite du nombre d'equipes configure.
     */
    public List<String> usableTeams() {
        List<String> usable = new ArrayList<String>();
        for (String teamName : TEAM_NAMES) {
            if (usable.size() >= teamCount) {
                break;
            }
            if (spawnPoints.containsKey(teamName)) {
                usable.add(teamName);
            }
        }
        return usable;
    }

    /**
     * Verifie que l'arene est jouable.
     *
     * @return la raison du refus, ou {@code null} si l'arene est valide
     */
    public String validate() {
        if (lobby == null) {
            return Messages.tr("arena.validate.no_lobby", "arena", name);
        }
        if (teamCount < 2 || teamCount > 4) {
            return Messages.tr("arena.validate.teams_range");
        }
        if (playersPerTeam <= 0) {
            return Messages.tr("arena.validate.per_team_positive");
        }
        if (usableTeams().size() < teamCount) {
            return Messages.tr("arena.validate.missing_spawns",
                    "defined", usableTeams().size(), "teams", teamCount);
        }
        // Le lit est pose d'avance : une arene sans lit configure pour
        // chaque equipe ne serait pas jouable, chaque mort y etant definitive.
        for (String teamName : usableTeams()) {
            if (!hasBed(teamName)) {
                return Messages.tr("arena.validate.missing_bed", "team", teamName, "arena", name);
            }
        }
        if (minPlayers < 2) {
            return Messages.tr("arena.validate.min_players");
        }
        if (maxPlayers < minPlayers) {
            return Messages.tr("arena.validate.max_below_min");
        }
        if (maxPlayers > teamCount * playersPerTeam) {
            return Messages.tr("arena.validate.max_exceeds",
                    "max", maxPlayers, "teams", teamCount, "per", playersPerTeam,
                    "capacity", (teamCount * playersPerTeam));
        }
        return null;
    }

    public boolean isConfigured() {
        return validate() == null;
    }

    // ================= Etat et joueurs =================

    public State getState() {
        return state;
    }

    public boolean isState(State other) {
        return this.state == other;
    }

    /** Vrai pendant les phases ou l'on joue reellement. */
    public boolean isRunning() {
        return state == State.PLAYING || state == State.DEATHMATCH;
    }

    /** Vrai si un joueur peut encore rejoindre : en attente, configuree, non pleine. */
    public boolean isJoinable() {
        return (state == State.WAITING || state == State.STARTING)
                && isConfigured() && !isFull();
    }

    public List<UUID> getPlayerIds() {
        return Collections.unmodifiableList(players);
    }

    /** Vrai si le joueur est dans cette arene, comme joueur ou comme spectateur. */
    public boolean contains(Player player) {
        return players.contains(player.getUniqueId()) || spectators.contains(player.getUniqueId());
    }

    public boolean isAllowSpectators() {
        return allowSpectators;
    }

    public void setAllowSpectators(boolean allowSpectators) {
        this.allowSpectators = allowSpectators;
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public int getSpectatorCount() {
        return spectators.size();
    }

    public List<Player> onlineSpectators() {
        List<Player> online = new ArrayList<Player>();
        for (UUID id : spectators) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /** Joueurs et spectateurs connectes. */
    public List<Player> audience() {
        List<Player> online = onlinePlayers();
        online.addAll(onlineSpectators());
        return online;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public String getTeam(Player player) {
        return teamOf.get(player.getUniqueId());
    }

    public BedManager getBeds() {
        return beds;
    }

    public MapTracker getMapTracker() {
        return mapTracker;
    }

    public boolean isAlive(Player player) {
        return alive.contains(player.getUniqueId());
    }

    public int aliveCount(String teamName) {
        if (teamName == null) {
            return 0;
        }
        int count = 0;
        for (UUID id : alive) {
            if (teamName.equals(teamOf.get(id))) {
                count++;
            }
        }
        return count;
    }

    public int getDeathmatchTimer() {
        return deathmatchTimer;
    }

    /** Appele par GameCycle : temps restant avant le deathmatch, pour le scoreboard. */
    public void setDeathmatchTimer(int seconds) {
        this.deathmatchTimer = seconds;
    }

    public List<Player> onlinePlayers() {
        List<Player> online = new ArrayList<Player>();
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /** Envoie un message aux seuls joueurs de cette arene. */
    public void broadcast(String message) {
        String prefixed = Main.PREFIX + message;
        for (Player player : audience()) {
            player.sendMessage(prefixed);
        }
    }

    /**
     * Envoie un message aux seuls membres d'une equipe.
     *
     * <p>Tout ce qui touche au lit passe par ici : savoir qui porte le lit
     * adverse, s'il est pose ou en cours de demenagement donnerait a l'ennemi
     * exactement ce qu'il cherche.
     *
     * @param exclude joueur a ne pas prevenir, par exemple parce qu'il recoit
     *                deja un message plus precis. Peut valoir {@code null}.
     */
    public void broadcastTeam(String teamName, String message, Player exclude) {
        if (teamName == null) {
            return;
        }
        String prefixed = Main.PREFIX + message;
        for (Player player : onlinePlayers()) {
            if (!teamName.equals(getTeam(player))) {
                continue;
            }
            if (exclude != null && exclude.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            player.sendMessage(prefixed);
        }
    }

    // ================= Entree et sortie =================

    /**
     * Fait entrer un joueur dans l'arene.
     *
     * @return la raison du refus, ou {@code null} en cas de succes
     */
    public String join(Player player) {
        if (players.contains(player.getUniqueId())) {
            return Messages.tr("arena.join.already_in");
        }
        if (state != State.WAITING && state != State.STARTING) {
            return Messages.tr("arena.join.in_progress");
        }
        if (isFull()) {
            return Messages.tr("arena.join.full", "current", players.size(), "max", maxPlayers);
        }
        String problem = validate();
        if (problem != null) {
            return Messages.tr("arena.join.misconfigured", "problem", problem);
        }

        // Premier joueur de la file : on remet la map a neuf avant l'attente.
        // Le suivi est vide apres un reset normal ; il ne contient quelque
        // chose que si une manche precedente a ete interrompue (crash, /reload).
        if (main.isMapRegenerationEnabled() && players.isEmpty()) {
            mapTracker.restore();
        }

        players.add(player.getUniqueId());
        Compat.resetPlayer(player, GameMode.ADVENTURE);
        player.teleport(lobby);
        LobbyItems.give(player);

        broadcast(Messages.tr("arena.broadcast.joined", "player", player.getName(),
                "current", players.size(), "max", maxPlayers));

        // C'est ici que la partie se declenche : entrer ne suppose pas d'avoir
        // DEJA le minimum de joueurs, sinon une arene vide serait inaccessible.
        if (state == State.WAITING && players.size() >= minPlayers) {
            startCountdown(main.getCountdown());
        }
        return null;
    }

    /** Fait sortir un joueur, volontairement ou par deconnexion. */
    public void leave(Player player, boolean teleportToLobby) {
        UUID id = player.getUniqueId();
        if (spectators.remove(id)) {
            releaseSpectator(player, teleportToLobby);
            return;
        }
        if (!players.remove(id)) {
            return;
        }
        alive.remove(id);
        startVotes.remove(id);
        // Si le partant avait encore le lit de son equipe en main, on le
        // confie a quelqu'un d'autre avant de perdre son appartenance.
        if (isRunning()) {
            beds.handleCarrierLost(player);
        }
        teamOf.remove(id);

        if (teleportToLobby && player.isOnline()) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.getScoreboardManager().clear(player);
            main.returnToLobby(player);
        }

        broadcast(Messages.tr("arena.broadcast.left", "player", player.getName(),
                "current", players.size(), "max", maxPlayers));

        if (state == State.STARTING && players.size() < minPlayers) {
            cancelCountdown();
            state = State.WAITING;
            broadcast(Messages.tr("arena.countdown_cancelled"));
        } else if (isRunning()) {
            checkWin();
        } else {
            // Un depart abaisse le seuil de votes : peut-etre est-il atteint.
            checkVoteStart();
        }
    }

    // ================= Spectateurs =================

    /**
     * Fait regarder la partie a un joueur. Il n'est pas un joueur : ni compte,
     * ni dans une equipe, ni dans les stats.
     *
     * @return la raison du refus, ou {@code null} en cas de succes
     */
    public String spectate(Player player) {
        if (contains(player)) {
            return Messages.tr("arena.join.already_in");
        }
        if (!allowSpectators || !main.isSpectatorEnabled()) {
            return Messages.tr("spectator.disabled");
        }
        Location spot = spectatorSpot();
        if (!isRunning() || spot == null) {
            return Messages.tr("spectator.not_running");
        }
        if (spectators.size() >= main.getSpectatorMax()) {
            return Messages.tr("spectator.full", "max", main.getSpectatorMax());
        }

        Compat.makeSpectator(player);
        player.teleport(spot);
        SpectatorItems.give(player);

        // Invisible aux autres, mais il voit les autres spectateurs.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (spectators.contains(other.getUniqueId())) {
                Compat.setHidden(player, other, false);
            } else {
                Compat.setHidden(other, player, true);
            }
        }
        spectators.add(player.getUniqueId());
        return null;
    }

    /** Point d'arrivee d'un spectateur : la base d'une equipe, a defaut le lobby. */
    public Location spectatorSpot() {
        for (String team : usableTeams()) {
            Location spawn = spawnPoints.get(team);
            if (spawn != null && spawn.getWorld() != null) {
                return spawn;
            }
        }
        return lobby;
    }

    /** Retire un spectateur : redevient visible, et rejoint le lobby si demande. */
    private void releaseSpectator(Player player, boolean teleportToLobby) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                Compat.setHidden(other, player, false);
            }
        }
        if (teleportToLobby && player.isOnline()) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.getScoreboardManager().clear(player);
            main.returnToLobby(player);
        }
    }

    // ================= Deconnexion et reconnexion en pleine partie =================

    /**
     * Un joueur se deconnecte (crash, coupure). A appeler pendant
     * {@code PlayerQuitEvent}, tant qu'il est encore la (inventaire accessible).
     *
     * <p>Regle : un joueur encore en vie garde sa place tant que le LIT de son
     * equipe existe ; une fois le lit detruit, la place est perdue. Dans tous les
     * autres cas (hors partie, deja elimine, lit deja detruit), c'est un depart
     * ordinaire ({@link #leave}).
     *
     * <p>Son equipement est mis de cote et son vrai inventaire vide : s'il ne
     * revient jamais, rien de la partie ne doit le suivre ailleurs sur le serveur.
     *
     * <p><b>Anti combat-log.</b> S'il a ete frappe par un adversaire dans les 10
     * dernieres secondes, la deconnexion compte comme une MORT : objets au sol,
     * mort et kill enregistres, et il revient les mains vides (comme apres une
     * mort) - seulement s'il est lie a un lit encore debout ; sinon, comme tout
     * mort non lie, il est elimine.
     */
    public void disconnect(Player player) {
        UUID id = player.getUniqueId();
        String team = teamOf.get(id);
        if (!isRunning() || !alive.contains(id) || team == null || beds.isDestroyed(team)) {
            leave(player, false);
            return;
        }

        // Duel 1v1 : pas de reconnexion possible, le joueur encore present
        // gagne immediatement. On le fait avant de sauvegarder son etat, sinon
        // la partie resterait ouverte pour un joueur qui ne reviendra pas.
        if (isOneVersusOne()) {
            main.getArenaManager().markStranded(id);
            main.getStatsManager().addLoss(player);
            main.getStatsManager().save(id);
            alive.remove(id);
            teamOf.remove(id);
            // Pas de message "garde sa place" ici : la partie se termine tout de
            // suite et l'annonce du vainqueur suffit.
            checkWin();
            return;
        }

        // Le lit qu'il transportait (deplacement en cours) repart chez un
        // coequipier ; on le retire de son inventaire pour ne pas le dupliquer.
        if (beds.handleCarrierLost(player)) {
            beds.stripBedItems(player);
        }

        GameStats stats = gameStats.get(id);
        UUID attackerId = stats == null ? null : stats.recentAttacker(System.currentTimeMillis(), COMBAT_TAG_MS);
        boolean combatLog = attackerId != null;
        PlayerSnapshot snapshot = null;
        boolean canReturn = true;
        if (combatLog) {
            broadcast(Messages.tr("arena.broadcast.combat_log", "player", player.getName()));
            // Verifie AVANT de toucher a l'etat : etre lie a un lit debout.
            canReturn = beds.canRespawn(player);
            dropInventory(player);
            registerDeath(player, Bukkit.getPlayer(attackerId));
        } else {
            snapshot = PlayerSnapshot.capture(player);
        }
        Compat.resetPlayer(player, GameMode.ADVENTURE);
        players.remove(id);
        startVotes.remove(id);

        if (!canReturn) {
            // Mort definitive (combat-log sans lit lie) : meme sort qu'un elimine.
            alive.remove(id);
            teamOf.remove(id);
            main.getArenaManager().markStranded(id);
            broadcast(Messages.tr("arena.broadcast.eliminated", "player", player.getName()));
            if (onlinePlayers().isEmpty()) {
                end(null);
            } else {
                checkWin();
            }
            return;
        }

        disconnected.put(id, new Absent(player.getName(), snapshot));
        if (!combatLog) {
            broadcast(Messages.tr("arena.broadcast.disconnected", "player", player.getName()));
        }

        // Plus personne de connecte : inutile de faire tourner la partie a vide.
        if (onlinePlayers().isEmpty()) {
            end(null);
            return;
        }
        checkWin();
    }

    /** Fait tomber l'inventaire d'un joueur comme a une mort (jamais les lits). */
    private void dropInventory(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (ItemStack item : inventory.getContents()) {
            items.add(item);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            items.add(item);
        }
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0 || Beds.isBed(item.getType())) {
                continue;
            }
            location.getWorld().dropItemNaturally(location, item);
        }
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
    }

    /** Vrai si ce joueur deconnecte peut encore reprendre sa place dans cette partie. */
    public boolean canRejoin(UUID id) {
        return isRunning() && disconnected.containsKey(id);
    }

    /**
     * Le joueur revient : il reprend sa place dans son equipe, a cote de son lit
     * s'il y est lie (sinon au point d'apparition de l'equipe), avec l'etat qu'il
     * avait en partant.
     */
    public void rejoin(Player player) {
        UUID id = player.getUniqueId();
        if (!canRejoin(id)) {
            return;
        }
        Absent absent = disconnected.remove(id);
        players.add(id);
        // Un spectateur arrive pendant son absence doit lui rester invisible.
        for (Player spectator : onlineSpectators()) {
            Compat.setHidden(player, spectator, true);
        }

        String team = teamOf.get(id);
        Compat.resetPlayer(player, GameMode.SURVIVAL);
        Location target = beds.canRespawn(player) ? beds.respawnLocation(player) : null;
        if (target == null) {
            target = spawnPoints.get(team);
        }
        if (target != null && target.getWorld() != null) {
            player.teleport(target);
        }
        if (absent.snapshot != null) {
            absent.snapshot.restore(player);
        } else {
            // Revient apres une mort (combat-log) : les mains vides, comme apres
            // toute mort.
        }
        protect(player);

        player.sendMessage(Main.PREFIX + Messages.tr("arena.your_team",
                "color", colorOf(team), "team", team));
        broadcast(Messages.tr("arena.broadcast.rejoined", "player", player.getName()));
    }

    /**
     * Le lit d'une equipe vient d'etre detruit : ses membres deconnectes perdent
     * leur place (plus de lit, plus de retour), comme un mort non lie. Sans cela
     * leur equipe resterait "en vie" indefiniment et la partie ne finirait jamais.
     */
    public void forfeitDisconnected(String team) {
        boolean any = false;
        Iterator<Map.Entry<UUID, Absent>> it = disconnected.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Absent> entry = it.next();
            UUID id = entry.getKey();
            if (!team.equals(teamOf.get(id))) {
                continue;
            }
            it.remove();
            alive.remove(id);
            teamOf.remove(id);
            main.getArenaManager().markStranded(id);
            broadcast(Messages.tr("arena.broadcast.eliminated", "player", entry.getValue().name));
            any = true;
        }
        if (any) {
            checkWin();
        }
    }

    // ================= Deroulement de la partie =================

    private void startCountdown(int seconds) {
        state = State.STARTING;
        cancelCountdown();
        countdownTask = new Countdown(this, seconds).runTaskTimer(main, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelCycle() {
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
    }

    // ================= Vote de demarrage =================

    /** Un joueur clique le papier pour voter le lancement anticipe. */
    public void vote(Player player) {
        if (state != State.WAITING && state != State.STARTING) {
            return;
        }
        if (!players.contains(player.getUniqueId())) {
            return;
        }
        if (!startVotes.add(player.getUniqueId())) {
            player.sendMessage(Main.PREFIX + Messages.tr("arena.already_voted"));
            return;
        }
        broadcast(Messages.tr("arena.broadcast.vote", "player", player.getName(),
                "votes", startVotes.size(), "needed", votesNeeded()));
        checkVoteStart();
    }

    /** Nombre de votes requis : majorite stricte des joueurs connectes. */
    private int votesNeeded() {
        return onlinePlayers().size() / 2 + 1;
    }

    /**
     * Lance la partie si le vote est atteint et qu'il y a assez de joueurs.
     * Appele apres chaque vote et a chaque depart (le seuil dependant du nombre
     * de joueurs, il peut etre atteint quand quelqu'un s'en va).
     */
    private void checkVoteStart() {
        if (state != State.WAITING && state != State.STARTING) {
            return;
        }
        if (onlinePlayers().size() < minPlayers) {
            return;
        }
        if (startVotes.size() >= votesNeeded()) {
            broadcast(Messages.tr("arena.vote_reached"));
            // On raccourcit a 3 secondes pour laisser jouer le 3-2-1 a l'ecran.
            startCountdown(3);
        }
    }

    /**
     * Lance reellement la partie : repartition en equipes, teleportation aux
     * points d'apparition et demarrage du cycle de jeu.
     */
    public void start() {
        cancelCountdown();
        // Securite avant le lancement : si une manche precedente a laisse des
        // blocs modifies (arret brutal), on remet la map a neuf maintenant.
        // Le suivi est ensuite remis a zero pour la nouvelle manche.
        if (main.isMapRegenerationEnabled()) {
            mapTracker.restore();
        }
        state = State.PLAYING;
        // Nouvelle manche : on repart d'un suivi de blocs vierge et sans votes.
        mapTracker.clear();
        startVotes.clear();

        List<String> teams = usableTeams();
        List<Player> participants = onlinePlayers();

        alive.clear();
        teamOf.clear();
        gameStats.clear();
        protectedUntil.clear();

        // Repartition equilibree : on distribue les joueurs un par un sur les
        // equipes, au lieu de remplir la premiere avant de passer a la suivante.
        // A 2 ou 3 joueurs sur 4 equipes, l'ordre est choisi pour mettre les
        // joueurs dans des coins opposes (pas cote a cote).
        List<String> assignment = assignmentOrder(teams, participants.size());
        for (int i = 0; i < participants.size(); i++) {
            Player player = participants.get(i);
            String teamName = assignment.get(i % assignment.size());
            Location spawn = spawnPoints.get(teamName);

            teamOf.put(player.getUniqueId(), teamName);
            alive.add(player.getUniqueId());
            gameStats.put(player.getUniqueId(), new GameStats(player.getName()));
            main.getStatsManager().addGame(player);

            Compat.resetPlayer(player, GameMode.SURVIVAL);
            protect(player);
            if (spawn != null && spawn.getWorld() != null) {
                player.teleport(spawn);
            }
            Titles.send(player, Messages.tr("title.game_start"), "");
            Sounds.gameStart(player);
            player.sendMessage(Main.PREFIX + Messages.tr("arena.your_team",
                    "color", colorOf(teamName), "team", teamName));
        }

        broadcast(Messages.tr("arena.game_begin"));

        // Les lits sont poses/restaures a leur emplacement configure une fois
        // les equipes formees (elles conditionnent la couleur des lits).
        beds.start(teams);
        // Plus besoin de cliquer le lit : chaque joueur est relie d'office au
        // lit de son equipe des le debut de la manche.
        for (String team : teams) {
            beds.bindAll(team);
        }

        // Les NPC de boutique sont poses le temps de la partie seulement.
        spawnShopNpcs();

        cancelCycle();
        cycleTask = new GameCycle(this).runTaskTimer(main, 0L, 20L);
    }

    public void startDeathmatch() {
        state = State.DEATHMATCH;
        broadcast(Messages.tr("arena.deathmatch"));
        applyBorder();
    }

    /**
     * Ordre de repartition. Avec 4 equipes et seulement 2 joueurs, on saute les
     * equipes voisines : Rouge (coin sud-ouest) puis Vert (coin nord-est).
     * Avec 3 joueurs, on garde cet ecart puis on complete par Bleu.
     */
    private List<String> assignmentOrder(List<String> teams, int playerCount) {
        List<String> order = new ArrayList<String>(teams);
        if (teams.size() == 4 && playerCount == 2) {
            order.clear();
            order.add(teams.get(0));
            order.add(teams.get(3));
        } else if (teams.size() == 4 && playerCount == 3) {
            order.clear();
            order.add(teams.get(0));
            order.add(teams.get(3));
            order.add(teams.get(1));
        }
        return order;
    }

    private void applyBorder() {
        if (lobby == null || lobby.getWorld() == null) {
            return;
        }
        World world = lobby.getWorld();
        WorldBorder border = world.getWorldBorder();
        Location center = borderCenter != null && borderCenter.getWorld() != null
                ? borderCenter : lobby;
        border.setCenter(center.getX(), center.getZ());
        border.setSize(main.getBorderStartSize());
        border.setSize(main.getBorderEndSize(), main.getBorderShrinkTime());
    }

    /** Remet la bordure du monde a sa taille par defaut en fin de partie. */
    private void resetBorder() {
        if (lobby == null || lobby.getWorld() == null) {
            return;
        }
        WorldBorder border = lobby.getWorld().getWorldBorder();
        border.setCenter(0, 0);
        // 60 000 000 : taille par defaut d'une bordure Minecraft.
        border.setSize(60000000d);
    }

    /** Elimine un joueur : il passe spectateur et son equipe peut perdre. */
    public void eliminate(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (!alive.remove(id)) {
            return;
        }
        Compat.resetPlayer(player, GameMode.SPECTATOR);
        // La raison n'est dite qu'a l'interesse : annoncer "plus de lit" a
        // toute l'arene revelerait qu'une equipe est devenue vulnerable.
        player.sendMessage(Main.PREFIX + (reason == null
                ? Messages.tr("arena.eliminated_self")
                : Messages.tr("arena.eliminated_self_reason", "reason", reason)));
        broadcast(Messages.tr("arena.broadcast.eliminated", "player", player.getName()));
        checkWin();
    }

    /** Vrai si la manche est un duel : exactement 2 joueurs vivants, 2 equipes. */
    private boolean isOneVersusOne() {
        if (alive.size() != 2) {
            return false;
        }
        Set<String> teams = new LinkedHashSet<String>();
        for (UUID id : alive) {
            String team = teamOf.get(id);
            if (team != null) {
                teams.add(team);
            }
        }
        return teams.size() == 2;
    }

    /** Termine la partie des qu'il ne reste qu'une equipe, ou aucune. */
    public void checkWin() {
        if (!isRunning()) {
            return;
        }
        Set<String> remaining = new LinkedHashSet<String>();
        for (UUID id : alive) {
            String teamName = teamOf.get(id);
            if (teamName != null) {
                remaining.add(teamName);
            }
        }
        if (remaining.size() == 1) {
            end(remaining.iterator().next());
        } else if (remaining.isEmpty()) {
            end(null);
        }
    }

    /** Cloture la partie et programme la remise a zero de l'arene. */
    public void end(String winningTeam) {
        if (state == State.FINISH) {
            return;
        }
        state = State.FINISH;
        cancelCountdown();
        cancelCycle();

        if (winningTeam == null) {
            broadcast(Messages.tr("arena.end_no_winner"));
        } else {
            broadcast(Messages.tr("arena.end_winner",
                    "color", colorOf(winningTeam), "team", winningTeam));
            spawnVictoryFireworks(winningTeam);
        }
        sendSummary();

        List<Player> participants = onlinePlayers();
        // ELO : calcule sur les notes d'AVANT la partie, une bonne fois pour
        // toutes les equipes encore representees. Vide (donc sans effet) si la
        // partie a ete interrompue sans vainqueur.
        Map<String, Integer> ratingDeltas = winningTeam == null
                ? Collections.<String, Integer>emptyMap()
                : Elo.deltas(winningTeam, teamRatings(participants));

        // Statistiques : victoire/defaite (seulement s'il y a un vainqueur, pas
        // sur une partie interrompue), puis on persiste tout le monde - c'est le
        // point de sauvegarde naturel des kills/morts accumules pendant la partie.
        for (Player player : participants) {
            String team = getTeam(player);
            if (winningTeam != null && team != null) {
                if (winningTeam.equals(team)) {
                    main.getStatsManager().addWin(player);
                    main.getQuestManager().addProgress(player, QuestType.WIN_GAMES, 1);
                } else {
                    main.getStatsManager().addLoss(player);
                }
                Integer delta = ratingDeltas.get(team);
                if (delta != null) {
                    main.getStatsManager().addRating(player, delta.intValue());
                }
            }
            main.getStatsManager().save(player.getUniqueId());
        }

        main.getDiscordNotifier().announceEnd(this, winningTeam, participants.size());

        new BukkitRunnable() {
            @Override
            public void run() {
                reset();
            }
        }.runTaskLater(main, 20L * main.getEndDelay());
    }

    // ================= Compteurs de partie, series, protection =================

    public GameStats getGameStats(Player player) {
        return gameStats.get(player.getUniqueId());
    }

    /**
     * Mort d'un participant : compteurs de carriere et de partie, kill du tueur
     * (stats + quete), puis series de kills annoncees a l'arene (rien qui touche
     * au lit : la confidentialite des equipes est preservee).
     *
     * @param killer le joueur qui a porte le coup fatal, ou {@code null}
     */
    public void registerDeath(Player victim, Player killer) {
        long now = System.currentTimeMillis();
        GameStats victimStats = gameStats.get(victim.getUniqueId());
        int victimStreak = victimStats == null ? 0 : victimStats.streak(now, STREAK_WINDOW_MS);
        if (victimStats != null) {
            victimStats.addDeath();
            victimStats.resetStreak();
            victimStats.clearAttacker();
        }
        main.getStatsManager().addDeath(victim);

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId()) || !contains(killer)) {
            return;
        }
        main.getStatsManager().addKill(killer);
        main.getQuestManager().addProgress(killer, QuestType.GET_KILLS, 1);

        if (victimStreak >= 3) {
            broadcast(Messages.tr("game.streak.stopped", "killer", killer.getName(),
                    "player", victim.getName(), "count", victimStreak));
        }
        GameStats killerStats = gameStats.get(killer.getUniqueId());
        if (killerStats == null) {
            return;
        }
        int streak = killerStats.registerKill(now, STREAK_WINDOW_MS);
        if (streak >= 2) {
            String key = streak == 2 ? "game.streak.double"
                    : streak == 3 ? "game.streak.triple"
                    : streak == 4 ? "game.streak.quadruple"
                    : "game.streak.many";
            broadcast(Messages.tr(key, "player", killer.getName(), "count", streak));
            for (Player viewer : onlinePlayers()) {
                Sounds.killStreak(viewer, streak);
            }
        }
    }

    /**
     * Un participant en frappe un autre : degats cumules pour le resume de fin,
     * et marquage "en combat" de la victime (anti deconnexion en combat).
     */
    public void recordHit(Player attacker, Player victim, double amount) {
        GameStats attackerStats = gameStats.get(attacker.getUniqueId());
        if (attackerStats != null) {
            attackerStats.addDamage(amount);
        }
        GameStats victimStats = gameStats.get(victim.getUniqueId());
        if (victimStats != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            victimStats.tagAttacker(attacker.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Un lit adverse vient d'etre detruit par ce joueur (resume de fin). */
    public void recordBedDestroyed(Player player) {
        GameStats stats = gameStats.get(player.getUniqueId());
        if (stats != null) {
            stats.addBed();
        }
    }

    /**
     * Vrai tant qu'un joueur vient d'apparaitre et ne peut pas etre blesse
     * ({@code respawn-protection} de config.yml). Elle tombe a l'expiration du
     * delai, ou des qu'il frappe quelqu'un.
     */
    public boolean hasSpawnProtection(Player player) {
        Long until = protectedUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until.longValue();
    }

    public void clearSpawnProtection(Player player) {
        protectedUntil.remove(player.getUniqueId());
    }

    /** Donne au joueur sa protection de reapparition (duree : config, 0 = aucune). */
    public void protect(Player player) {
        int seconds = main.getRespawnProtection();
        if (seconds > 0) {
            protectedUntil.put(player.getUniqueId(),
                    Long.valueOf(System.currentTimeMillis() + seconds * 1000L));
        }
    }

    /**
     * Resume de fin de partie : plus de kills, plus de degats, plus de lits
     * detruits. Les joueurs deconnectes y figurent (leur nom est retenu).
     */
    private void sendSummary() {
        if (gameStats.isEmpty()) {
            return;
        }
        GameStats topKills = null;
        GameStats topDamage = null;
        GameStats topBeds = null;
        for (GameStats stats : gameStats.values()) {
            if (topKills == null || stats.getKills() > topKills.getKills()) {
                topKills = stats;
            }
            if (topDamage == null || stats.getDamage() > topDamage.getDamage()) {
                topDamage = stats;
            }
            if (topBeds == null || stats.getBeds() > topBeds.getBeds()) {
                topBeds = stats;
            }
        }
        broadcast(Messages.tr("summary.header"));
        broadcast(topKills.getKills() > 0
                ? Messages.tr("summary.kills", "player", topKills.getName(), "value", topKills.getKills())
                : Messages.tr("summary.kills_none"));
        broadcast(topDamage.getDamage() > 0
                ? Messages.tr("summary.damage", "player", topDamage.getName(),
                        "value", String.format(java.util.Locale.US, "%.1f", topDamage.getDamage() / 2d))
                : Messages.tr("summary.damage_none"));
        broadcast(topBeds.getBeds() > 0
                ? Messages.tr("summary.beds", "player", topBeds.getName(), "value", topBeds.getBeds())
                : Messages.tr("summary.beds_none"));
    }

    /**
     * Note moyenne de chaque equipe encore representee, pour le calcul ELO.
     * Une equipe sans aucun joueur connecte n'apparait pas dans le resultat.
     */
    private Map<String, Integer> teamRatings(List<Player> participants) {
        Map<String, List<Integer>> byTeam = new LinkedHashMap<String, List<Integer>>();
        for (Player player : participants) {
            String team = getTeam(player);
            if (team == null) {
                continue;
            }
            PlayerStats stats = main.getStatsManager().getCached(player.getUniqueId());
            int rating = stats == null ? 1000 : stats.getRating();
            List<Integer> ratings = byTeam.get(team);
            if (ratings == null) {
                ratings = new ArrayList<Integer>();
                byTeam.put(team, ratings);
            }
            ratings.add(Integer.valueOf(rating));
        }
        Map<String, Integer> averages = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, List<Integer>> entry : byTeam.entrySet()) {
            int sum = 0;
            for (Integer rating : entry.getValue()) {
                sum += rating.intValue();
            }
            averages.put(entry.getKey(), Integer.valueOf(sum / entry.getValue().size()));
        }
        return averages;
    }

    /**
     * Petit feu d'artifice a la position de chaque vainqueur, purement
     * cosmetique. L'API Firework/FireworkMeta n'a pas change depuis bien avant
     * la 1.8 : pas besoin d'isoler quoi que ce soit dans compat/.
     */
    private void spawnVictoryFireworks(String winningTeam) {
        Color color = teamColorOf(winningTeam);
        for (Player player : onlinePlayers()) {
            if (!winningTeam.equals(getTeam(player)) || player.getWorld() == null) {
                continue;
            }
            Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(color)
                    .with(FireworkEffect.Type.BURST)
                    .withFlicker()
                    .build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        }
    }

    /**
     * Couleur "objet" d'une equipe (feu d'artifice, armure en cuir), distincte de
     * la couleur de chat {@link ChatColor}.
     */
    public static Color teamColorOf(String teamName) {
        if ("Rouge".equalsIgnoreCase(teamName)) {
            return Color.RED;
        }
        if ("Bleu".equalsIgnoreCase(teamName)) {
            return Color.BLUE;
        }
        if ("Jaune".equalsIgnoreCase(teamName)) {
            return Color.YELLOW;
        }
        if ("Vert".equalsIgnoreCase(teamName)) {
            return Color.GREEN;
        }
        return Color.WHITE;
    }

    /**
     * Renvoie tout le monde au lobby du serveur et rend l'arene disponible.
     * Egalement appelee a l'arret du plugin.
     */
    public void reset() {
        cancelCountdown();
        cancelCycle();
        resetBorder();

        for (Player player : onlinePlayers()) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.getScoreboardManager().clear(player);
            main.returnToLobby(player);
        }
        for (Player spectator : onlineSpectators()) {
            releaseSpectator(spectator, true);
        }
        spectators.clear();

        deathmatchTimer = 0;
        // Les deconnectes qui ne sont pas revenus se reconnecteront au milieu de
        // la map de jeu : on le retient pour les renvoyer au lobby.
        for (UUID id : disconnected.keySet()) {
            main.getArenaManager().markStranded(id);
        }
        disconnected.clear();
        gameStats.clear();
        protectedUntil.clear();
        players.clear();
        alive.clear();
        teamOf.clear();
        startVotes.clear();
        // Regeneration de la map AVANT les lits : ainsi les lits, restaures
        // ensuite, gagnent toujours a leur emplacement.
        mapTracker.restore();
        beds.endRound();
        beds.clear();
        despawnShopNpcs();
        if (main.getBedListeners() != null) {
            main.getBedListeners().forgetTnt();
        }
        state = State.WAITING;

        // File d'attente automatique : des qu'une manche se termine, les joueurs
        // du lobby sont remis dans la file sans commande. Ceux qui ont quitte
        // volontairement (/aw leave) sont marques "disabled" et ignores.
        if (main.isAutoQueueEnabled() && main.getAutoQueue() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (main.getArenaManager().getArenaOf(online) == null) {
                    main.getAutoQueue().queue(online);
                }
            }
        }
    }

    /** Couleur de chat associee a une equipe. */
    public static ChatColor colorOf(String teamName) {
        if ("Rouge".equalsIgnoreCase(teamName)) {
            return ChatColor.RED;
        }
        if ("Bleu".equalsIgnoreCase(teamName)) {
            return ChatColor.BLUE;
        }
        if ("Jaune".equalsIgnoreCase(teamName)) {
            return ChatColor.YELLOW;
        }
        if ("Vert".equalsIgnoreCase(teamName)) {
            return ChatColor.GREEN;
        }
        return ChatColor.WHITE;
    }

    /** Nom d'equipe canonique correspondant a une saisie utilisateur. */
    public static String matchTeamName(String input) {
        for (String teamName : TEAM_NAMES) {
            if (teamName.equalsIgnoreCase(input)) {
                return teamName;
            }
        }
        return null;
    }
}
