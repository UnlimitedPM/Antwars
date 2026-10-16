package fr.laugh.antwars.compat;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Petites operations dont l'ecriture "naturelle" casserait sur une partie de
 * l'intervalle 1.8 - 1.21. Tout ce qui est ici compile contre l'API 1.8 et
 * s'execute sans erreur jusqu'en 1.21.
 */
public final class Compat {

    private Compat() {
    }

    /**
     * Remet un joueur a pleine vie.
     *
     * <p>L'ecriture {@code player.setHealth(20)} est fausse : elle leve une
     * {@code IllegalArgumentException} des qu'un autre plugin, un attribut ou
     * un effet a modifie la vie maximale du joueur. On lit donc toujours le
     * maximum reel.
     *
     * <p>{@code getMaxHealth()} est deprecie depuis la 1.9 au profit de
     * l'API Attribute, mais il est toujours present en 1.21 et reste le seul
     * appel qui fonctionne aussi en 1.8. L'API Attribute ne conviendrait pas :
     * l'enum a encore change en 1.21.3.
     */
    @SuppressWarnings("deprecation")
    public static void healFully(Player player) {
        player.setHealth(player.getMaxHealth());
    }

    /**
     * Rend a un joueur une vie donnee (celle qu'il avait avant de se deconnecter),
     * bornee a son maximum reel et jamais inferieure a un demi-coeur : une vie
     * nulle le tuerait, et une vie superieure au maximum leverait une
     * {@code IllegalArgumentException} (voir {@link #healFully}).
     */
    @SuppressWarnings("deprecation")
    public static void restoreHealth(Player player, double health) {
        player.setHealth(Math.max(1d, Math.min(health, player.getMaxHealth())));
    }

    /**
     * Remet un joueur completement a zero : inventaire, armure, vie, faim,
     * experience, effets et feu. A utiliser a chaque changement de phase pour
     * eviter qu'un etat de partie precedente ne fuite dans la suivante.
     */
    public static void resetPlayer(Player player, GameMode mode) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        healFully(player);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);

        player.setLevel(0);
        player.setExp(0f);

        player.setFireTicks(0);
        player.setFallDistance(0f);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setAllowFlight(mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        player.setFlying(false);
        // Annule makeSpectator si le joueur en revient.
        player.setCanPickupItems(true);
        player.setGameMode(mode);
    }

    /**
     * Met un joueur en observateur : vol, invisible, rien a ramasser. Il n'est
     * PAS en mode "spectateur" de Minecraft (qui cache la hotbar et dont le menu
     * de teleportation n'existe pas en 1.8) mais en aventure, ce qui laisse ses
     * objets (boussole, barriere) utilisables sur toutes les versions.
     */
    public static void makeSpectator(Player player) {
        resetPlayer(player, GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCanPickupItems(false);
        // Invisibilite : ceinture et bretelles en plus du masquage (voir setHidden).
        // Un champ statique de type d'effet qui changerait de nom dans une version
        // future ne doit pas empecher de regarder une partie : le masquage suffit.
        try {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, 0, true, false));
        } catch (Throwable ignored) {
            // Sans cet effet, setHidden cache deja le spectateur.
        }
    }

    /**
     * Cache (ou rend visible) {@code target} aux yeux de {@code viewer}.
     * {@code hidePlayer(Player)} est deprecie depuis la 1.12 au profit d'une
     * variante a plugin, mais reste servi en 1.21 et seul present en 1.8.
     */
    @SuppressWarnings("deprecation")
    public static void setHidden(Player viewer, Player target, boolean hidden) {
        try {
            if (hidden) {
                viewer.hidePlayer(target);
            } else {
                viewer.showPlayer(target);
            }
        } catch (Throwable ignored) {
            // Sans masquage, l'invisibilite de makeSpectator suffit.
        }
    }

    /** Traduit les codes couleur ecrits avec {@code &} en codes Minecraft. */
    public static String color(String message) {
        return message == null ? "" : ChatColor.translateAlternateColorCodes('&', message);
    }
}
