package fr.laugh.antwars;

/**
 * Etats successifs d'une arene.
 *
 * <p>L'etat est porte par chaque {@link Arena} : plusieurs arenes peuvent
 * tourner en parallele, chacune dans sa phase.
 */
public enum State {

    /** L'arene attend des joueurs. */
    WAITING,

    /** Le minimum de joueurs est atteint, le compte a rebours tourne. */
    STARTING,

    /** Phase de minage et de preparation. */
    PLAYING,

    /** Bordure active et PvP ouvert. */
    DEATHMATCH,

    /** Partie terminee, remise a zero en cours. */
    FINISH
}
