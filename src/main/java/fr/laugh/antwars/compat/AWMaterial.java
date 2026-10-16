package fr.laugh.antwars.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.cryptomorin.xseries.XMaterial;

/**
 * Tous les materiaux utilises par AntWars, sous une forme qui survit au
 * changement de nommage de la 1.13 ("The Flattening").
 *
 * <p>Le probleme resolu ici : ecrire {@code Material.WOOD_SWORD} dans le code
 * compile une reference directe au champ de l'enum. Ce champ n'existe plus a
 * partir de la 1.13, et la JVM leve alors un {@code NoSuchFieldError} des le
 * chargement de la classe, ce qui tue le plugin au demarrage.
 *
 * <p>La resolution est donc deleguee a XMaterial (bibliotheque XSeries), qui
 * connait la correspondance entre les noms de toutes les versions et gere au
 * passage les valeurs de data des versions 1.8 a 1.12 : c'est elle qui sait
 * qu'un panneau de verre gris s'obtient avec {@code STAINED_GLASS_PANE:7} en
 * 1.8 et {@code GRAY_STAINED_GLASS_PANE} en 1.13+.
 *
 * <p>Cette classe est l'un des trois seuls points de contact entre AntWars et
 * XSeries (les deux autres etant {@code Heads}, pour les skins de tete du NPC
 * de boutique, et {@code Sounds}, pour les sons cross-version) : changer de
 * bibliotheque ne demanderait de toucher qu'a ces trois-la.
 */
public enum AWMaterial {

    // --- Identiques sur toutes les versions ---
    DIRT(XMaterial.DIRT),
    TORCH(XMaterial.TORCH),
    TNT(XMaterial.TNT),
    CHEST(XMaterial.CHEST),
    COOKED_BEEF(XMaterial.COOKED_BEEF),
    GOLDEN_APPLE(XMaterial.GOLDEN_APPLE),
    FLINT_AND_STEEL(XMaterial.FLINT_AND_STEEL),
    STONE_SWORD(XMaterial.STONE_SWORD),
    IRON_SWORD(XMaterial.IRON_SWORD),
    NETHER_STAR(XMaterial.NETHER_STAR),
    CLOCK(XMaterial.CLOCK),                         // WATCH avant la 1.13 (icone du temps des bordures)
    DEAD_BUSH(XMaterial.DEAD_BUSH),
    PAPER(XMaterial.PAPER),
    COMPASS(XMaterial.COMPASS),
    BARRIER(XMaterial.BARRIER),
    WRITTEN_BOOK(XMaterial.WRITTEN_BOOK),
    BOW(XMaterial.BOW),
    ARROW(XMaterial.ARROW),
    LADDER(XMaterial.LADDER),
    WATER_BUCKET(XMaterial.WATER_BUCKET),
    LEATHER_HELMET(XMaterial.LEATHER_HELMET),
    LEATHER_CHESTPLATE(XMaterial.LEATHER_CHESTPLATE),
    LEATHER_LEGGINGS(XMaterial.LEATHER_LEGGINGS),
    LEATHER_BOOTS(XMaterial.LEATHER_BOOTS),
    CHAINMAIL_HELMET(XMaterial.CHAINMAIL_HELMET),
    CHAINMAIL_CHESTPLATE(XMaterial.CHAINMAIL_CHESTPLATE),
    CHAINMAIL_LEGGINGS(XMaterial.CHAINMAIL_LEGGINGS),
    CHAINMAIL_BOOTS(XMaterial.CHAINMAIL_BOOTS),

    // --- Renommes en 1.13 (anciens noms entre parentheses) ---
    WOODEN_SWORD(XMaterial.WOODEN_SWORD),          // WOOD_SWORD
    WOODEN_SHOVEL(XMaterial.WOODEN_SHOVEL),        // WOOD_SPADE
    STONE_SHOVEL(XMaterial.STONE_SHOVEL),          // STONE_SPADE
    IRON_SHOVEL(XMaterial.IRON_SHOVEL),            // IRON_SPADE
    GOLDEN_SWORD(XMaterial.GOLDEN_SWORD),          // GOLD_SWORD
    MUSHROOM_STEW(XMaterial.MUSHROOM_STEW),        // MUSHROOM_SOUP
    GLASS_PANE(XMaterial.GLASS_PANE),              // THIN_GLASS
    END_STONE(XMaterial.END_STONE),                // ENDER_STONE
    PLAYER_HEAD(XMaterial.PLAYER_HEAD),            // SKULL_ITEM:3 (tete du NPC boutique)

