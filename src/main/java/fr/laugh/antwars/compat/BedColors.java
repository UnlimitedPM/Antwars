package fr.laugh.antwars.compat;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * Recolore un lit d'equipe en gardant son orientation.
 *
 * <p>Le projet compile contre l'API 1.8 et ne peut donc pas appeler
 * {@code BlockState#getBlockData()} ni {@code Block#setBlockData(...)} : ces
 * methodes n'existent qu'a partir de la 1.13. On les appelle par reflexion,
 * uniquement sur les serveurs modernes, et on retombe sur l'instantane
 * d'origine si une version future change l'interface.
 *
 * <p>Pourquoi c'est necessaire : un lit pose dans une map 1.8 est converti en
 * lit ROUGE par le DataFixer, quel que soit le proprietaire. Sans cette
 * couche, toutes les equipes se retrouveraient avec un lit rouge.
 */
public final class BedColors {

    private BedColors() {
    }

    /**
     * Pose {@code snapshot} (les deux moities d'un lit) en changeant seulement
     * la couleur du materiau : la direction et la moitie tete/pied sont
     * recopiees depuis l'instantane.
     */
    public static void apply(Block block, BlockState snapshot, AWMaterial bedMaterial) {
        if (block == null || snapshot == null || bedMaterial == null) {
            if (snapshot != null) {
                snapshot.update(true, false);
            }
            return;
        }
        if (MCVersion.isLegacy()) {
            // 1.8 - 1.12 : un seul lit, la couleur n'existe pas dans l'API.
            snapshot.update(true, false);
            return;
        }
        try {
            Object oldData = BlockState.class.getMethod("getBlockData").invoke(snapshot);
            Material material = bedMaterial.material();
            Object newData = Material.class.getMethod("createBlockData").invoke(material);

            Class<?> bedClass = Class.forName("org.bukkit.block.data.type.Bed");
            if (bedClass.isInstance(oldData) && bedClass.isInstance(newData)) {
                Class<?> facingClass = Class.forName("org.bukkit.block.BlockFace");
                Object facing = bedClass.getMethod("getFacing").invoke(oldData);
                bedClass.getMethod("setFacing", facingClass).invoke(newData, facing);

                Object part = bedClass.getMethod("getPart").invoke(oldData);
                bedClass.getMethod("setPart", bedClass.getMethod("getPart").getReturnType())
                        .invoke(newData, part);

                Object occupied = bedClass.getMethod("isOccupied").invoke(oldData);
                bedClass.getMethod("setOccupied", boolean.class).invoke(newData, occupied);
            }

            // Physique desactivee : les deux moities sont posees l'une apres
            // l'autre, et une moitie seule ne doit pas sauter entre les deux.
            block.setType(material, false);
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Block.class.getMethod("setBlockData", blockDataClass, boolean.class)
                    .invoke(block, newData, false);
        } catch (Throwable ignored) {
            // Version inattendue : on preserve au moins le lit d'origine.
            snapshot.update(true, false);
        }
    }
}
