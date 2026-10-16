package fr.laugh.antwars.compat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Lecture des fichiers YAML <b>toujours en UTF-8</b>.
 *
 * <p>{@code YamlConfiguration.loadConfiguration(File)} lit, jusqu'en 1.12, avec
 * l'encodage par defaut du systeme : sur un serveur Windows sous Java 8, c'est
 * cp1252, et les accents de nos fichiers (enregistres en UTF-8) arrivent en
 * charabia (deux caracteres errones par lettre accentuee). Depuis Java 18
 * l'encodage par defaut est UTF-8, d'ou un bug qui n'apparait que sur les vieux
 * serveurs Windows. On passe donc par {@code loadConfiguration(Reader)} (present
 * des la 1.8) avec un lecteur UTF-8 explicite.
 */
public final class Yaml {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Yaml() {
    }

    /** Charge un fichier YAML en UTF-8 ; fichier absent ou illisible = config vide. */
    public static YamlConfiguration load(File file) {
        if (file == null || !file.exists()) {
            return new YamlConfiguration();
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF8);
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return new YamlConfiguration();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Fermeture : rien a faire de plus.
                }
            }
        }
    }
}
