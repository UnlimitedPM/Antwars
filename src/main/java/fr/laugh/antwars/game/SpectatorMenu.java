package fr.laugh.antwars.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.lang.Messages;

/**
 * Menu d'un spectateur : une tunique en cuir a la couleur de l'equipe par joueur
 * encore en vie, un clic teleporte le spectateur sur lui. Le menu d'origine de
 * Minecraft n'existe pas en 1.8, d'ou ce menu valable sur toutes les versions.
 */
public class SpectatorMenu {

    private static final int MAX_SLOTS = 54;

    private final Main main;

    public SpectatorMenu(Main main) {
        this.main = main;
    }

    public void open(Player spectator, Arena arena) {
        List<Player> targets = new ArrayList<Player>();
        for (Player player : arena.onlinePlayers()) {
            if (arena.isAlive(player)) {
                targets.add(player);
            }
        }
        int rows = Math.max(1, Math.min(MAX_SLOTS / 9, (targets.size() + 8) / 9));

        SpectatorHolder holder = new SpectatorHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Messages.tr("spectator.menu_title"));
        holder.setInventory(inv);

        for (int i = 0; i < targets.size() && i < rows * 9; i++) {
            Player target = targets.get(i);
            String team = arena.getTeam(target);
            ItemStack icon = AWMaterial.LEATHER_CHESTPLATE.item();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (meta instanceof LeatherArmorMeta && team != null) {
                    ((LeatherArmorMeta) meta).setColor(Arena.teamColorOf(team));
                }
                meta.setDisplayName(Arena.colorOf(team) + target.getName());
                meta.setLore(Arrays.asList(Arena.colorOf(team) + String.valueOf(team),
                        Messages.tr("spectator.menu_lore")));
                icon.setItemMeta(meta);
            }
            holder.bind(i, target.getUniqueId());
            inv.setItem(i, icon);
        }
        spectator.openInventory(inv);
    }

    public void handleClick(Player spectator, SpectatorHolder holder, int slot) {
        Arena arena = main.getArenaManager().getArenaOf(spectator);
        if (arena == null || !arena.isSpectator(spectator)) {
            return;
        }
        UUID id = holder.playerAt(slot);
        if (id == null) {
            return;
        }
        spectator.closeInventory();
        Player target = Bukkit.getPlayer(id);
        if (target == null || !arena.isAlive(target)) {
            spectator.sendMessage(Main.PREFIX + Messages.tr("spectator.player_gone"));
            return;
        }
        spectator.teleport(target.getLocation());
        spectator.sendMessage(Main.PREFIX + Messages.tr("spectator.teleported",
                "color", Arena.colorOf(arena.getTeam(target)), "player", target.getName()));
    }
}
