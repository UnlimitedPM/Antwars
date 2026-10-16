package fr.laugh.antwars.compat;

import org.bukkit.Bukkit;

/**
 * Detection de la version du serveur, valable de 1.8 a 1.21 et au-dela.
 *
 * <p>On se base sur {@link Bukkit#getBukkitVersion()}, qui renvoie une chaine
 * du type {@code "1.21.4-R0.1-SNAPSHOT"} et existe depuis toujours.
 *
 * <p>On n'utilise volontairement PAS l'ancienne astuce du package
 * {@code org.bukkit.craftbukkit.v1_XX_RX} : Paper a supprime le suffixe de
 * version de ses packages en 1.20.5, ce qui casse cette methode sur tous les
 * serveurs recents.
 *
 * <p><b>Numerotation par annee (depuis 2026).</b> A partir de la sortie 26.1,
 * Mojang numerote les versions par annee ({@code 26.1}, {@code 26.2}...) au
 * lieu de {@code 1.X}, et {@code getBukkitVersion()} renvoie alors quelque
 * chose comme {@code "26.1.2.build.63-stable"} plutot que
 * {@code "26.1.2-R0.1-SNAPSHOT"}. Sans precaution, {@link #init()} lirait le
 * "1" de "26.<b>1</b>.2..." comme s'il s'agissait du numero mineur d'une 1.X,
 * et {@link #isLegacy()} croirait a tort tourner sur une antique 1.9-1.12 —
 * avec pour consequence un {@code NoClassDefFoundError} au premier achat de
 * potion ({@code Potions} partirait sur {@code LegacyPotions}, dont la classe
 * {@code org.bukkit.potion.Potion} n'existe plus sur un serveur recent). On
 * detecte donc ce nouveau format au premier nombre (jamais "1" sur les
 * versions par annee, toujours "1" avant) et on le traite comme "tres recent",
 * exactement comme un format totalement imprevu.
 */
public final class MCVersion {

    /** Numero mineur : le 21 de 1.21.4. -1 tant que {@link #init()} n'a pas tourne. */
    private static int minor = -1;

    /** Numero de patch : le 4 de 1.21.4. */
    private static int patch = 0;

    /** Chaine brute renvoyee par le serveur, utile pour les logs. */
    private static String raw = "inconnue";

    private MCVersion() {
    }

    /**
     * A appeler une fois au demarrage du plugin, avant tout autre usage.
     */
    public static void init() {
        raw = Bukkit.getBukkitVersion();

        // "1.21.4-R0.1-SNAPSHOT" -> "1.21.4" ; "26.1.2.build.63-stable" -> "26.1.2.build.63"
        String numbers = raw.split("-")[0];
        String[] parts = numbers.split("\\.");

        try {
            int first = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
            if (first != 1) {
                // Numerotation par annee (26.1, 26.2...) : le premier nombre
                // n'est plus "1" mais l'annee. Forcement plus recent que la
                // toute derniere 1.X (1.21), donc meme repli que le format
                // totalement imprevu ci-dessous - inutile de decoder le
                // numero de "drop" pour minor(), rien dans le plugin n'a
                // besoin de plus fin que "tres recent".
                minor = 99;
                patch = 0;
                return;
            }
            minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        } catch (NumberFormatException e) {
            // Format inattendu : on suppose une version recente plutot que
            // de faire croire a tort au plugin qu'il tourne sur du 1.8.
            minor = 99;
            patch = 0;
        }
    }

    private static void ensureInit() {
        if (minor == -1) {
            init();
        }
    }

    /** Numero mineur de la version ({@code 8} pour 1.8.8, {@code 21} pour 1.21.4). */
    public static int minor() {
        ensureInit();
        return minor;
    }

    /** Numero de patch de la version ({@code 8} pour 1.8.8, {@code 4} pour 1.21.4). */
    public static int patch() {
        ensureInit();
        return patch;
    }

    /** Version brute telle que rapportee par le serveur. */
    public static String raw() {
        ensureInit();
        return raw;
    }

    /** Vrai si le serveur tourne au moins en 1.{@code minorVersion}. */
    public static boolean atLeast(int minorVersion) {
        return minor() >= minorVersion;
    }

    /** Vrai si le serveur tourne au moins en 1.{@code minorVersion}.{@code patchVersion}. */
    public static boolean atLeast(int minorVersion, int patchVersion) {
        ensureInit();
        if (minor != minorVersion) {
            return minor > minorVersion;
        }
        return patch >= patchVersion;
    }

    /**
     * Vrai pour les versions 1.8 a 1.12, c'est-a-dire avant "The Flattening".
     * Sur ces versions, les items se distinguent par une valeur de data
     * (les fameux {@code :7} des blocs de verre) et les noms de materiaux
     * sont les anciens ({@code WOOD_SWORD} et non {@code WOODEN_SWORD}).
     */
    public static boolean isLegacy() {
        return minor() < 13;
    }
}
