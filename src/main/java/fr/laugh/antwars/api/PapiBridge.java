package fr.laugh.antwars.api;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Remplacement des placeholders de TOUS les plugins (%player_name%,
 * %luckperms_prefix%, %vault_eco_balance%...) via PlaceholderAPI, pour les
 * lignes du scoreboard configurable.
 *
 * <p><b>Isolation</b>, meme regle que {@link Placeholders} : seule cette classe
 * reference l'API de PAPI dans son code, et elle n'est appelee qu'apres avoir
 * verifie que PlaceholderAPI est present ({@code Main#hasPlaceholderApi()}).
 * La JVM ne resolvant {@code PlaceholderAPI} qu'a l'execution de l'appel, un
 * serveur sans PAPI ne la charge jamais.
 */
public final class PapiBridge {

    private PapiBridge() {
    }

    public static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
