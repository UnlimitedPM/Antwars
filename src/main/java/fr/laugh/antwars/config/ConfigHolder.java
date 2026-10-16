package fr.laugh.antwars.config;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur pose sur les inventaires du menu de configuration d'arene.
 *
 * <p>Meme principe que {@code ShopHolder} : on reconnait un menu de config a son
 * {@link InventoryHolder} (stable depuis la 1.8), jamais a son titre. Le holder
 * retient de plus DE QUEL menu il s'agit ({@link Type}), sur quelle arene, et
 * pour quelle equipe le cas echeant : c'est ce qui permet a un seul listener de
 * router chaque clic vers la bonne action.
 */
public class ConfigHolder implements InventoryHolder {

    public enum Type {
        /** Liste de toutes les arenes. */
        LIST,
        /** Edition d'une arene precise. */
        EDIT,
        /** Reglages d'une equipe (spawn, lit). */
        TEAM,
        /** Confirmation de suppression d'une arene. */
        DELETE
    }

    private final Type type;
    private final String arena;
    private final String team;
    private Inventory inventory;

    public ConfigHolder(Type type, String arena, String team) {
        this.type = type;
        this.arena = arena;
        this.team = team;
    }

    public Type getType() {
        return type;
    }

    /** Nom de l'arene editee, ou {@code null} pour la liste. */
    public String getArena() {
        return arena;
    }

    /** Equipe concernee (menu {@link Type#TEAM}), ou {@code null}. */
    public String getTeam() {
        return team;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
