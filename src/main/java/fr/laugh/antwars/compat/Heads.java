package fr.laugh.antwars.compat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.cryptomorin.xseries.SkullUtils;

/**
 * Tetes de joueur skinnees, pour le NPC de boutique (un armor stand coiffe
 * d'une tete).
 *
 * <p>Un des trois points de contact avec XSeries, avec {@link AWMaterial} et
 * {@link Sounds} : {@code SkullUtils} applique un skin de la meme facon sur
 * toutes les versions, a partir d'un pseudo, d'un UUID, d'une texture base64 ou
 * d'une URL. Reconstruire ce mecanisme a la main demanderait la meme jonglerie
 * de {@code GameProfile} version par version qu'un vrai NPC packet ; on delegue.
 */
public final class Heads {

    private Heads() {
    }

    /**
     * Tete du NPC de boutique. {@code skin} vide renvoie une tete par defaut
     * (Steve) ; sinon on applique la peau demandee.
     *
     * <p>Un skin invalide (ou un lookup reseau indisponible) ne doit jamais
     * empecher le NPC d'exister : on retombe alors sur la tete par defaut.
     */
    public static ItemStack shopHead(String skin) {
        ItemStack head = AWMaterial.PLAYER_HEAD.item();
        if (skin == null || skin.trim().isEmpty()) {
            return head;
        }
        ItemMeta meta = head.getItemMeta();
        if (meta == null) {
            return head;
        }
        try {
            head.setItemMeta(SkullUtils.applySkin(meta, skin.trim()));
        } catch (Throwable t) {
            // Peau introuvable ou API indisponible : tete par defaut.
        }
        return head;
    }
}
