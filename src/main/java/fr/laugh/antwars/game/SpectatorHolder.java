package fr.laugh.antwars.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur pose sur le menu des joueurs d'un spectateur (comme ShopHolder) : on
 * reconnait le menu a son holder, jamais a son titre, et il retient quel joueur
 * se cache derriere chaque case.
 */
public class SpectatorHolder implements InventoryHolder {

    private final Map<Integer, UUID> playerBySlot = new HashMap<Integer, UUID>();
    private Inventory inventory;

    void bind(int slot, UUID playerId) {
        playerBySlot.put(Integer.valueOf(slot), playerId);
    }

    /** Joueur dans cette case, ou {@code null}. */
    public UUID playerAt(int slot) {
        return playerBySlot.get(Integer.valueOf(slot));
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
