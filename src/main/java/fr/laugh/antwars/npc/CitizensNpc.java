package fr.laugh.antwars.npc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/**
 * Pont vers le plugin <b>Citizens</b> pour poser un vrai NPC joueur (skinne)
 * en guise de boutiquier, la ou {@link ShopNpc} n'offre qu'un armor stand.
 *
 * <p><b>Pourquoi Citizens et pas nos propres paquets.</b> Un NPC "vrai joueur"
 * n'existe dans aucune API Bukkit : il faut envoyer soi-meme les paquets
 * reseau (NMS), et ceux-ci changent radicalement sur la plage 1.8-1.21
 * (PacketPlayOutPlayerInfo remplace en 1.19.3, paquet d'apparition remplace en
 * 1.20.2, deplacement des packages en 1.17, mappings Spigot/Mojang divergents,
 * index de DataWatcher mouvants...). Ecrire cela violerait la regle 3 du projet
 * et serait la partie la plus fragile du plugin. Citizens absorbe exactement ce
 * travail, et son API de base n'a pas bouge de 1.8 a 1.21.
 *
 * <p><b>Pourquoi par reflexion.</b> Aucune dependance n'est ajoutee au pom :
 * le build ne depend donc d'aucun depot externe supplementaire (c'est le
 * probleme qui a deja mordu ce projet avec bungeecord-chat, voir regle 5). En
 * prime, l'isolation devient automatique : cette classe ne reference aucun type
 * Citizens en signature ni en champ, donc la JVM ne cherche jamais a charger
 * l'API Citizens sur un serveur qui ne l'a pas. Elle se contente de renvoyer
 * {@code false} et {@link ShopNpc} retombe sur l'armor stand.
 *
 * <p>Surface d'API utilisee, volontairement minuscule et stable :
 * {@code CitizensAPI.getNPCRegistry()}, {@code NPCRegistry.createNPC/getById/getNPC},
 * {@code NPC.spawn/destroy/getId/getName/data}, {@code MetadataStore.setPersistent}.
 * Tout le reste (regard vers le joueur, protection) est tente puis ignore en cas
 * d'echec : une evolution de Citizens degrade le confort, jamais le service.
 */
public final class CitizensNpc {

    /** Cle de metadonnee Citizens portant le pseudo dont on veut la peau. */
    private static final String SKIN_KEY = "player-skin-name";

    private static boolean ready;

    private static Object registry;
    /** L'interface {@code NPC}, gardee pour resoudre les methodes optionnelles. */
    private static Class<?> npcInterface;
    private static Method mCreateNpc;
    private static Method mGetById;
    private static Method mGetNpcOf;
    private static Method mSpawn;
    private static Method mDestroy;
    private static Method mGetId;
    private static Method mGetName;
    private static Method mData;
    private static Method mSetPersistent;

    /** Emplacement (cle de position) -> identifiant du NPC Citizens pose la. */
    private static final Map<String, Integer> byLocation = new HashMap<String, Integer>();
    /** Identifiants de NOS NPC, pour ne jamais confondre avec ceux d'un autre plugin. */
    private static final Set<Integer> ours = new HashSet<Integer>();

    private CitizensNpc() {
    }

    // ================= Mise en route =================

