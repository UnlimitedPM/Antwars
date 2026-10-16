package fr.laugh.antwars.quest;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;

import fr.laugh.antwars.compat.Yaml;

/**
 * Quetes quotidiennes : trois objectifs fixes (voir {@link QuestType}), qui se
 * reinitialisent chaque jour.
 *
 * <p>Meme discipline de threading que {@code stats.StatsManager} : la
 * progression n'est modifiee que sur le thread principal (les evenements de
 * jeu), toute I/O passe par un unique thread dedie, et ce qui traverse vers ce
 * thread est toujours une {@link QuestProgress#copy() copie}.
 *
 * <p>Stockage toujours en YAML ({@code quests.yml}), meme si
 * {@code storage.type: mysql} est choisi pour les statistiques : ce n'est
 * qu'un confort personnel du joueur, pas une donnee a partager entre plusieurs
 * serveurs, donc pas besoin de dupliquer toute la mecanique MySQL pour ca.
 */
public class QuestManager {

    private static final String ROOT = "quests";

    private final Main main;
    private final ExecutorService io;
    private final ConcurrentHashMap<UUID, QuestProgress> cache =
            new ConcurrentHashMap<UUID, QuestProgress>();

    private File file;
    private FileConfiguration config;

    public QuestManager(Main main) {
        this.main = main;
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "AntWars-Quests");
                thread.setDaemon(true);
                return thread;
            }
        });
        io.submit(new Runnable() {
            @Override
            public void run() {
                file = new File(main.getDataFolder(), "quests.yml");
                config = Yaml.load(file);
            }
        });
    }

    // ================= Chargement / dechargement =================

    /** Charge la progression d'un joueur a sa connexion (asynchrone). Rien si desactive. */
    public void load(Player player) {
        if (!main.isQuestsEnabled()) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        io.submit(new Runnable() {
            @Override
            public void run() {
                cache.put(uuid, read(uuid));
            }
        });
    }

    public void unload(Player player) {
        save(player.getUniqueId());
        cache.remove(player.getUniqueId());
    }

    // ================= Lecture et progression (thread principal) =================

    private QuestProgress live(UUID uuid) {
        QuestProgress progress = cache.get(uuid);
        if (progress == null) {
            progress = new QuestProgress(LocalDate.now());
            cache.put(uuid, progress);
        }
        progress.resetIfNewDay(LocalDate.now());
        return progress;
    }

    /** Progression actuelle d'un joueur connecte (deja remise a zero si le jour a change). */
    public QuestProgress getProgress(Player player) {
        return live(player.getUniqueId());
    }

    /**
     * Ajoute a la progression d'une quete et annonce sa reussite au joueur des
     * qu'elle vient d'etre atteinte (jamais deux fois le meme jour : une fois
     * l'objectif atteint, {@link QuestProgress#add} ne le depasse plus). Rien
     * si les quetes sont desactivees (config {@code quests.enabled}).
     */
    public void addProgress(Player player, QuestType type, int amount) {
        if (!main.isQuestsEnabled()) {
            return;
        }
        QuestProgress progress = live(player.getUniqueId());
        boolean wasComplete = progress.isComplete(type);
        progress.add(type, amount);
        if (!wasComplete && progress.isComplete(type)) {
            player.sendMessage(Main.PREFIX
                    + Messages.tr("quest.completed", "quest", Messages.tr(type.getLabel())));
        }
    }

    // ================= Persistance =================

    /** Programme la sauvegarde d'un joueur (copie remise au thread d'I/O). */
    public void save(final UUID uuid) {
        QuestProgress progress = cache.get(uuid);
        if (progress == null) {
            return;
        }
        final QuestProgress copy = progress.copy();
        io.submit(new Runnable() {
            @Override
            public void run() {
                write(uuid, copy);
            }
        });
    }

    /** Sauve tout le cache. Appele a la desactivation du plugin. */
    public void shutdown() {
        for (Map.Entry<UUID, QuestProgress> entry : cache.entrySet()) {
            final UUID uuid = entry.getKey();
            final QuestProgress copy = entry.getValue().copy();
            io.submit(new Runnable() {
                @Override
                public void run() {
                    write(uuid, copy);
                }
            });
        }
        io.shutdown();
        try {
            io.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ================= Interne (thread d'I/O uniquement) =================

    private QuestProgress read(UUID uuid) {
        if (config == null) {
            return new QuestProgress(LocalDate.now());
        }
        ConfigurationSection section = config.getConfigurationSection(ROOT + "." + uuid);
        if (section == null) {
            return new QuestProgress(LocalDate.now());
        }
        LocalDate date;
        try {
            date = LocalDate.parse(section.getString("date", ""));
        } catch (DateTimeParseException e) {
            // Absent ou corrompu : on repart d'aujourd'hui, progression a zero.
            date = LocalDate.now();
        }
        QuestProgress progress = new QuestProgress(date);
        ConfigurationSection progressSection = section.getConfigurationSection("progress");
        if (progressSection != null) {
            for (QuestType type : QuestType.values()) {
                progress.set(type, progressSection.getInt(type.name(), 0));
            }
        }
        return progress;
    }

    private void write(UUID uuid, QuestProgress progress) {
        if (config == null || file == null) {
            return;
        }
        String path = ROOT + "." + uuid;
        config.set(path + ".date", progress.getDate().toString());
        for (Map.Entry<QuestType, Integer> entry : progress.asMap().entrySet()) {
            config.set(path + ".progress." + entry.getKey().name(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            main.getLogger().severe("Impossible d'ecrire quests.yml : " + e.getMessage());
        }
    }
}
