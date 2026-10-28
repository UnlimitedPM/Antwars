package fr.laugh.antwars.listeners;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.lang.Messages;
import fr.laugh.antwars.quest.QuestType;

/**
 * Tout ce qui touche aux lits d'equipe : pose, reprise, liaison, et
 * destruction a la TNT.
 *
 * <p>Ces evenements sont traites en priorite haute, apres
 * {@link BlocksListeners} : si celui-ci a deja refuse l'action parce que le
 * joueur ne participe a aucune partie, on ne fait rien de plus.
 */
public class BedListeners implements Listener {

    /**
     * Qui a pose quelle TNT, pour savoir a quelle equipe imputer une
     * explosion et epargner son propre lit. L'entree est retiree des que la
     * TNT explose ; {@link #forgetTnt()} nettoie le reste en fin de partie.
     */
    private final Map<String, UUID> tntOwners = new HashMap<String, UUID>();

    private final Main main;

    public BedListeners(Main main) {
        this.main = main;
    }

    // ================= Pose =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isRunning()) {
            return;
        }

        // Traçage des TNT : c'est ce qui permettra plus tard de savoir quelle
        // equipe est a l'origine de l'explosion.
        if (AWMaterial.TNT.is(block.getType())) {
            tntOwners.put(key(block.getLocation()), player.getUniqueId());
            return;
        }

        if (!Beds.isBed(block.getType())) {
            return;
        }

        String problem = arena.getBeds().place(player, block);
        if (problem != null) {
            event.setCancelled(true);
            player.sendMessage(Main.PREFIX + Messages.tr("bed.place_failed", "reason", problem));
            return;
        }

        final String team = arena.getTeam(player);
        final Arena target = arena;
        // La seconde moitie du lit n'est pas forcement en place au moment ou
        // l'evenement est declenche : on la cherche au tick suivant.
        new BukkitRunnable() {
            @Override
            public void run() {
                target.getBeds().completePlacement(team, block);
            }
        }.runTaskLater(main, 1L);
    }

    // ================= Reprise a la main =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Beds.isBed(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isRunning()) {
            return;
        }

        String bedTeam = arena.getBeds().teamAt(block);
        if (bedTeam == null) {
            // Un lit qui ne compte pour aucune equipe : simple decor.
            return;
        }

        String playerTeam = arena.getTeam(player);
        if (!bedTeam.equals(playerTeam)) {
            event.setCancelled(true);
            player.sendMessage(Main.PREFIX + Messages.tr("bed.cannot_break_enemy"));
            return;
        }

        // Un allie reprend le lit pour le demenager.
        //
        // On annule l'evenement et on retire les blocs a la main : sans cela
        // Minecraft ferait tomber un lit au sol en plus de celui que l'on rend
        // au joueur, qui se retrouverait avec deux lits posables.
        event.setCancelled(true);
        disableVanillaDrops(event);
        clearBed(arena, bedTeam);
        arena.getBeds().pickUp(bedTeam, player);
        player.getInventory().addItem(Beds.bedOf(bedTeam).item());
        player.sendMessage(Main.PREFIX + Messages.tr("bed.picked_up_self"));
    }

    // ================= Liaison =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!Beds.isBed(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isRunning()) {
            return;
        }

        // On coupe systematiquement le comportement d'origine : Minecraft
        // refuserait le sommeil en pleine journee, or la partie se joue sous
        // terre, et en 1.16+ un lit explose carrement hors de l'Overworld.
        event.setCancelled(true);

        String bedTeam = arena.getBeds().teamAt(block);
        if (bedTeam == null) {
            return;
        }

        String problem = arena.getBeds().bind(player, bedTeam);
        if (problem != null) {
            player.sendMessage(Main.PREFIX + Messages.tr("bed.bind_failed", "reason", problem));
            return;
        }
        player.sendMessage(Main.PREFIX + Messages.tr("bed.bound"));
    }

    // ================= Explosions =================

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        UUID ownerId = tntOwners.remove(key(event.getLocation()));
        Player owner = resolveOwner(ownerId, event.getEntity());

        Arena arena = owner != null ? main.getArenaManager().getArenaOf(owner) : null;
        if (arena == null) {
            arena = findArenaByBeds(event.blockList());
        }
        if (arena == null || !arena.isRunning()) {
            // Explosion hors partie : le plugin n'a pas a s'en meler.
            return;
        }

        String ownerTeam = owner != null ? arena.getTeam(owner) : null;
        Set<String> destroyed = new LinkedHashSet<String>();

        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            String bedTeam = arena.getBeds().teamAt(block);

            if (bedTeam != null) {
                if (!bedTeam.equals(ownerTeam)) {
                    destroyed.add(bedTeam);
                }
                // Dans les deux cas le bloc sort de la liste : soit le lit est
                // celui de l'equipe qui a pose la TNT et il est epargne, soit
                // il est detruit un peu plus bas, sans faire tomber d'item
                // qu'un adversaire pourrait ramasser.
                iterator.remove();
                continue;
            }

            // Le reste du decor est preserve : seuls les blocs du jeu sautent.
            if (!BlocksListeners.isGameBlock(block.getType())) {
                iterator.remove();
                continue;
            }

            // Bloc de jeu qui va sauter : on memorise son etat d'origine pour la
            // regeneration de la map (ici, l'arene est deja connue, contrairement
            // a MapListeners qui n'attribue les explosions a aucun joueur).
            if (main.isMapRegenerationEnabled()) {
                arena.getMapTracker().record(block);
            }
        }

        String cause = owner != null ? owner.getName() : Messages.tr("bed.cause_explosion");
        for (String team : destroyed) {
            clearBed(arena, team);
            arena.getBeds().destroy(team, cause);
            if (owner != null) {
                main.getQuestManager().addProgress(owner, QuestType.DESTROY_BEDS, 1);
                arena.recordBedDestroyed(owner);
            }
        }
    }

    private Player resolveOwner(UUID ownerId, Entity exploding) {
        if (ownerId != null) {
            Player player = Bukkit.getPlayer(ownerId);
            if (player != null) {
                return player;
            }
        }
        // Solution de repli : l'allumeur de la TNT, quand le serveur le connait.
        if (exploding instanceof TNTPrimed) {
            Entity source = ((TNTPrimed) exploding).getSource();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        return null;
    }

    /** Cherche l'arene concernee quand l'auteur de l'explosion est inconnu. */
    private Arena findArenaByBeds(List<Block> blocks) {
        for (Arena arena : main.getArenaManager().getArenas()) {
            if (!arena.isRunning()) {
                continue;
            }
            for (Block block : blocks) {
                if (arena.getBeds().teamAt(block) != null) {
                    return arena;
                }
            }
        }
        return null;
    }

    /**
     * Desactive les drops vanilla quand l'API le permet (1.12+). En 1.8, le
     * simple {@code setCancelled(true)} suffit deja ; la methode est appelee par
     * reflexion pour ne pas casser la compilation contre l'API 1.8.8.
     */
    private void disableVanillaDrops(BlockBreakEvent event) {
        try {
            BlockBreakEvent.class.getMethod("setDropItems", boolean.class)
                    .invoke(event, Boolean.FALSE);
        } catch (Throwable ignored) {
            // 1.8-1.11 : pas de setDropItems, l'annulation de l'evenement suffit.
        }
    }

    private void clearBed(Arena arena, String team) {
        for (Location location : arena.getBeds().blocksOf(team)) {
            Block block = location.getBlock();
            if (Beds.isBed(block.getType())) {
                // Physique desactivee : sinon la premiere moitie retiree casse
                // la seconde par mise a jour de voisin, qui tombe alors en item
                // en plus de celui rendu au joueur (duplication du lit).
                block.setType(Material.AIR, false);
            }
        }
    }

    // ================= Divers =================

    /** Le lit ne doit pas pouvoir etre jete au sol ni echange. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!Beds.isBed(event.getItemDrop().getItemStack().getType())) {
            return;
        }
        Arena arena = main.getArenaManager().getArenaOf(event.getPlayer());
        if (arena != null && arena.isRunning()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Main.PREFIX + Messages.tr("bed.cannot_drop"));
        }
    }

    /** Vide le suivi des TNT, appele a la remise a zero d'une arene. */
    public void forgetTnt() {
        tntOwners.clear();
    }

    private String key(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }
}
