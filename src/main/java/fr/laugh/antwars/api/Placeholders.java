package fr.laugh.antwars.api;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import fr.laugh.antwars.Main;

/**
 * Expansion PlaceholderAPI : expose les stats et l'etat de jeu sous forme de
 * placeholders {@code %antwars_...%}, utilisables par n'importe quel plugin
 * d'affichage (scoreboard, tab, hologrammes, chat) sans qu'il connaisse AntWars.
 *
 * <p><b>Isolation</b> : cette classe etend une classe de PlaceholderAPI. Elle
 * n'est instanciee que par {@link Main} <b>apres avoir verifie que le plugin
 * PlaceholderAPI est present</b> ; la JVM ne la resout donc jamais sur un serveur
 * sans PAPI (meme technique que le driver MariaDB). Ne jamais la referencer
 * ailleurs sans ce garde.
 *
 * <p>Les valeurs elles-memes (liste des placeholders comprise) vivent dans
 * {@link PlaceholderValues}, qui ne depend pas de PAPI : les lignes du
 * scoreboard s'en servent aussi sans lui. Les stats viennent du cache (joueur
 * connecte) ; un joueur hors ligne renvoie des zeros.
 */
public class Placeholders extends PlaceholderExpansion {

    private final Main main;

    public Placeholders(Main main) {
        this.main = main;
    }

    @Override
    public String getIdentifier() {
        return "antwars";
    }

    @Override
    public String getAuthor() {
        return "xLaugh";
    }

    @Override
    public String getVersion() {
        return main.getDescription().getVersion();
    }

    /** Reste enregistree quand PlaceholderAPI se recharge. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // null = placeholder inconnu : PlaceholderAPI laisse le texte tel quel.
        return PlaceholderValues.resolve(main, player, params);
    }
}
