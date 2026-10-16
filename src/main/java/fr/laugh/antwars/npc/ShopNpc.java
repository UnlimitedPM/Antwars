package fr.laugh.antwars.npc;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import fr.laugh.antwars.compat.AWMaterial;

/**
 * Le NPC de boutique : un armor stand habille et coiffe d'une tete, qu'on
 * clique pour ouvrir la boutique (alternative a la pancarte [Boutique]).
 *
 * <p>Pourquoi un armor stand et pas un vrai PNJ joueur : un PNJ « joueur avec
 * skin qui marche » impose des paquets NMS differents a chaque version, ce que
 * la contrainte 1.8->1.21 du projet interdit. L'armor stand, lui, utilise une
 * API stable depuis la 1.8, ne bouge jamais (pas d'IA a desactiver, absente en
 * 1.8) et se laisse habiller/skinner sans NMS. Il ne se deplace pas : c'est une
 * statue, mais elle fait le travail d'un comptoir de boutique.
 *
 * <p>Les NPC sont volontairement ephemeres : poses au lancement de la partie,
 * retires a la fin. Ils ne survivent donc pas d'une manche a l'autre, ce qui
 * evite les doublons apres un redemarrage. On les reconnait a leur nom
 * sentinelle {@link #LABEL}, ce qui permet aussi de nettoyer un eventuel orphelin
 * laisse par un crash : {@link #clearAt} le fait au prochain spawn.
 */
public final class ShopNpc {

    /** Nom affiche ET sentinelle de reconnaissance (voir {@link #isNpc}). */
    public static final String LABEL = "Boutique";

    private static final String DISPLAY = ChatColor.GOLD + "" + ChatColor.BOLD + LABEL;

    /** Vrai si les NPC doivent etre de vrais NPC Citizens plutot que des armor stands. */
    private static boolean citizens;
    /** Pseudo dont on veut la peau, tel qu'ecrit dans shop-npc.skin. */
    private static String skin = "";

    private ShopNpc() {
    }

    /**
     * Choisit le type de NPC selon la config et la presence de Citizens. Appelee
     * au demarrage et a chaque /aw reload.
     *
     * <p>{@code shop-npc.type} : {@code auto} (Citizens s'il est la, sinon armor
     * stand), {@code citizens} (idem, mais on previent si Citizens manque) ou
     * {@code armorstand} (jamais de Citizens).
     */
    public static void setup(fr.laugh.antwars.Main main) {
        // Les NPC deja poses appartiennent a l'ancien mode : on fait le menage
        // avant de changer, sinon ils resteraient orphelins dans le decor.
        CitizensNpc.removeAll();

        String type = main.getConfig().getString("shop-npc.type", "auto").toLowerCase();
        skin = main.getConfig().getString("shop-npc.skin", "");

        if ("armorstand".equals(type)) {
            citizens = false;
            return;
        }
        citizens = CitizensNpc.init(main.getLogger());
        if (!citizens && "citizens".equals(type)) {
            main.getLogger().warning("shop-npc.type = citizens mais le plugin Citizens est absent :"
                    + " le NPC de boutique sera un armor stand.");
        }
        // Citizens ne sait poser une peau qu'a partir d'un PSEUDO : une texture
        // base64 (acceptee par l'armor stand via SkullUtils) n'a pas d'equivalent
        // sans la signature qui l'accompagne. On le dit plutot que de laisser
        // croire a un bug.
        if (citizens && skin != null && skin.length() > 20) {
            main.getLogger().warning("shop-npc.skin ressemble a une texture base64 :"
                    + " Citizens n'accepte qu'un pseudo, le NPC gardera la peau par defaut.");
        }
    }

    public static boolean usesCitizens() {
        return citizens;
    }

    public static void removeAllCitizens() {
        CitizensNpc.removeAll();
    }

    /**
     * Pose un NPC de boutique a cette position (au centre du bloc, pieds au sol).
     *
     * @param head tete a lui coiffer, ou {@code null} pour aucune
     * @return l'armor stand cree, ou {@code null} si la position est invalide
     */
    public static ArmorStand spawn(Location location, ItemStack head) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        // Anti-doublon : on retire d'abord TOUT NPC deja present ici, des deux
        // formes (armor stand ET Citizens), typiquement un orphelin laisse par un
        // arret en pleine partie ou par l'autre mode.
        //
        // Ce menage doit rester AVANT l'aiguillage : place apres, il n'etait
        // jamais atteint en mode Citizens, et un armor stand d'une manche
        // precedente restait plante a cote du vrai NPC.
        clearAt(location);

        if (citizens && CitizensNpc.spawn(location, skin, DISPLAY)) {
            return null;
        }
        World world = location.getWorld();

        Location at = location.getBlock().getLocation().add(0.5d, 0d, 0.5d);
        at.setYaw(location.getYaw());
        at.setPitch(0f);

        ArmorStand npc = world.spawn(at, ArmorStand.class);
        npc.setCustomName(DISPLAY);
        npc.setCustomNameVisible(true);
        npc.setBasePlate(false);
        npc.setArms(true);
        npc.setGravity(false);
        npc.setVisible(true);
        npc.setSmall(false);
        // marker = false : il faut un hitbox pour pouvoir cliquer dessus. Le vol
        // de son equipement est bloque cote listener (evenement de manipulation).
        npc.setMarker(false);
        npc.setRemoveWhenFarAway(false);

        EntityEquipment equipment = npc.getEquipment();
        if (equipment != null) {
            if (head != null) {
                equipment.setHelmet(head);
            }
            equipment.setChestplate(AWMaterial.LEATHER_CHESTPLATE.item());
            equipment.setLeggings(AWMaterial.LEATHER_LEGGINGS.item());
            equipment.setBoots(AWMaterial.LEATHER_BOOTS.item());
        }
        return npc;
    }

    /**
     * Vrai si l'entite est un NPC de boutique. On se fie au nom sentinelle, ce
     * qui rend le test independant de l'arene et survit a un rechargement de
     * chunk (contrairement a une reference d'entite gardee en memoire).
     */
    public static boolean isNpc(Entity entity) {
        return entity != null
                && entity.getType() == EntityType.ARMOR_STAND
                && entity.getCustomName() != null
                && LABEL.equalsIgnoreCase(ChatColor.stripColor(entity.getCustomName()));
    }

    /** Vrai si cette entite est un NPC de boutique, armor stand OU NPC Citizens. */
    public static boolean isShopNpc(Entity entity) {
        return isNpc(entity) || CitizensNpc.isNpc(entity);
    }

    /** Retire tout NPC de boutique present dans le chunk de cette position. */
    public static void clearAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        // On nettoie les deux formes : le mode a pu changer entre deux manches
        // (installation de Citizens, /aw reload...).
        CitizensNpc.removeAt(location);
        // getChunk charge le chunk au besoin : on peut ainsi nettoyer meme si
        // la zone n'etait pas encore chargee.
        for (Entity entity : location.getChunk().getEntities()) {
            if (isNpc(entity)) {
                entity.remove();
            }
        }
    }
}
