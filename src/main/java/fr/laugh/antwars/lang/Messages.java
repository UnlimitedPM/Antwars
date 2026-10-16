package fr.laugh.antwars.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import fr.laugh.antwars.Main;

import fr.laugh.antwars.compat.Yaml;

/**
 * Traduction des messages joueur. La langue est choisie pour tout le serveur
 * par l'option {@code language} de la config.
 *
 * <p>Les textes vivent dans {@code lang/<code>.yml} (fr, en, es, it, de, pt), embarques dans
 * le jar et recopies dans le dossier du plugin pour pouvoir etre edites. Chaque
 * cle donne un texte avec codes couleur {@code &} et variables {@code %nom%}.
 *
 * <p>Repli : une cle absente du fichier choisi est cherchee dans le francais
 * embarque (toujours complet). Absente partout, on renvoie la cle elle-meme,
 * ce qui saute aux yeux et aide a reperer un oubli.
 */
public class Messages {

    private static final String[] BUNDLED = { "fr", "en", "es", "it", "de", "pt" };
    private static final String FALLBACK = "fr";

    /**
     * Instance courante, pour l'acces statique {@link #tr}. Les messages sont
     * demandes depuis partout (y compris des classes sans reference a Main :
     * objets de lobby, taches, enums) ; un point d'acces statique evite de faire
     * transiter Main dans chacune.
     */
    private static Messages instance;

    private final Main main;
    private FileConfiguration lang;
    private FileConfiguration fallback;

    public Messages(Main main) {
        this.main = main;
        instance = this;
        load();
    }

    /** Message traduit, ou la cle si le systeme n'est pas encore pret. */
    public static String tr(String key, Object... placeholders) {
        return instance == null ? key : instance.get(key, placeholders);
    }

    /**
     * Liste de lignes traduite (couleurs {@code &} appliquees) : celle de la
     * langue choisie, sinon celle du francais embarque, sinon une liste vide.
     * Sert a la disposition par defaut du scoreboard.
     */
    public static List<String> trList(String key) {
        List<String> result = new ArrayList<String>();
        if (instance == null) {
            return result;
        }
        FileConfiguration[] sources = { instance.lang, instance.fallback };
        for (FileConfiguration source : sources) {
            if (source != null && source.isList(key) && !source.getStringList(key).isEmpty()) {
                for (String line : source.getStringList(key)) {
                    result.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                return result;
            }
        }
        return result;
    }

    /** (Re)charge la langue configuree. Appelee au demarrage et au reload. */
    public void load() {
        for (String code : BUNDLED) {
            saveIfAbsent(code);
        }

        String code = main.getConfig().getString("language", FALLBACK).toLowerCase();
        File file = new File(main.getDataFolder(), "lang/" + code + ".yml");
        if (!file.exists()) {
            main.getLogger().warning("Langue '" + code + "' introuvable, retour au francais.");
            code = FALLBACK;
            file = new File(main.getDataFolder(), "lang/" + FALLBACK + ".yml");
        }
        lang = Yaml.load(file);
        fallback = fromJar(FALLBACK);
        main.getLogger().info("Langue chargee : " + code + ".");
    }

    /**
     * Message traduit : couleurs {@code &} appliquees et variables {@code %nom%}
     * remplacees. Les variables se passent par paires, nom puis valeur :
     * {@code get("arena.joined", "player", nom, "count", 3)}.
     */
    public String get(String key, Object... placeholders) {
        String raw = lang == null ? null : lang.getString(key);
        if (raw == null && fallback != null) {
            raw = fallback.getString(key);
        }
        if (raw == null) {
            return key;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private void saveIfAbsent(String code) {
        File file = new File(main.getDataFolder(), "lang/" + code + ".yml");
        if (file.exists()) {
            return;
        }
        try {
            main.saveResource("lang/" + code + ".yml", false);
        } catch (IllegalArgumentException e) {
            main.getLogger().warning("Fichier de langue absent du jar : lang/" + code + ".yml");
        }
    }

    private FileConfiguration fromJar(String code) {
        InputStream in = main.getResource("lang/" + code + ".yml");
        if (in == null) {
            return new YamlConfiguration();
        }
        // Lecture en UTF-8 pour que les accents du fichier arrivent intacts.
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, Charset.forName("UTF-8")));
    }
}
