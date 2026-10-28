package fr.laugh.antwars.bed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.compat.BedColors;
import fr.laugh.antwars.compat.Beds;
import fr.laugh.antwars.lang.Messages;

/**
 * Les lits d'equipe d'une arene : pose, liaison des joueurs et destruction.
 *
 * <p>Regles du jeu implementees ici :
 * <ul>
 *   <li>au lancement, le lit de chaque equipe est pose (ou restaure) a
 *       l'emplacement configure d'avance via /aw setbed ; un seul lit par
 *       equipe ;</li>
 *   <li>chaque coequipier doit cliquer sur le lit pour s'y lier, faute de quoi
 *       sa mort est definitive ;</li>
 *   <li>un allie peut reprendre le lit a la main pour le demenager, ce qui
 *       delie toute l'equipe ; si le porteur meurt ou se deconnecte avant de
 *       l'avoir repose, le lit repart a un autre coequipier ;</li>
 *   <li>un adversaire ne peut le detruire qu'a la TNT.</li>
 * </ul>
 *
 * <p>Restauration du lit sans API posterieure a la 1.8 : plutot que de
 * reconstruire l'orientation d'un lit par code (ce qui exigerait
 * {@code org.bukkit.block.data.type.Bed} en 1.13+, interdit ici), on capture un
 * instantane {@link BlockState} du vrai lit pose par l'admin et on le reapplique
 * tel quel avec {@link BlockState#update(boolean, boolean)}, presente depuis la
 * 1.8. L'instantane est en memoire (perdu au redemarrage) : s'il manque mais que
 * le vrai lit est encore la, on le recapture ; s'il manque et que le lit a
 * disparu, on retombe sur une pose sommaire, fonctionnelle mais sans orientation.
 */
public class BedManager {

    /** Etat du lit d'une equipe. */
    private static final class TeamBed {
        private Location foot;
        private Location head;
        private boolean destroyed;

        private boolean isPlaced() {
            return foot != null && !destroyed;
        }
    }

    private final Arena arena;
    private final Map<String, TeamBed> beds = new LinkedHashMap<String, TeamBed>();
    private final Set<UUID> bound = new HashSet<UUID>();
    private final Random random = new Random();

    /**
     * Tous les emplacements de lit occupes depuis le debut de la partie,
     * y compris ceux des lits demenages ou detruits : ils servent au menage de
     * fin de partie, pour ne pas laisser de lit sans proprietaire dans le
     * decor de la prochaine manche.
     */
    private final List<Location> history = new ArrayList<Location>();

    /**
     * Instantane des deux moities du lit de chaque equipe, capture quand un vrai
     * lit correctement oriente existe (a la commande /aw setbed, ou au lancement
     * si le lit est encore la). Sert a le reposer a l'identique au debut de
     * chaque manche. En memoire seulement : volontairement absent de
     * {@link #clear()} pour survivre d'une partie a l'autre.
     */
    private final Map<String, BlockState[]> homeSnapshots = new LinkedHashMap<String, BlockState[]>();

    public BedManager(Arena arena) {
        this.arena = arena;
    }

    // ================= Cycle de vie =================

    /**
     * Prepare et pose les lits au lancement de la partie, chacun a
     * l'emplacement configure pour son equipe (/aw setbed).
     */
    public void start(List<String> teams) {
        clear();
        for (String team : teams) {
            TeamBed bed = new TeamBed();
            beds.put(team, bed);
            placeHome(team, bed);
        }
        // Securite : le lit est pose sur la map, jamais distribue. Aucun joueur
        // ne doit donc commencer une partie avec un lit en main : on retire ce
        // qui trainerait (ramassage d'un lit tombe au sol, etc.).
        for (Player player : arena.onlinePlayers()) {
            stripBedItems(player);
        }
    }

