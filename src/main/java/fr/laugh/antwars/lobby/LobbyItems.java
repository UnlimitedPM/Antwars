package fr.laugh.antwars.lobby;

import java.util.Arrays;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.lang.Messages;

/**
 * Objets de la hotbar donnes aux joueurs dans le lobby d'attente d'une arene :
 * un livre d'explication (slot 0), un papier pour voter le demarrage (slot 4),
 * une barriere pour quitter (slot 8).
 *
 * <p>Ces objets sont posables/cliquables sur toutes les versions (papier,
 * barriere depuis la 1.8, livre signe via {@code BookMeta}). Ils disparaissent
 * au lancement de la partie, l'inventaire etant remis a zero a ce moment.
 */
public final class LobbyItems {

    public static final int SLOT_BOOK = 0;
    public static final int SLOT_VOTE = 4;
    public static final int SLOT_LEAVE = 8;

    private LobbyItems() {
    }

    public static void give(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(SLOT_BOOK, book());
        inventory.setItem(SLOT_VOTE, voteItem());
        inventory.setItem(SLOT_LEAVE, leaveItem());
    }

    private static ItemStack voteItem() {
        return named(AWMaterial.PAPER.item(),
                Messages.tr("lobby.vote_name"),
                Messages.tr("lobby.right_click"));
    }

    private static ItemStack leaveItem() {
        return named(AWMaterial.BARRIER.item(),
                Messages.tr("lobby.leave_name"),
                Messages.tr("lobby.right_click"));
    }

    private static ItemStack book() {
        ItemStack item = AWMaterial.WRITTEN_BOOK.item();
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BookMeta) {
            BookMeta book = (BookMeta) meta;
            // Titre/auteur simples (sans couleur) : c'est ce qui rend le livre
            // "signe" donc ouvrable en lecture d'un clic droit sur toutes les
            // versions. Le nom colore de l'item, lui, va sur le display name.
            book.setTitle("AntWars");
            book.setAuthor("AntWars");
            book.setPages(Arrays.asList(pageText()));
            book.setDisplayName(Messages.tr("lobby.book_name"));
            book.setLore(Arrays.asList(Messages.tr("lobby.book_lore")));
            item.setItemMeta(book);
        }
        return item;
    }

    private static String pageText() {
        return Messages.tr("book.page");
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
