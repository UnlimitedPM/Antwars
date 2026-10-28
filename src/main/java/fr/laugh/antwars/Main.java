package fr.laugh.antwars;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.antwars.api.AntWarsAPI;
import fr.laugh.antwars.bstats.bukkit.Metrics;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.compat.Heads;
import fr.laugh.antwars.compat.MCVersion;
import fr.laugh.antwars.compat.Proxy;
import fr.laugh.antwars.config.ArenaNaming;
import fr.laugh.antwars.config.ConfigMenu;
import fr.laugh.antwars.config.Placements;
import fr.laugh.antwars.discord.DiscordNotifier;
import fr.laugh.antwars.game.SpectatorMenu;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.listeners.BedListeners;
import fr.laugh.antwars.listeners.BlocksListeners;
import fr.laugh.antwars.listeners.ChatListeners;
import fr.laugh.antwars.listeners.ConfigListeners;
import fr.laugh.antwars.listeners.DamageListeners;
import fr.laugh.antwars.listeners.LobbyListeners;
import fr.laugh.antwars.listeners.MapListeners;
import fr.laugh.antwars.listeners.MenuListeners;
import fr.laugh.antwars.listeners.NpcListeners;
import fr.laugh.antwars.listeners.PlayerListeners;
import fr.laugh.antwars.listeners.SignListeners;
import fr.laugh.antwars.listeners.SpectatorListeners;
import fr.laugh.antwars.listeners.WorldListeners;
import fr.laugh.antwars.quest.QuestManager;
import fr.laugh.antwars.queue.AutoQueue;
import fr.laugh.antwars.scoreboard.ScoreboardManager;
import fr.laugh.antwars.shop.QuickBuyManager;
import fr.laugh.antwars.shop.ShopMenu;
import fr.laugh.antwars.sign.JoinSignManager;
import fr.laugh.antwars.stats.StatsManager;

/**
 * Point d'entree du plugin AntWars.
 *
 * <p>Compatible des serveurs 1.8 a 1.21, en Spigot comme en Paper : le jar est
 * compile en bytecode Java 8 contre l'API 1.8, et tout ce qui a change entre
 * ces versions est isole dans le paquet {@code fr.laugh.antwars.compat}.
 */
public class Main extends JavaPlugin {

    public static final String PREFIX = ChatColor.GOLD + "[AntWars] " + ChatColor.GRAY;

    private ArenaManager arenaManager;
    private Messages messages;
    private ShopMenu shopMenu;
    private StatsManager statsManager;
    private QuestManager questManager;
    private QuickBuyManager quickBuyManager;
    private JoinSignManager joinSignManager;
    private ConfigMenu configMenu;
    private Placements placements;
    private ArenaNaming arenaNaming;
    private SpectatorMenu spectatorMenu;
    private ChatListeners chatListeners;
    private ScoreboardManager scoreboardManager;
    /** bStats : suivi anonyme (nombre de serveurs, versions...). Opt-out cote serveur, pas ici. */
    private Metrics metrics;
    private BedListeners bedListeners;
    private AutoQueue autoQueue;
    private Proxy proxy;
    private DiscordNotifier discordNotifier;
    private Location serverLobby;
    /** Tete du NPC de boutique, construite une fois puis clonee a chaque pose. */
    private ItemStack shopNpcHead;

