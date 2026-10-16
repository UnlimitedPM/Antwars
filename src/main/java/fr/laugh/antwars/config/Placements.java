package fr.laugh.antwars.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.lang.Messages;

/**
 * Gestion du "mode pose" : quand l'admin clique un bouton du menu qui demande
 * une position (lobby, spawn, lit, NPC), le menu se ferme et on retient ce qu'il
 * place. Le prochain clic droit dans le monde capture sa position.
 *
 * <p>Pourquoi ce detour plutot que tout regler dans le menu : un inventaire ne
 * peut pas faire "va te placer quelque part". On sort donc du menu, l'admin se
 * deplace, et son clic droit sert de validation. Les spawns et les lits
 * s'enchainent equipe par equipe sans rouvrir le menu a chaque fois.
 */
public class Placements {

    /**
     * Anti double-declenchement : depuis la 1.9 un clic droit leve
     * PlayerInteractEvent deux fois (main principale + seconde main). On ne peut
     * pas filtrer par {@code getHand()} (API post-1.8, interdite), donc on ignore
     * deux clics rapproches du meme joueur.
     */
    private static final long DEBOUNCE_MS = 250L;

    private final Main main;
    private final Map<UUID, PlacementSession> sessions = new HashMap<UUID, PlacementSession>();
    private final Map<UUID, Long> lastClick = new HashMap<UUID, Long>();

    public Placements(Main main) {
        this.main = main;
    }

    public boolean isPlacing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start(Player player, PlacementSession session) {
        sessions.put(player.getUniqueId(), session);
        lastClick.remove(player.getUniqueId());
        player.closeInventory();
        prompt(player, session);
    }

    public void cancel(Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.cancelled"));
        }
    }

    /** Retire une session sans message (deconnexion). */
    public void clear(Player player) {
        sessions.remove(player.getUniqueId());
        lastClick.remove(player.getUniqueId());
    }

    private void prompt(Player player, PlacementSession session) {
        switch (session.getKind()) {
            case LOBBY:
                player.sendMessage(Main.PREFIX + Messages.tr("config.place.lobby"));
                break;
            case SPAWN:
                player.sendMessage(Main.PREFIX
                        + Messages.tr("config.place.spawn", "team", session.currentTeam()));
                break;
            case BED:
                player.sendMessage(Main.PREFIX
                        + Messages.tr("config.place.bed", "team", session.currentTeam()));
                break;
            case NPC:
                player.sendMessage(Main.PREFIX + Messages.tr("config.place.npc"));
                break;
            default:
                break;
        }
    }

    /**
     * Traite le clic droit d'un joueur en session de pose. Renvoie {@code true}
     * si le clic a ete consomme (le listener doit alors annuler l'evenement).
     */
    public boolean handleInteract(Player player) {
        PlacementSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        // Anti double-declenchement (voir DEBOUNCE_MS).
        long now = System.currentTimeMillis();
        Long previous = lastClick.get(player.getUniqueId());
        if (previous != null && now - previous.longValue() < DEBOUNCE_MS) {
            return true;
        }
        lastClick.put(player.getUniqueId(), Long.valueOf(now));

        Arena arena = main.getArenaManager().getArena(session.getArena());
        if (arena == null) {
            sessions.remove(player.getUniqueId());
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.arena_gone"));
            return true;
        }

        switch (session.getKind()) {
            case LOBBY:
                arena.setLobby(player.getLocation());
                finish(player, arena, Messages.tr("config.place.lobby_done"));
                break;
            case NPC:
                arena.addShopNpc(player.getLocation());
                if (arena.isRunning()) {
                    arena.spawnShopNpcs();
                }
                finish(player, arena, Messages.tr("config.place.npc_done"));
                break;
            case SPAWN:
                captureSpawn(player, session, arena);
                break;
            case BED:
                captureBed(player, session, arena);
                break;
            default:
                break;
        }
        return true;
    }

    private void captureSpawn(Player player, PlacementSession session, Arena arena) {
        String team = session.currentTeam();
        arena.setSpawnPoint(team, player.getLocation());
        player.sendMessage(Main.PREFIX + Messages.tr("config.place.spawn_done", "team", team));
        if (session.advance()) {
            player.sendMessage(Main.PREFIX
                    + Messages.tr("config.place.spawn", "team", session.currentTeam()));
        } else {
            finish(player, arena, Messages.tr("config.place.all_spawns_done"));
        }
    }

    private void captureBed(Player player, PlacementSession session, Arena arena) {
        String team = session.currentTeam();
        // Meme capture que /aw setbed : on lit un vrai lit sous les pieds pour
        // recuperer son orientation exacte (irreconstructible par code en 1.13+).
        Block foot = Beds.findBedNear(player);
        if (foot == null) {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.no_bed"));
            return; // on ne fait pas avancer la file : l'admin peut reessayer.
        }
        Block head = Beds.otherHalf(foot);
        if (head == null) {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.bed_incomplete"));
            return;
        }
        arena.setBed(team, foot.getLocation(), head.getLocation());
        arena.getBeds().captureHome(team, foot, head);
        player.sendMessage(Main.PREFIX + Messages.tr("config.place.bed_done", "team", team));
        if (session.advance()) {
            player.sendMessage(Main.PREFIX
                    + Messages.tr("config.place.bed", "team", session.currentTeam()));
        } else {
            finish(player, arena, Messages.tr("config.place.all_beds_done"));
        }
    }

    /** Termine une session : sauve, informe, et rouvre le menu d'edition. */
    private void finish(Player player, Arena arena, String message) {
        sessions.remove(player.getUniqueId());
        main.getArenaManager().save();
        player.sendMessage(Main.PREFIX + message);
        main.getConfigMenu().openEdit(player, arena.getName());
    }
}
