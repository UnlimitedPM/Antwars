package fr.laugh.antwars.quest;

/**
 * Les quetes du jour : trois objectifs fixes, identiques pour tous les joueurs,
 * qui se reinitialisent chaque jour (voir {@link QuestManager}).
 *
 * <p>Pas de tirage aleatoire ni de catalogue configurable : c'est ce qui garde
 * la fonctionnalite "tres simple", contrairement a un systeme de quetes
 * personnalisees par joueur.
 */
public enum QuestType {

    WIN_GAMES("quest.win_games", 3),
    GET_KILLS("quest.get_kills", 5),
    DESTROY_BEDS("quest.destroy_beds", 1);

    private final String label;
    private final int target;

    QuestType(String label, int target) {
        this.label = label;
        this.target = target;
    }

    /** Cle de traduction du libelle (a passer a Messages.tr). */
    public String getLabel() {
        return label;
    }

    /** Objectif a atteindre pour que la quete soit accomplie. */
    public int getTarget() {
        return target;
    }
}
