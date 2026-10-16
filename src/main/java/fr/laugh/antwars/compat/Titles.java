package fr.laugh.antwars.compat;

import org.bukkit.entity.Player;

/**
 * Affichage d'un titre au centre de l'ecran, de la 1.8 a la 1.21.
 *
 * <p>On passe par {@code Player.sendTitle(String, String)}. Cette methode est
 * depreciee depuis la 1.11 (au profit de la variante avec durees, ABSENTE de
 * l'API 1.8), mais elle est toujours presente en 1.21 et reste le seul appel qui
 * fonctionne aussi en 1.8 sans toucher aux paquets NMS. Isolee ici : si elle
 * disparait un jour, un seul endroit a corriger.
 *
 * <p>Sans controle des durees, chaque nouvel appel remplace simplement le titre
 * precedent - ce qui suffit pour un compte a rebours qui change chaque seconde.
 */
public final class Titles {

    private Titles() {
    }

    @SuppressWarnings("deprecation")
    public static void send(Player player, String title, String subtitle) {
        player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle);
    }
}
