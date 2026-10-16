package fr.laugh.antwars.shop;

/**
 * Les potions vendues en boutique.
 *
 * <p>Chaque potion porte les deux representations dont AntWars a besoin, car la
 * fabrication d'une potion est l'un des rares points ou les versions divergent
 * radicalement :
 * <ul>
 *   <li>sur 1.8-1.12, une potion est definie par la valeur de data de l'item,
 *       posee par {@code org.bukkit.potion.Potion} a partir d'un
 *       {@code PotionType} et d'un niveau (le nom 1.8 de la regeneration est
 *       {@code REGEN}, pas {@code REGENERATION}) ;</li>
 *   <li>a partir de la 1.9, l'effet est porte par le {@code PotionMeta} (effet,
 *       amplificateur, duree en ticks) et le splash est un materiau distinct.</li>
 * </ul>
 *
 * <p>Les durees indiquees reproduisent le vanilla : Regeneration I = 45s (900
 * ticks), Regeneration II = 22s (440), splash Poison = 33s (660), splash
 * Faiblesse = 1:07 (1340). Les degats sont instantanes, leur duree est ignoree.
 *
 * <p>La construction proprement dite vit dans {@code compat.Potions}, seul juge
 * de la version. Cet enum ne fait que decrire.
 */
public enum PotionKind {

    // label (CLE de traduction), type 1.8, niveau, duree(ticks), splash, prix, noms d'effet modernes
    HARMING_I("potion.harming_1",    "INSTANT_DAMAGE", 1, 1,    true,  64,  new String[] { "HARM", "INSTANT_DAMAGE" }),
    HARMING_II("potion.harming_2",   "INSTANT_DAMAGE", 2, 1,    true,  128, new String[] { "HARM", "INSTANT_DAMAGE" }),
    REGEN_I("potion.regen_1",        "REGEN",          1, 900,  false, 64,  new String[] { "REGENERATION", "REGEN" }),
    REGEN_II("potion.regen_2",       "REGEN",          2, 440,  false, 128, new String[] { "REGENERATION", "REGEN" }),
    POISON("potion.poison",          "POISON",         1, 660,  true,  64,  new String[] { "POISON" }),
    WEAKNESS("potion.weakness",      "WEAKNESS",       1, 1340, true,  64,  new String[] { "WEAKNESS" });

    private final String label;
    private final String legacyType;
    private final int level;
    private final int durationTicks;
    private final boolean splash;
    private final int defaultPrice;
    private final String[] modernNames;

    PotionKind(String label, String legacyType, int level, int durationTicks, boolean splash,
            int defaultPrice, String[] modernNames) {
        this.label = label;
        this.legacyType = legacyType;
        this.level = level;
        this.durationTicks = durationTicks;
        this.splash = splash;
        this.defaultPrice = defaultPrice;
        this.modernNames = modernNames;
    }

    /** Cle de traduction du libelle (a passer a Messages.tr), pas le texte final. */
    public String getLabel() {
        return label;
    }

    /** Nom du {@code PotionType} tel qu'il existe en 1.8 (ex. {@code REGEN}). */
    public String getLegacyType() {
        return legacyType;
    }

    /** Niveau de la potion : 1 ou 2 (l'amplificateur moderne vaut niveau - 1). */
    public int getLevel() {
        return level;
    }

    /** Duree de l'effet en ticks, pour le chemin moderne (ignoree si instantane). */
    public int getDurationTicks() {
        return durationTicks;
    }

    public boolean isSplash() {
        return splash;
    }

    public int getDefaultPrice() {
        return defaultPrice;
    }

    /**
     * Noms candidats de l'effet moderne, essayes dans l'ordre via
     * {@code PotionEffectType.getByName} : le premier trouve gagne. Deux noms
     * pour les effets dont l'intitule a bouge selon la version.
     */
    public String[] getModernNames() {
        return modernNames;
    }
}
