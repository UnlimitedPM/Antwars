package fr.laugh.antwars.compat;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.shop.PotionKind;

/**
 * Fabrique une potion de boutique, de la 1.8 a la 1.21.
 *
 * <p>C'est le point le plus divergent du plugin. Deux mondes irreconciliables :
 * <ul>
 *   <li>1.8-1.12 : l'effet est encode dans la valeur de data de l'item, posee
 *       par {@code org.bukkit.potion.Potion} ; il n'existe pas de materiau
 *       splash distinct, et la classe {@code PotionData} n'existe pas ;</li>
 *   <li>1.9+ : l'effet passe par {@code PotionMeta} (effets personnalises), le
 *       splash est un materiau a part, et sur les versions recentes la classe
 *       {@code org.bukkit.potion.Potion} a fini par disparaitre.</li>
 * </ul>
 *
 * <p>Impossible ici d'appliquer la ruse de {@link Beds} (n'utiliser aucune des
 * deux API) : une potion n'a pas d'equivalent neutre. On isole donc chaque
 * monde dans sa propre classe, {@link LegacyPotions} et {@link ModernPotions}.
 *
 * <p><b>Pourquoi deux classes et pas un simple {@code if}</b> : la JVM ne resout
 * une classe qu'a la premiere execution de l'appel qui la designe, pas au
 * chargement de l'appelant. Tant que {@code LegacyPotions.build} n'est jamais
 * execute (serveur moderne), {@code org.bukkit.potion.Potion} n'est jamais
 * resolu, donc son absence ne fait rien planter. La condition de version
 * ci-dessous est donc ce qui garantit qu'on ne touche jamais la mauvaise API.
 * Corollaire a respecter : dans ces deux classes, les types propres a une
 * version ne doivent apparaitre que dans des corps de methode, jamais dans une
 * signature ou un champ.
 */
public final class Potions {

    private Potions() {
    }

    /** Construit l'item de la potion demandee, effet compris et deja nomme. */
    public static ItemStack build(PotionKind kind) {
        ItemStack item = MCVersion.isLegacy()
                ? LegacyPotions.build(kind)
                : ModernPotions.build(kind);

        if (item == null) {
            // Repli defensif : au pire un flacon inerte, mais l'achat aboutit.
            item = AWMaterial.POTION.item();
        }

        // Nom lisible, commun aux deux versions. WHITE evite l'italique que
        // certaines versions appliquent aux noms personnalises.
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + Messages.tr(kind.getLabel()));
            item.setItemMeta(meta);
        }
        return item;
    }
}