    // --- Renomme ET colore en 1.13 ---
    GRAY_STAINED_GLASS_PANE(XMaterial.GRAY_STAINED_GLASS_PANE), // STAINED_GLASS_PANE:7
    // Pastilles de statut du menu de configuration (vert = defini, rouge = manquant).
    GREEN_STAINED_GLASS_PANE(XMaterial.GREEN_STAINED_GLASS_PANE), // STAINED_GLASS_PANE:5
    RED_STAINED_GLASS_PANE(XMaterial.RED_STAINED_GLASS_PANE),     // STAINED_GLASS_PANE:14

    // --- Potions ---
    // Sur 1.8 il n'existe qu'un materiau POTION : la difference boire/splash y
    // passe par la valeur de data (geree par la classe org.bukkit.potion.Potion,
    // cote LegacyPotions). SPLASH_POTION n'apparait comme materiau distinct qu'a
    // partir de la 1.9 ; il n'est donc utilise que par le chemin moderne.
    POTION(XMaterial.POTION),
    SPLASH_POTION(XMaterial.SPLASH_POTION),

    // --- Lits d'equipe ---
    // Sur 1.8 a 1.12 il n'existe qu'un seul lit : les quatre constantes y
    // renvoient donc toutes le meme item rouge. Ce n'est qu'un ecart visuel,
    // le plugin identifiant les lits par leur position enregistree et non par
    // leur couleur.
    RED_BED(XMaterial.RED_BED),
    BLUE_BED(XMaterial.BLUE_BED),
    YELLOW_BED(XMaterial.YELLOW_BED),
    GREEN_BED(XMaterial.GREEN_BED);

    private final XMaterial delegate;

    /** Materiau resolu a l'execution, mis en cache. */
    private Material resolved;

    AWMaterial(XMaterial delegate) {
        this.delegate = delegate;
    }

    /**
     * Materiau correspondant sur la version en cours, ou {@code null} s'il
     * n'existe pas.
     */
    public Material material() {
        if (resolved == null) {
            resolved = delegate.parseMaterial();
        }
        return resolved;
    }

    /** Vrai si {@code other} est ce materiau sur la version en cours. */
    public boolean is(Material other) {
        return other != null && other == material();
    }

    public ItemStack item() {
        return item(1);
    }

    /**
     * {@code amount} exemplaires de cet item.
     *
     * <p>C'est {@code parseItem()} qui applique la valeur de data attendue par
     * les versions 1.8 a 1.12.
     */
    public ItemStack item(int amount) {
        ItemStack item = delegate.parseItem();
        if (item == null) {
            throw new IllegalStateException("Materiau introuvable pour " + name()
                    + " sur la version " + MCVersion.raw());
        }
        item.setAmount(amount);
        return item;
    }

    /**
     * Resout tous les materiaux d'un coup au demarrage, pour decouvrir un
     * eventuel probleme de compatibilite tout de suite plutot qu'au milieu
     * d'une partie.
     *
     * @return la liste des materiaux introuvables, vide si tout va bien
     */
    public static List<String> resolveAll(Logger logger) {
        List<String> missing = new ArrayList<String>();
        for (AWMaterial value : values()) {
            if (!value.delegate.isSupported() || value.material() == null) {
                missing.add(value.name());
            }
        }
        if (!missing.isEmpty()) {
            logger.warning("Materiaux introuvables sur " + MCVersion.raw() + " : " + missing);
        }
        return missing;
    }

    /**
     * Item de <b>n'importe quel</b> materiau nomme (nom moderne ou d'avant la
     * 1.13), pour la boutique configurable ou l'admin peut ajouter des articles
     * a sa guise. Contrairement a {@link #fromName}, non limite aux materiaux
     * declares dans cet enum : XSeries reste seul juge, ce qui garde ce fichier
     * comme unique point de contact avec XMaterial.
     *
     * @return l'item dans la quantite voulue, ou {@code null} si le nom ne
     *         correspond a aucun materiau existant sur cette version
     */
    public static ItemStack itemFromName(String name, int amount) {
        if (name == null) {
            return null;
        }
        Optional<XMaterial> match = XMaterial.matchXMaterial(name.trim());
        if (!match.isPresent() || !match.get().isSupported()) {
            return null;
        }
        ItemStack item = match.get().parseItem();
        if (item == null) {
            return null;
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    /**
     * Retrouve une constante AntWars a partir d'un nom ecrit dans la config.
     * Les noms modernes comme ceux d'avant la 1.13 sont acceptes, XMaterial
     * se chargeant de la correspondance.
     *
     * @return la constante, ou {@code null} si le nom ne correspond a aucun
     *         article du plugin
     */
    public static AWMaterial fromName(String name) {
        if (name == null) {
            return null;
        }
        Optional<XMaterial> match = XMaterial.matchXMaterial(name.trim());
        if (!match.isPresent()) {
            return null;
        }
        XMaterial found = match.get();
        for (AWMaterial value : values()) {
            if (value.delegate == found) {
                return value;
            }
        }
        return null;
    }
}
