package fr.laugh.antwars.compat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * Manipulation des lits, du 1.8 au 1.21.
 *
 * <p>C'est l'un des rares endroits ou les versions divergent vraiment :
 * <ul>
 *   <li>en 1.8 a 1.12, il existe {@code BED} (l'item) et {@code BED_BLOCK}
 *       (le bloc pose), un seul lit, et l'orientation ainsi que la moitie
 *       tete/pied sont portees par la valeur de data, lue via
 *       {@code org.bukkit.material.Bed} ;</li>
 *   <li>a partir de la 1.13, il existe seize lits colores, item et bloc
 *       portent le meme nom, et la moitie se lit via
 *       {@code org.bukkit.block.data.type.Bed}, une classe qui n'existe pas
 *       du tout en 1.8.</li>
 * </ul>
 *
 * <p>Plutot que de coder les deux API, on n'en utilise aucune : un lit se
 * reconnait a son nom de materiau, et sa seconde moitie se trouve en
 * regardant les quatre blocs voisins. C'est du code identique sur toutes les
 * versions, et il resiste aussi aux lits qu'un jour Mojang renommerait.
 */
public final class Beds {

    /** Les quatre directions horizontales, seules possibles pour un lit. */
    private static final BlockFace[] AROUND = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private Beds() {
    }

    /**
     * Vrai si ce materiau est un lit, pose ou en item, quelle que soit la
     * version : {@code BED} et {@code BED_BLOCK} en 1.8, {@code RED_BED} et
     * consorts a partir de la 1.13.
     */
    public static boolean isBed(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.equals("BED") || name.equals("BED_BLOCK") || name.endsWith("_BED");
    }

    /**
     * Seconde moitie d'un lit, trouvee parmi les blocs voisins.
     *
     * @return l'autre moitie, ou {@code null} si le bloc n'est pas un lit ou
     *         que sa seconde moitie a deja disparu
     */
    public static Block otherHalf(Block block) {
        if (block == null || !isBed(block.getType())) {
            return null;
        }
        for (BlockFace face : AROUND) {
            Block neighbour = block.getRelative(face);
            if (isBed(neighbour.getType())) {
                return neighbour;
            }
        }
        return null;
    }

    /**
     * Cherche un lit sur lequel (ou a cote duquel) se tient un joueur, pour la
     * commande /aw setbed : l'admin pose un vrai lit, se place dessus, puis
     * enregistre sa position.
     *
     * <p>On evite volontairement {@code Player.getTargetBlock} : sa signature a
     * change entre les versions ({@code HashSet<Byte>} en 1.8,
     * {@code Set<Material>} ensuite), ce qui la rend inutilisable dans un jar
     * unique. On se contente de regarder les blocs proches des pieds, ce qui
     * couvre le cas ou le joueur est debout sur le lit comme celui ou il est
     * juste a cote.
     *
     * @return le bloc de lit trouve (une seule de ses deux moities), ou
     *         {@code null} si le joueur n'est pas sur un lit
     */
    public static Block findBedNear(Player player) {
        if (player == null) {
            return null;
        }
        Block feet = player.getLocation().getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        // Ordre de preference : le bloc sous les pieds (cas normal, debout sur
        // le lit), puis les pieds, puis les voisins horizontaux des deux.
        Block[] bases = { below, feet };
        for (Block base : bases) {
            if (isBed(base.getType())) {
                return base;
            }
        }
        for (Block base : bases) {
            for (BlockFace face : AROUND) {
                Block candidate = base.getRelative(face);
                if (isBed(candidate.getType())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Lit de la couleur d'une equipe. Rouge par defaut. */
    public static AWMaterial bedOf(String teamName) {
        if ("Bleu".equalsIgnoreCase(teamName)) {
            return AWMaterial.BLUE_BED;
        }
        if ("Jaune".equalsIgnoreCase(teamName)) {
            return AWMaterial.YELLOW_BED;
        }
        if ("Vert".equalsIgnoreCase(teamName)) {
            return AWMaterial.GREEN_BED;
        }
        return AWMaterial.RED_BED;
    }

    /**
     * Position ou faire reapparaitre un joueur aupres de son lit.
     *
     * <p>On cherche d'abord a cote du lit, et seulement ensuite au-dessus.
     * L'ordre a son importance : AntWars se joue dans des galeries de deux
     * blocs de haut, ou il n'y a jamais la place de tenir debout sur le lit,
     * alors qu'il y a presque toujours un espace libre a cote.
     *
     * <p>Le lit lui-meme n'est jamais renvoye tel quel : reapparaitre dans un
     * bloc solide ferait ejecter le joueur n'importe ou.
     *
     * @return une position sure, ou {@code null} s'il n'y en a aucune
     */
    public static Location safeRespawn(Location bed) {
        if (bed == null || bed.getWorld() == null) {
            return null;
        }
        Block base = bed.getBlock();

        // 1. Les quatre cotes, au niveau du lit : le cas normal d'une galerie.
        for (BlockFace face : AROUND) {
            Block candidate = base.getRelative(face);
            if (fits(candidate)) {
                return center(candidate);
            }
        }

        // 2. A defaut, au-dessus du lit, en remontant un peu.
        for (int offset = 1; offset <= 3; offset++) {
            Block candidate = base.getRelative(BlockFace.UP, offset);
            if (fits(candidate)) {
                return center(candidate);
            }
        }

        // 3. Les diagonales, en dernier ressort.
        for (BlockFace face : AROUND) {
            for (BlockFace second : AROUND) {
                if (face == second || face.getOppositeFace() == second) {
                    continue;
                }
                Block candidate = base.getRelative(face).getRelative(second);
                if (fits(candidate)) {
                    return center(candidate);
                }
            }
        }
        return null;
    }

    /** Vrai si un joueur tient debout ici : deux blocs libres superposes. */
    private static boolean fits(Block feet) {
        return isPassable(feet) && isPassable(feet.getRelative(BlockFace.UP));
    }

    private static Location center(Block block) {
        return block.getLocation().add(0.5d, 0d, 0.5d);
    }

    /**
     * Vrai si un joueur peut tenir dans ce bloc.
     *
     * <p>{@code Block.isPassable()} n'est apparu qu'en 1.13 : on se rabat sur
     * {@code isSolid()}, present depuis toujours et suffisant ici.
     */
    private static boolean isPassable(Block block) {
        Material type = block.getType();
        return type == Material.AIR || !type.isSolid();
    }
}
