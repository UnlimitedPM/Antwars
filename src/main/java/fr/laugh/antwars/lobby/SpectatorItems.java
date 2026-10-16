package fr.laugh.antwars.lobby;

import java.util.Arrays;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.lang.Messages;

/**
 * Objets de la hotbar d'un spectateur : une boussole qui ouvre la liste des
 * joueurs (slot 0) et une barriere pour quitter (slot 8).
 *
 * <p>Le spectateur n'est pas en mode "spectateur" de Minecraft, qui cache la
 * hotbar : il est en aventure, en vol et invisible (voir
 * {@code Compat.makeSpectator}), ce qui laisse ses objets utilisables.
 */
public final class SpectatorItems {

    public static final int SLOT_PLAYERS = 0;
    public static final int SLOT_LEAVE = 8;

    private SpectatorItems() {
    }

    public static void give(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(SLOT_PLAYERS, named(AWMaterial.COMPASS.item(),
                Messages.tr("spectator.players_name"), Messages.tr("lobby.right_click")));
        inventory.setItem(SLOT_LEAVE, named(AWMaterial.BARRIER.item(),
                Messages.tr("lobby.leave_name"), Messages.tr("lobby.right_click")));
    }

    private static ItemStack named(ItemStack item, String name, String lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
