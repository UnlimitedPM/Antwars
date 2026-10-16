package fr.laugh.antwars.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur pose sur l'inventaire de la boutique, qui retient aussi l'onglet
 * ouvert.
 *
 * <p>C'est ce qui permet de reconnaitre la boutique sans comparer son titre.
 * L'ancienne methode, {@code inventory.getName()}, a ete supprimee de l'API en
 * 1.14 : sur un serveur recent elle provoque un {@code NoSuchMethodError}.
 * {@link InventoryHolder}, lui, existe depuis la 1.8 et n'a jamais bouge ; il
 * resiste en prime au renommage du titre et aux traductions.
 */
public class ShopHolder implements InventoryHolder {

    private final Category category;
    private final boolean customize;
    private Inventory inventory;

    /** Boutique normale (achat), sur cet onglet. */
    public ShopHolder(Category category) {
        this(category, false);
    }

    /**
     * @param customize vrai pour le menu de personnalisation de l'Achat Rapide
     *                  (clic = ajoute/retire un favori, jamais un achat)
     */
    public ShopHolder(Category category, boolean customize) {
        this.category = category;
        this.customize = customize;
    }

    public Category getCategory() {
        return category;
    }

    /** Vrai si c'est le menu de personnalisation de l'Achat Rapide, pas la boutique. */
    public boolean isCustomize() {
        return customize;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