    @Override
    public void onEnable() {
        // A faire en tout premier : les couches de compatibilite en dependent.
        MCVersion.init();
        getLogger().info("Serveur detecte : " + MCVersion.raw()
                + " (1." + MCVersion.minor() + "." + MCVersion.patch() + ")"
                + (MCVersion.isLegacy() ? " - mode legacy 1.8/1.12" : ""));

        List<String> missing = AWMaterial.resolveAll(getLogger());
        if (!missing.isEmpty()) {
            getLogger().severe("Cette version de Minecraft ne fournit pas les materiaux requis : "
                    + missing + ". Le plugin est desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        messages = new Messages(this);
        loadServerLobby();
        buildShopNpcHead();
        proxy = new Proxy(this);
        discordNotifier = new DiscordNotifier(this);

        arenaManager = new ArenaManager(this);
        autoQueue = new AutoQueue(this);
        autoQueue.start();
        shopMenu = new ShopMenu(this);
        statsManager = new StatsManager(this);
        questManager = new QuestManager(this);
        quickBuyManager = new QuickBuyManager(this);
        joinSignManager = new JoinSignManager(this);
        configMenu = new ConfigMenu(this);
        placements = new Placements(this);
        arenaNaming = new ArenaNaming(this);
        spectatorMenu = new SpectatorMenu(this);
        chatListeners = new ChatListeners(this);
        scoreboardManager = new ScoreboardManager(this);
        AntWarsAPI.init(this);

        bedListeners = new BedListeners(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListeners(this), this);
        pm.registerEvents(new DamageListeners(this), this);
        pm.registerEvents(new BlocksListeners(this), this);
        pm.registerEvents(new MenuListeners(this), this);
        pm.registerEvents(new NpcListeners(this), this);
        pm.registerEvents(new MapListeners(this), this);
        pm.registerEvents(new LobbyListeners(this), this);
        pm.registerEvents(new SignListeners(this), this);
        pm.registerEvents(new ConfigListeners(this), this);
        pm.registerEvents(new WorldListeners(this), this);
        pm.registerEvents(new SpectatorListeners(this), this);
        pm.registerEvents(chatListeners, this);
        pm.registerEvents(bedListeners, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                joinSignManager.updateAll();
            }
        }.runTaskTimer(this, 40L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                scoreboardManager.updateAll();
            }
        }.runTaskTimer(this, 20L, 20L);

        PluginCommand command = getCommand("aw");
        if (command == null) {
            // Arrive si le plugin.yml embarque n'est pas celui attendu.
            getLogger().severe("La commande /aw est absente du plugin.yml. Le plugin est desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CommandArena executor = new CommandArena(this, arenaManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        // Rechargement a chaud (/reload) : les joueurs sont deja connectes, donc
        // PlayerJoinEvent ne se declenchera pas pour eux. On charge leurs stats
        // maintenant, sinon un premier compteur creerait une entree a zero qui
        // ecraserait leurs vraies stats a la sauvegarde.
        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.load(player);
            questManager.load(player);
            quickBuyManager.load(player);
        }

        // Placeholders %antwars_...% : uniquement si PlaceholderAPI est la. Le
        // "new Placeholders" n'est atteint que dans cette branche, donc la JVM ne
        // charge jamais cette classe (ni l'API de PAPI) sur un serveur sans PAPI.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new fr.laugh.antwars.api.Placeholders(this).register();
            getLogger().info("PlaceholderAPI detecte : placeholders %antwars_...% enregistres.");
        }

        // bStats : nombre de serveurs, versions, joueurs en ligne - donnees
        // anonymes, opt-out global par le serveur (plugins/bStats/config.yml),
        // jamais par un reglage a nous. ID de service enregistre sur bstats.org.
        metrics = new Metrics(this, 34179);
    }

