package fr.laugh.antwars.shop;

import java.util.Locale;

import org.bukkit.inventory.ItemStack;

import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.compat.Potions;

/**
 * Un article de la boutique, decrit dans config.yml : materiau, categorie,
 * emplacement, prix, quantite.
 *
 * <p>Un article est soit un item ordinaire (un materiau nomme, resolu par
 * {@link AWMaterial#itemFromName}), soit une potion (un {@link PotionKind}, dont
 * l'effet varie selon la version, batie par {@link Potions}). Dans les deux cas,
 * {@link #buildStack()} donne l'item pret a etre affiche ou remis.
 *
 * <p>Nom affiche : {@link #getName()} si l'admin a mis un {@code name:} dans la
 * config (nom personnalise, une langue), sinon {@link #getLabel()} sert de cle de
 * traduction ({@code item.<id>}) pour garder les articles par defaut multilingues.
 */
public class ShopItem {

    private final Category category;
    /** Nom du materiau tel qu'ecrit en config ; {@code null} pour une potion. */
    private final String materialName;
    /** Cle de traduction du libelle (ex. {@code item.wooden_sword}). */
    private final String label;
    /** Nom personnalise (config), ou {@code null} pour utiliser la traduction. */
    private final String name;
    private final int amount;
    private final int price;
    private final boolean quick;
    /** Position dans la grille de l'onglet (0 a 20). */
    private final int slot;
    /** Non nul si l'article est une potion. */
    private final PotionKind potion;

    public ShopItem(Category category, String materialName, String label, String name,
            int amount, int price, boolean quick, int slot) {
        this.category = category;
        this.materialName = materialName;
        this.label = label;
        this.name = name;
        this.amount = Math.max(1, amount);
        this.price = price;
        this.quick = quick;
        this.slot = slot;
        this.potion = null;
    }

    /** Article potion : l'item est bati par {@link Potions}, pas par un materiau nomme. */
    public ShopItem(Category category, PotionKind potion, int price, boolean quick, int slot) {
        this.category = category;
        this.materialName = null;
        this.label = potion.getLabel();
        this.name = null;
        this.amount = 1;
        this.price = price;
        this.quick = quick;
        this.slot = slot;
        this.potion = potion;
    }

    /**
     * Item pret a l'emploi : la potion batie par {@link Potions} si c'en est une,
     * sinon l'item ordinaire dans la quantite voulue. Peut valoir {@code null} si
     * le materiau est introuvable (normalement ecarte des le chargement).
     */
    public ItemStack buildStack() {
        if (potion != null) {
            return Potions.build(potion);
        }
        return AWMaterial.itemFromName(materialName, amount);
    }

    public Category getCategory() {
        return category;
    }

    /** Nom du materiau (config), ou {@code null} pour une potion. */
    public String getMaterialName() {
        return materialName;
    }

    public String getLabel() {
        return label;
    }

    /** Nom personnalise, ou {@code null} s'il faut utiliser la traduction du libelle. */
    public String getName() {
        return name;
    }

    public int getAmount() {
        return amount;
    }

    public int getPrice() {
        return price;
    }

    public boolean isQuick() {
        return quick;
    }

    public int getSlot() {
        return slot;
    }

    public PotionKind getPotion() {
        return potion;
    }

    public boolean isPotion() {
        return potion != null;
    }

    /** Un article a prix nul ou negatif est simplement retire de la vente. */
    public boolean isSold() {
        return price > 0;
    }

    /**
     * Vrai si cet article est une piece d'armure a equiper. On se fie au suffixe
     * du nom de materiau, inchange entre la 1.8 et la 1.21.
     */
    public boolean isArmor() {
        if (materialName == null) {
            return false;
        }
        String n = materialName.toUpperCase(Locale.ROOT);
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }
}