    public void stripBedItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && Beds.isBed(item.getType())) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public void clear() {
        beds.clear();
        bound.clear();
        history.clear();
    }

    /**
     * Fin de manche. Le lit fait partie du decor : il doit rester a son
     * emplacement configure entre deux parties. On retire donc uniquement les
     * lits EGARES (ceux qu'une equipe a deplaces ailleurs pendant la partie),
     * puis on remet a domicile tout lit qui n'y serait plus (deplace ou detruit
     * a la TNT). Bonus : le lit ainsi repose est sauvegarde avec le monde, ce
     * qui evite la pose sommaire apres un redemarrage.
     *
     * <p>Sans ce menage des lits egares, un lit deplace resterait au sol a la
     * manche suivante sans proprietaire, et le premier a le casser repartirait
     * avec un lit en main.
     */
    public void endRound() {
        // 1. Retirer les lits egares, en epargnant les emplacements a domicile.
        //    Physique desactivee : retirer une moitie ferait tomber l'autre en
        //    item ramassable.
        for (Location location : history) {
            if (location == null || location.getWorld() == null || isHomePosition(location)) {
                continue;
            }
            Block block = location.getBlock();
            if (Beds.isBed(block.getType())) {
                block.setType(org.bukkit.Material.AIR, false);
            }
        }
        history.clear();

        // 2. Remettre a domicile tout lit qui n'y est plus.
        for (String team : arena.getBedFeet().keySet()) {
            Location foot = arena.getBedFoot(team);
            if (foot != null && foot.getWorld() != null
                    && !Beds.isBed(foot.getBlock().getType())) {
                placeHomeBlocks(team);
            }
        }
    }

    /** Vrai si cette position est l'une des moities d'un lit a domicile. */
    private boolean isHomePosition(Location location) {
        for (Location foot : arena.getBedFeet().values()) {
            if (sameBlock(foot, location)) {
                return true;
            }
        }
        for (Location head : arena.getBedHeads().values()) {
            if (sameBlock(head, location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Memorise l'aspect exact du lit d'une equipe a partir de ses deux blocs
     * reels, pour pouvoir le reposer a l'identique (type + orientation) sans
     * jamais toucher l'API des donnees de bloc. Appele par /aw setbed.
     */
    public void captureHome(String team, Block foot, Block head) {
        if (team == null || foot == null || head == null) {
            return;
        }
        homeSnapshots.put(team, new BlockState[] { foot.getState(), head.getState() });
    }

    /**
     * Pose (ou restaure) PHYSIQUEMENT le lit d'une equipe a son emplacement
     * configure : instantane a l'identique si on en a un, sinon pose sommaire.
     * Ne touche pas a l'etat de jeu (TeamBed), ce qui permet de s'en servir
     * aussi bien au lancement (via {@link #placeHome}) qu'a la remise en place
     * en fin de manche (via {@link #endRound}).
     */
    private void placeHomeBlocks(String team) {
        Location foot = arena.getBedFoot(team);
        Location head = arena.getBedHead(team);
        if (foot == null || head == null
                || foot.getWorld() == null || head.getWorld() == null) {
            // Ne devrait pas arriver : validate() impose un lit par equipe.
            return;
        }
        Block footBlock = foot.getBlock();
        Block headBlock = head.getBlock();

        // Pas encore d'instantane mais le vrai lit est toujours la (typiquement
        // apres un redemarrage) : on le capture pour pouvoir le restaurer.
        if (!homeSnapshots.containsKey(team)
                && Beds.isBed(footBlock.getType()) && Beds.isBed(headBlock.getType())) {
            captureHome(team, footBlock, headBlock);
        }

        BlockState[] snapshot = homeSnapshots.get(team);
        if (snapshot != null) {
            // Restauration de l'orientation, avec la couleur de l'equipe. Sur
            // une map convertie depuis la 1.8, le DataFixer a transforme tous
            // les lits en lits rouges : sans cela, les quatre equipes
            // partageraient le meme lit. La physique reste desactivee (voir
            // BedColors) pour ne pas casser une moitie pendant la pose.
            AWMaterial bedMaterial = Beds.bedOf(team);
            BedColors.apply(footBlock, snapshot[0], bedMaterial);
            BedColors.apply(headBlock, snapshot[1], bedMaterial);
        } else {
            // Repli : le bon materiau aux bons blocs, mais sans orientation. Le
            // lit rend mal en 1.13+ (deux pieds accoles) tout en restant
            // parfaitement fonctionnel. N'arrive qu'apres un redemarrage ayant
            // efface a la fois l'instantane et le lit du monde.
            //
            // Physique desactivee (setType ..., false) : une moitie de lit posee
            // seule est invalide, et avec la physique le serveur la casserait
            // aussitot en faisant TOMBER un lit au sol, qu'un joueur pourrait
            // ramasser. C'est la cause classique d'un "lit en main au lancement".
            footBlock.setType(Beds.bedOf(team).material(), false);
            headBlock.setType(Beds.bedOf(team).material(), false);
        }
    }

    /**
     * Pose le lit d'une equipe au lancement et l'enregistre comme lit pose de
     * cette equipe.
     */
    private void placeHome(String team, TeamBed bed) {
        placeHomeBlocks(team);
        Location foot = arena.getBedFoot(team);
        Location head = arena.getBedHead(team);
        if (foot == null || head == null) {
            return;
        }
        bed.foot = foot;
        bed.head = head;
        history.add(foot);
        history.add(head);
    }

    // ================= Porteur du lit (apres reprise a la main) =================

    /** Choisit un porteur au hasard dans une equipe, en evitant un joueur. */
    private Player pickCarrier(String team, Player exclude) {
        List<Player> candidates = new ArrayList<Player>();
        for (Player player : arena.onlinePlayers()) {
            if (team.equals(arena.getTeam(player)) && arena.isAlive(player)
                    && (exclude == null || !player.getUniqueId().equals(exclude.getUniqueId()))) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private void giveBed(Player carrier, String team) {
        ItemStack bed = Beds.bedOf(team).item();
        carrier.getInventory().addItem(bed);
        carrier.sendMessage(Main.PREFIX + Messages.tr("bed.carrying"));
        // Reserve a l'equipe : l'adversaire saurait sinon exactement qui
        // abattre en priorite pour empecher la pose.
        arena.broadcastTeam(team, Messages.tr("bed.entrusted", "player", carrier.getName()), carrier);
    }

    /**
     * Rend le lit a un coequipier quand son porteur meurt ou se deconnecte
     * avant de l'avoir pose. Sans cela, une equipe entiere serait condamnee
     * par une simple chute.
     *
     * @return vrai si un lit a effectivement ete redistribue
     */
    public boolean handleCarrierLost(Player lost) {
        String team = arena.getTeam(lost);
        if (team == null) {
            return false;
        }
        TeamBed bed = beds.get(team);
        if (bed == null || bed.isPlaced() || bed.destroyed) {
            return false;
        }
        if (!carries(lost)) {
            return false;
        }
        Player replacement = pickCarrier(team, lost);
        if (replacement == null) {
            return false;
        }
        giveBed(replacement, team);
        return true;
    }

    /**
     * Vrai si ce joueur a un lit dans son inventaire.
     *
     * <p>On ne verifie pas la couleur : sur 1.8 a 1.12 les quatre equipes
     * partagent le meme materiau, et de toute facon un joueur ne peut jamais
     * detenir le lit d'une autre equipe, celui-ci n'etant recuperable que par
     * les siens.
     */
    public boolean carries(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && Beds.isBed(item.getType())) {
                return true;
            }
        }
        return false;
    }

    // ================= Pose et reprise =================

    /**
     * Enregistre la pose d'un lit.
     *
     * @return la raison du refus, ou {@code null} si la pose est acceptee
     */
    public String place(Player player, Block block) {
        String team = arena.getTeam(player);
        if (team == null) {
            return Messages.tr("bed.place.no_team");
        }
        TeamBed bed = beds.get(team);
        if (bed == null) {
            return Messages.tr("bed.place.no_bed");
        }
        if (bed.destroyed) {
            return Messages.tr("bed.place.destroyed");
        }
        if (bed.isPlaced()) {
            return Messages.tr("bed.place.already_placed");
        }
        bed.foot = block.getLocation();
        bed.head = null;
        history.add(bed.foot);
        return null;
    }

    /**
     * Complete l'enregistrement avec la seconde moitie du lit, une fois que
     * le serveur a fini de poser les deux blocs.
     */
    public void completePlacement(String team, Block placed) {
        TeamBed bed = beds.get(team);
        if (bed == null || bed.foot == null) {
            return;
        }
        // La pose a pu etre refusee entre-temps par un autre plugin : on ne
        // valide l'enregistrement que si le lit est reellement la.
        if (!Beds.isBed(placed.getType())) {
            bed.foot = null;
            bed.head = null;
            return;
        }

        Block other = Beds.otherHalf(placed);
        // Deux lits colles l'un a l'autre : le voisin trouve pourrait etre la
        // moitie du lit d'une autre equipe. On ne l'adopte que s'il n'est
        // revendique par personne.
        if (other != null && teamAt(other) == null) {
            bed.head = other.getLocation();
            history.add(bed.head);
        }

        // Reserve a l'equipe : savoir qu'un lit vient d'etre pose indiquerait
        // a l'adversaire qu'il est temps de partir le chercher.
        arena.broadcastTeam(team, Messages.tr("bed.placed"), null);

        // Le lit a ete deplace : on relie automatiquement toute l'equipe pour
        // ne pas la punir d'un clic droit oublie. C'est le meme comportement
        // qu'au lancement de la partie (voir Arena#start).
        bindAll(team);
    }

    /** Equipe dont le lit occupe ce bloc, ou {@code null}. */
    public String teamAt(Block block) {
        if (block == null) {
            return null;
        }
        Location location = block.getLocation();
        for (Map.Entry<String, TeamBed> entry : beds.entrySet()) {
            TeamBed bed = entry.getValue();
            if (bed.destroyed) {
                continue;
            }
            if (sameBlock(bed.foot, location) || sameBlock(bed.head, location)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Le lit est repris par un allie pour etre demenage : l'equipe redevient
     * mortelle jusqu'a ce que chacun ait dormi dans le nouveau lit.
     *
     * @param picker le joueur qui reprend le lit ; il est exclu du message
     *               d'equipe car il en recoit deja un, plus precis, de son cote
     *               (evite le doublon)
     */
    public void pickUp(String team, Player picker) {
        TeamBed bed = beds.get(team);
        if (bed == null) {
            return;
        }
        bed.foot = null;
        bed.head = null;
        unbindTeam(team);
        // Le demenagement reste secret : c'est precisement le moment ou
        // l'equipe est vulnerable, l'adversaire n'a pas a le savoir.
        arena.broadcastTeam(team, Messages.tr("bed.taken_back"), picker);
    }

    /** Le lit est detruit par une TNT adverse : c'est definitif. */
    public void destroy(String team, String cause) {
        TeamBed bed = beds.get(team);
        if (bed == null || bed.destroyed) {
            return;
        }
        bed.destroyed = true;
        bed.foot = null;
        bed.head = null;
        unbindTeam(team);
        arena.broadcast(cause == null
                ? Messages.tr("bed.destroyed_broadcast", "color", Arena.colorOf(team), "team", team)
                : Messages.tr("bed.destroyed_broadcast_by",
                        "color", Arena.colorOf(team), "team", team, "cause", cause));
        // Plus de lit : les membres de l'equipe encore deconnectes ne peuvent
        // plus revenir.
        arena.forfeitDisconnected(team);
    }

    /** Blocs encore occupes par le lit d'une equipe, pour les nettoyer. */
    public List<Location> blocksOf(String team) {
        List<Location> locations = new ArrayList<Location>();
        TeamBed bed = beds.get(team);
        if (bed != null) {
            if (bed.foot != null) {
                locations.add(bed.foot);
            }
            if (bed.head != null) {
                locations.add(bed.head);
            }
        }
        return locations;
    }

    // ================= Liaison des joueurs =================

    /**
     * Lie un joueur au lit de son equipe.
     *
     * @return la raison du refus, ou {@code null} si la liaison est faite
     */
    public String bind(Player player, String bedTeam) {
        String team = arena.getTeam(player);
        if (team == null || !team.equals(bedTeam)) {
            return Messages.tr("bed.bind.wrong_team");
        }
        if (!isUsable(team)) {
            return Messages.tr("bed.bind.no_bed");
        }
        if (!bound.add(player.getUniqueId())) {
            return Messages.tr("bed.bind.already_bound");
        }
        return null;
    }

    public boolean isBound(Player player) {
        return bound.contains(player.getUniqueId());
    }

    /**
     * Relie d'office tous les membres connectes d'une equipe a son lit.
     *
     * <p>AntWars v1 imposait un clic droit sur le lit ; un joueur qui oubliait
     * ce geste perdait definitivement a sa premiere mort. On considere
     * desormais que le lit pose est le lit de l'equipe : la liaison est
     * automatique au lancement et apres chaque demenagement.
     */
    public void bindAll(String team) {
        if (!isUsable(team)) {
            return;
        }
        for (Player player : arena.onlinePlayers()) {
            if (team.equals(arena.getTeam(player)) && arena.isAlive(player)) {
                bound.add(player.getUniqueId());
            }
        }
    }

    /**
     * Delie toute une equipe de son lit.
     *
     * <p>Les joueurs deconnectes sont retires au passage : ils ont de toute
     * facon quitte l'arene, leur identifiant ne serait qu'une trace morte.
     */
    private void unbindTeam(String team) {
        List<UUID> toRemove = new ArrayList<UUID>();
        for (UUID id : bound) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null || team.equals(arena.getTeam(player))) {
                toRemove.add(id);
            }
        }
        bound.removeAll(toRemove);
    }

    /** Vrai si l'equipe dispose d'un lit pose et intact. */
    public boolean isUsable(String team) {
        TeamBed bed = beds.get(team);
        return bed != null && bed.isPlaced();
    }

    /** Vrai si le lit de cette equipe a ete detruit (definitif). Pour le scoreboard. */
    public boolean isDestroyed(String team) {
        TeamBed bed = beds.get(team);
        return bed != null && bed.destroyed;
    }

    /**
     * Vrai si ce joueur peut reapparaitre : il faut a la fois que son equipe
     * ait un lit intact et qu'il s'y soit lie.
     */
    public boolean canRespawn(Player player) {
        String team = arena.getTeam(player);
        return team != null && isUsable(team) && isBound(player);
    }

    /** Position de reapparition, juste au-dessus du lit. */
    public Location respawnLocation(Player player) {
        String team = arena.getTeam(player);
        if (team == null) {
            return null;
        }
        TeamBed bed = beds.get(team);
        if (bed == null || !bed.isPlaced()) {
            return null;
        }
        Location reference = bed.head != null ? bed.head : bed.foot;
        Location safe = Beds.safeRespawn(reference);
        return safe != null ? safe : arena.getSpawnPoints().get(team);
    }
}