    @Override
    public void onDisable() {
        // Arreter la file avant le reset des arenes : sinon le reset remettrait
        // les joueurs en attente alors que le plugin s'arrete.
        if (autoQueue != null) {
            autoQueue.stop();
            autoQueue = null;
        }
        // Sans cela, un rechargement du plugin laisserait des joueurs bloques
        // en mode spectateur, avec une bordure de monde retrecie.
        if (arenaManager != null) {
            arenaManager.resetAll();
            arenaManager.save();
        }
        // Filet de securite : rendre son scoreboard normal a tout joueur restant.
        if (scoreboardManager != null) {
            scoreboardManager.clearAll();
        }
        // Idem pour les NPC Citizens : ils survivraient au rechargement du plugin
        // (Citizens les persiste dans ses propres fichiers) et s'accumuleraient.
        fr.laugh.antwars.npc.ShopNpc.removeAllCitizens();
        // Apres la remise a zero des arenes : on sauve le cache des stats et on
        // attend la fin des ecritures avant de rendre la main au serveur.
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (questManager != null) {
            questManager.shutdown();
        }
        if (quickBuyManager != null) {
            quickBuyManager.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
    }

    // ================= Acces aux composants =================

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    /** File d'attente automatique ; {@code null} pendant l'arret du plugin. */
    public AutoQueue getAutoQueue() {
        return autoQueue;
    }

    public boolean isAutoQueueEnabled() {
        return getConfig().getBoolean("auto-queue.enabled", false);
    }

    public ShopMenu getShopMenu() {
        return shopMenu;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public QuickBuyManager getQuickBuyManager() {
        return quickBuyManager;
    }

    /** bStats. Peut valoir {@code null} avant la fin de {@code onEnable}. */
    public Metrics getMetrics() {
        return metrics;
    }

    public Messages getMessages() {
        return messages;
    }

    public JoinSignManager getJoinSignManager() {
        return joinSignManager;
    }

    public ConfigMenu getConfigMenu() {
        return configMenu;
    }

    public Placements getPlacements() {
        return placements;
    }

    public ArenaNaming getArenaNaming() {
        return arenaNaming;
    }

    public SpectatorMenu getSpectatorMenu() {
        return spectatorMenu;
    }

    public ChatListeners getChatListeners() {
        return chatListeners;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public String msg(String key, Object... placeholders) {
        return messages.get(key, placeholders);
    }

    public boolean isMapRegenerationEnabled() {
        return getConfig().getBoolean("map-regeneration", true);
    }

    /**
     * Vrai si un joueur vivant deconnecte en partie garde sa place tant que le
     * lit de son equipe existe ; faux = toute deconnexion est un depart definitif.
     */
    public boolean isReconnectionEnabled() {
        return getConfig().getBoolean("reconnection.enabled", true);
    }

    /** Secondes entre la fin de partie (resume, feux d'artifice) et le renvoi au lobby. */
    public int getEndDelay() {
        return Math.max(1, getConfig().getInt("end-delay", 5));
    }

    /** Secondes d'invulnerabilite apres une apparition ; 0 = aucune. */
    public int getRespawnProtection() {
        return Math.max(0, Math.min(30, getConfig().getInt("respawn-protection", 3)));
    }

    /** Vrai si l'armure en cuir vendue en boutique est teinte a la couleur de l'equipe de l'acheteur. */
    public boolean isTeamArmorEnabled() {
        return getConfig().getBoolean("team-armor", true);
    }

    /** Vrai si on peut regarder une partie sans y jouer (/aw spectate), sur les arenes qui l'autorisent. */
    public boolean isSpectatorEnabled() {
        return getConfig().getBoolean("spectator.enabled", true);
    }

    public int getSpectatorMax() {
        return Math.max(0, getConfig().getInt("spectator.max-per-arena", 5));
    }

    public boolean isChatEnabled() {
        return getConfig().getBoolean("chat.enabled", true);
    }

    /** {@code arena} ou {@code world} : qui est "tout le monde" pour un message a tous. */
    public String getChatScope() {
        return getConfig().getString("chat.scope", "arena").toLowerCase(java.util.Locale.ROOT);
    }

    /** Debut de message pour parler a tout le monde (ou a son equipe si c'est le chat par defaut). */
    public String getChatAllPrefix() {
        String prefix = getConfig().getString("chat.all-prefix", "!");
        return prefix == null || prefix.isEmpty() ? "!" : prefix;
    }

    /**
     * Vrai si PlaceholderAPI est present. Garde d'isolation : {@code api.PapiBridge}
     * (et l'expansion) ne doivent etre appeles qu'apres ce test.
     */
    public boolean hasPlaceholderApi() {
        return getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean isQuestsEnabled() {
        return getConfig().getBoolean("quests.enabled", true);
    }

    public boolean isArenaModeEnabled() {
        return getConfig().getBoolean("arena-mode.enabled", false);
    }

    /** Arene a rejoindre automatiquement en mode arene (peut etre vide). */
    public String getArenaModeArena() {
        return getConfig().getString("arena-mode.arena", "");
    }

    /** Peut valoir {@code null} si le plugin s'est desactive au demarrage. */
    public BedListeners getBedListeners() {
        return bedListeners;
    }

    /**
     * Un exemplaire de la tete du NPC de boutique (clone, pour ne pas partager
     * le meme objet entre plusieurs NPC).
     */
    public ItemStack getShopNpcHead() {
        return shopNpcHead == null ? null : shopNpcHead.clone();
    }

    /**
     * (Re)configure le NPC de boutique : sa tete (pour le repli armor stand) et
     * le choix armor stand / vrai NPC Citizens. Appelee au demarrage et au
     * rechargement de la configuration.
     */
    public void buildShopNpcHead() {
        shopNpcHead = Heads.shopHead(getConfig().getString("shop-npc.skin", ""));
        fr.laugh.antwars.npc.ShopNpc.setup(this);
    }

    // ================= Lobby principal =================

    public Location getServerLobby() {
        return serverLobby;
    }

    /**
     * Renvoie un joueur "a la maison" en fin de partie ou sur /aw leave : vers
     * le serveur hub du proxy si le mode BungeeCord est actif, sinon vers le
     * lobby local. Le joueur doit avoir ete remis a zero au prealable.
     */
    public void returnToLobby(Player player) {
        if (proxy != null && proxy.isEnabled()) {
            proxy.sendToFallback(player);
            return;
        }
        if (serverLobby != null && serverLobby.getWorld() != null) {
            player.teleport(serverLobby);
            return;
        }
        // Repli : aucun lobby serveur defini (/aw setlobby jamais fait) ou son
        // monde n'est pas charge. Sans ce repli, le joueur resterait bloque dans
        // l'arene apres /aw leave ou en fin de partie. On l'envoie au point
        // d'apparition du monde principal, faute de mieux.
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            player.teleport(worlds.get(0).getSpawnLocation());
        }
    }

    public void setServerLobby(Location location) {
        this.serverLobby = location;
        if (location == null || location.getWorld() == null) {
            return;
        }
        FileConfiguration config = getConfig();
        config.set("server-lobby.world", location.getWorld().getName());
        config.set("server-lobby.x", location.getX());
        config.set("server-lobby.y", location.getY());
        config.set("server-lobby.z", location.getZ());
        config.set("server-lobby.yaw", location.getYaw());
        config.set("server-lobby.pitch", location.getPitch());
        saveConfig();
    }

    /**
     * Un monde vient d'etre charge (souvent par un gestionnaire de mondes, apres
     * AntWars) : on lui rend les positions, panneaux et lobby qui l'attendaient.
     */
    public void onWorldLoaded(World world) {
        if (arenaManager != null) {
            arenaManager.resolvePending(world);
        }
        if (joinSignManager != null) {
            joinSignManager.resolvePending(world);
        }
        if (serverLobby == null && world.getName().equals(getConfig().getString("server-lobby.world"))) {
            loadServerLobby();
        }
    }

    private void loadServerLobby() {
        ConfigurationSection section = getConfig().getConfigurationSection("server-lobby");
        if (section == null) {
            return;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // La position reste dans config.yml : elle sera reprise par
            // onWorldLoaded() si le monde se charge apres le plugin.
            getLogger().warning("Le monde du lobby principal (" + worldName
                    + ") n'est pas encore charge : lobby repris des qu'il le sera.");
            return;
        }
        serverLobby = new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    // ================= Reglages de partie =================

    public int getCountdown() {
        return Math.max(3, getConfig().getInt("countdown", 30));
    }

    /**
     * Duree par defaut avant l'apparition des bordures (deathmatch), en secondes.
     * Sert de valeur initiale aux nouvelles arenes ; chacune peut ensuite
     * l'overrider (voir {@link Arena#getBorderTime()}). 1200 = 20 minutes.
     */
    public int getGameDuration() {
        return Math.max(10, getConfig().getInt("game-duration", 1200));
    }

    public double getBorderStartSize() {
        return Math.max(10d, getConfig().getDouble("border.start-size", 100d));
    }

    public double getBorderEndSize() {
        return Math.max(5d, getConfig().getDouble("border.end-size", 20d));
    }

    public long getBorderShrinkTime() {
        return Math.max(1L, getConfig().getLong("border.shrink-time", 60L));
    }
}
