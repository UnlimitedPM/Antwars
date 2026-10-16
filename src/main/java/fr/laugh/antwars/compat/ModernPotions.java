package fr.laugh.antwars.compat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.laugh.antwars.shop.PotionKind;

/**
 * Fabrication des potions a partir de la 1.9, via {@code PotionMeta} et des
 * effets personnalises.
 *
 * <p>On n'utilise pas {@code setBasePotionData} : la classe {@code PotionData}
 * n'existe pas en 1.8, et surtout l'enum {@code PotionType} moderne a change de
 * constantes en 1.20.5. Un effet personnalise ({@code addCustomEffect}), lui,
 * s'exprime de la meme facon depuis la 1.9 et se resout par nom, ce qui traverse
 * bien toutes les versions.
 *
 * <p>Le splash est un materiau distinct a partir de la 1.9 : on le prend via
 * {@link AWMaterial#SPLASH_POTION}. Un item splash muni d'effets personnalises
 * les applique bien a l'impact.
 */
final class ModernPotions {

    private ModernPotions() {
    }

    static ItemStack build(PotionKind kind) {
        AWMaterial base = kind.isSplash() ? AWMaterial.SPLASH_POTION : AWMaterial.POTION;
        ItemStack item = base.item();

        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof PotionMeta)) {
            return item;
        }
        PotionMeta meta = (PotionMeta) rawMeta;

        PotionEffectType type = resolve(kind.getModernNames());
        if (type != null) {
            // niveau - 1 : le niveau I correspond a l'amplificateur 0. Le second
            // parametre true fait de cet effet l'effet principal de la potion.
            meta.addCustomEffect(
                    new PotionEffect(type, kind.getDurationTicks(), kind.getLevel() - 1), true);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Premier effet trouve parmi les noms candidats de la potion. */
    @SuppressWarnings("deprecation")
    private static PotionEffectType resolve(String[] names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) {
                return type;
            }
        }
        return null;
    }
}
