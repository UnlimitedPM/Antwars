package fr.laugh.antwars.compat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Etat d'un joueur mis de cote pendant une deconnexion en pleine partie :
 * inventaire, armure, XP, vie et faim.
 *
 * <p>Pourquoi le garder en memoire plutot que de laisser l'inventaire dans le
 * fichier du joueur : si le joueur ne revient jamais, rien de la partie ne doit
 * le suivre ailleurs sur le serveur. On vide donc son vrai inventaire au depart
 * et on le lui rend s'il revient ; sinon la copie est simplement oubliee.
 *
 * <p>La vie et la faim sont rendues telles quelles : sans cela, se deconnecter
 * puis revenir soignerait completement le joueur.
 *
 * <p>Cross-version : en 1.8 {@code getContents()} renvoie les cases principales
 * seules, a partir de la 1.9 il inclut aussi armure et seconde main. On rejoue
 * donc {@code setContents} PUIS {@code setArmorContents} : sur les deux familles
 * de versions, le resultat est le meme inventaire.
 */
public final class PlayerSnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final int level;
    private final float exp;
    private final double health;
    private final int food;

    private PlayerSnapshot(ItemStack[] contents, ItemStack[] armor, int level, float exp,
            double health, int food) {
        this.contents = contents;
        this.armor = armor;
        this.level = level;
        this.exp = exp;
        this.health = health;
        this.food = food;
    }

    public static PlayerSnapshot capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new PlayerSnapshot(copy(inventory.getContents()), copy(inventory.getArmorContents()),
                player.getLevel(), player.getExp(), player.getHealth(), player.getFoodLevel());
    }

    /** Rend l'etat au joueur (inventaire deja vide, mode de jeu deja remis). */
    public void restore(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setContents(copy(contents));
        inventory.setArmorContents(copy(armor));
        player.setLevel(level);
        player.setExp(exp);
        player.setFoodLevel(food);
        Compat.restoreHealth(player, health);
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }
}
