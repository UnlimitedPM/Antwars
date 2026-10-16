package fr.laugh.antwars.game;

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Teinte l'armure en cuir vendue en boutique a la couleur de l'equipe de
 * l'acheteur, pour reconnaitre les equipes sans regarder le scoreboard. Rien
 * n'est donne gratuitement : seule l'armure achetee est coloree.
 *
 * <p>{@code LeatherArmorMeta} existe depuis la 1.8, sans divergence a isoler.
 */
public final class TeamArmor {

    private TeamArmor() {
    }

    /**
     * Teint cet objet a la couleur donnee s'il s'agit d'une piece de cuir ; sans
     * effet sur toute autre armure (fer, diamant...).
     */
    public static void dye(ItemStack item, Color color) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta) {
            LeatherArmorMeta leather = (LeatherArmorMeta) meta;
            leather.setColor(color);
            item.setItemMeta(leather);
        }
    }
}
