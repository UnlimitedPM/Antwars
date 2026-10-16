package fr.laugh.antwars.compat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import fr.laugh.antwars.shop.PotionKind;

/**
 * Fabrication des potions sur 1.8-1.12, via {@code org.bukkit.potion.Potion}.
 *
 * <p>Cette classe ne doit tourner QUE sur les versions legacy : elle est
 * appelee exclusivement derriere le test de version de {@link Potions}. C'est
 * indispensable car {@code org.bukkit.potion.Potion} a ete supprimee des
 * versions recentes ; ici, la reference n'apparait que dans le corps de
 * {@link #build}, donc la JVM ne tente de la resoudre qu'a l'execution reelle
 * sur un serveur legacy, ou la classe existe.
 *
 * <p>Le nom du type de regeneration en 1.8 est {@code REGEN} (et non
 * {@code REGENERATION}) ; c'est {@link PotionKind#getLegacyType()} qui le porte.
 * La classe {@code Potion} pose la bonne valeur de data, ce qui donne au passage
 * les durees vanilla exactes attendues.
 */
final class LegacyPotions {

    private LegacyPotions() {
    }

    @SuppressWarnings("deprecation")
    static ItemStack build(PotionKind kind) {
        PotionType type;
        try {
            type = PotionType.valueOf(kind.getLegacyType());
        } catch (IllegalArgumentException e) {
            // Type absent sur cette version legacy : on renonce plutot que de
            // faire echouer l'achat.
            return null;
        }
        // Constructeur (type, niveau 1-base, splash) present depuis la 1.8.
        Potion potion = new Potion(type, kind.getLevel(), kind.isSplash());
        return potion.toItemStack(1);
    }
}