    /**
     * Tente de se brancher sur Citizens.
     *
     * @return vrai si Citizens est present et son API resolue
     */
    public static boolean init(Logger logger) {
        ready = false;
        registry = null;
        byLocation.clear();
        ours.clear();

        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Class<?> registryClass = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            Class<?> npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
            // MetadataStore vit dans le paquet ...api.npc, PAS dans ...api.metadata :
            // verifie au javap sur Citizens 2.0.24 (build 1605) comme sur 2.0.43.
            Class<?> metaClass = Class.forName("net.citizensnpcs.api.npc.MetadataStore");
            npcInterface = npcClass;

            registry = api.getMethod("getNPCRegistry").invoke(null);
            if (registry == null) {
                logger.warning("Citizens est present mais son registre n'est pas encore pret ;"
                        + " le NPC de boutique restera un armor stand.");
                return false;
            }

            // Methodes resolues sur les INTERFACES : les classes d'implementation
            // de Citizens ne sont pas toutes publiques.
            mCreateNpc = registryClass.getMethod("createNPC", EntityType.class, String.class);
            mGetById = registryClass.getMethod("getById", int.class);
            mGetNpcOf = registryClass.getMethod("getNPC", Entity.class);
            mSpawn = npcClass.getMethod("spawn", Location.class);
            mDestroy = npcClass.getMethod("destroy");
            mGetId = npcClass.getMethod("getId");
            mGetName = npcClass.getMethod("getName");
            mData = npcClass.getMethod("data");
            mSetPersistent = metaClass.getMethod("setPersistent", String.class, Object.class);

            ready = true;
            cleanupOrphans(logger);
            logger.info("Citizens detecte : le NPC de boutique sera un vrai NPC joueur.");
            return true;
        } catch (Throwable error) {
            // Toute evolution incompatible de Citizens atterrit ici : on le dit et
            // on repart sur l'armor stand, sans jamais empecher le demarrage.
            // Le message porte le nom exact de la classe ou methode manquante :
            // sans lui, un ClassNotFoundException ne dit pas QUOI est introuvable.
            logger.warning("Citizens est present mais son API n'a pas pu etre utilisee ("
                    + error.getClass().getSimpleName() + " : " + error.getMessage()
                    + ") ; retour a l'armor stand.");
            ready = false;
            return false;
        }
    }

    public static boolean isReady() {
        return ready;
    }

    // ================= Pose et retrait =================

    /**
     * Pose un NPC de boutique.
     *
     * @param skin        pseudo dont on veut la peau, ou vide pour la peau par defaut
     * @param displayName nom affiche au-dessus de la tete (couleurs deja traduites)
     * @return vrai si le NPC a bien ete pose
     */
    public static boolean spawn(Location location, String skin, String displayName) {
        if (!ready || location == null || location.getWorld() == null) {
            return false;
        }
        removeAt(location);
        try {
            Object npc = mCreateNpc.invoke(registry, EntityType.PLAYER, displayName);
            if (npc == null) {
                return false;
            }
            // La peau doit etre choisie AVANT l'apparition : Citizens l'applique
            // au moment ou l'entite est creee.
            if (skin != null && !skin.isEmpty()) {
                Object meta = mData.invoke(npc);
                mSetPersistent.invoke(meta, SKIN_KEY, skin);
            }
            tryLookClose(npc);
            tryProtect(npc);

            // Au centre du bloc, comme l'armor stand, en gardant l'orientation.
            Location at = location.getBlock().getLocation().add(0.5d, 0d, 0.5d);
            at.setYaw(location.getYaw());
            at.setPitch(0f);
            mSpawn.invoke(npc, at);

            int id = ((Integer) mGetId.invoke(npc)).intValue();
            byLocation.put(key(location), Integer.valueOf(id));
            ours.add(Integer.valueOf(id));
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    /** Retire le NPC pose a cet emplacement, s'il y en a un. */
    public static void removeAt(Location location) {
        if (!ready || location == null) {
            return;
        }
        Integer id = byLocation.remove(key(location));
        if (id != null) {
            destroy(id);
        }
    }

    /** Retire tous les NPC poses par AntWars (arret du plugin, rechargement). */
    public static void removeAll() {
        if (!ready) {
            return;
        }
        List<Integer> ids = new ArrayList<Integer>(ours);
        for (Integer id : ids) {
            destroy(id);
        }
        byLocation.clear();
    }

    private static void destroy(Integer id) {
        ours.remove(id);
        try {
            Object npc = mGetById.invoke(registry, Integer.valueOf(id.intValue()));
            if (npc != null) {
                mDestroy.invoke(npc);
            }
        } catch (Throwable ignored) {
            // NPC deja disparu (Citizens recharge, monde decharge) : rien a faire.
        }
    }

    /** Vrai si cette entite est l'un de NOS NPC de boutique. */
    public static boolean isNpc(Entity entity) {
        if (!ready || entity == null) {
            return false;
        }
        try {
            Object npc = mGetNpcOf.invoke(registry, entity);
            if (npc == null) {
                return false;
            }
            // Le test d'identifiant evite de confondre avec le NPC d'un autre plugin.
            return ours.contains((Integer) mGetId.invoke(npc));
        } catch (Throwable error) {
            return false;
        }
    }

    // ================= Interne =================

    /**
     * Detruit les NPC de boutique laisses par une session precedente (crash en
     * pleine partie) : Citizens persiste ses NPC dans ses propres fichiers, sans
     * ce menage ils s'accumuleraient a chaque redemarrage. On les reconnait a leur
     * nom sentinelle, comme les armor stands (voir {@link ShopNpc#LABEL}).
     */
    private static void cleanupOrphans(Logger logger) {
        if (!(registry instanceof Iterable)) {
            return;
        }
        int removed = 0;
        try {
            List<Object> doomed = new ArrayList<Object>();
            Iterator<?> it = ((Iterable<?>) registry).iterator();
            while (it.hasNext()) {
                Object npc = it.next();
                Object name = mGetName.invoke(npc);
                if (name instanceof String
                        && ShopNpc.LABEL.equalsIgnoreCase(ChatColor.stripColor((String) name))) {
                    doomed.add(npc);
                }
            }
            for (Object npc : doomed) {
                mDestroy.invoke(npc);
                removed++;
            }
        } catch (Throwable ignored) {
            // Menage impossible : sans gravite, on continue.
        }
        if (removed > 0) {
            logger.info("Menage Citizens : " + removed + " NPC de boutique orphelin(s) retire(s).");
        }
    }

    /**
     * Fait suivre le joueur du regard (trait LookClose). Confort pur : le nom des
     * methodes du trait a change au fil des versions de Citizens, donc on tente
     * plusieurs formes et on abandonne sans bruit si aucune ne repond.
     */
    private static void tryLookClose(Object npc) {
        try {
            Class<?> lookClose = Class.forName("net.citizensnpcs.trait.LookClose");
            Object trait = null;
            // Methodes resolues sur l'INTERFACE NPC : la classe d'implementation
            // de Citizens n'est pas publique, invoquer une methode obtenue sur
            // elle leverait IllegalAccessException.
            // getOrAddTrait n'existe pas avant Citizens ~2.0.26 ; getTrait, si
            // (et il cree le trait au besoin). D'ou les deux tentatives.
            try {
                trait = npcInterface.getMethod("getOrAddTrait", Class.class).invoke(npc, lookClose);
            } catch (Throwable older) {
                trait = npcInterface.getMethod("getTrait", Class.class).invoke(npc, lookClose);
            }
            if (trait == null) {
                return;
            }
            try {
                lookClose.getMethod("lookClose", boolean.class).invoke(trait, Boolean.TRUE);
            } catch (Throwable other) {
                lookClose.getMethod("setLookClose", boolean.class).invoke(trait, Boolean.TRUE);
            }
        } catch (Throwable ignored) {
            // Le NPC regardera droit devant lui : sans consequence.
        }
    }

    /** Rend le NPC insensible aux degats cote Citizens (ceinture et bretelles). */
    private static void tryProtect(Object npc) {
        try {
            // Resolue sur l'interface, pour la meme raison que dans tryLookClose.
            npcInterface.getMethod("setProtected", boolean.class).invoke(npc, Boolean.TRUE);
        } catch (Throwable ignored) {
            // NpcListeners annule deja les degats de son cote.
        }
    }

    private static String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }
}
