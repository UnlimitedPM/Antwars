package fr.laugh.antwars.shop;

import fr.laugh.antwars.compat.AWMaterial;

/**
 * Les onglets de la boutique, dans leur ordre d'affichage.
 */
public enum Category {

    /** Selection des articles les plus utiles, toutes categories confondues. */
    QUICK("category.quick", AWMaterial.NETHER_STAR),

    WEAPONS("category.weapons", AWMaterial.GOLDEN_SWORD),
    TOOLS("category.tools", AWMaterial.STONE_SHOVEL),
    ARMOR("category.armor", AWMaterial.CHAINMAIL_CHESTPLATE),
    FOOD("category.food", AWMaterial.MUSHROOM_STEW),
    MISC("category.misc", AWMaterial.DEAD_BUSH);

    // Le titre est une CLE de traduction (voir lang/*.yml) ; il est traduit au
    // moment de l'affichage dans ShopMenu, jamais stocke deja traduit.
    private final String title;
    private final AWMaterial icon;

    Category(String title, AWMaterial icon) {
        this.title = title;
        this.icon = icon;
    }

    /** Cle de traduction du titre de l'onglet (a passer a Messages.tr). */
    public String getTitle() {
        return title;
    }

    public AWMaterial getIcon() {
        return icon;
    }

    /** Emplacement de l'onglet dans la premiere ligne du menu. */
    public int getSlot() {
        return ordinal();
    }

    public static Category fromSlot(int slot) {
        Category[] values = values();
        return slot >= 0 && slot < values.length ? values[slot] : null;
    }

    /** Categorie correspondant a un nom de config (insensible a la casse), ou {@code null}. */
    public static Category fromName(String name) {
        if (name == null) {
            return null;
        }
        for (Category category : values()) {
            if (category.name().equalsIgnoreCase(name.trim())) {
                return category;
            }
        }
        return null;
    }
}
