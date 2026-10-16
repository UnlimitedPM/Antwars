package fr.laugh.antwars.quest;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Progression des quetes du jour pour un joueur.
 *
 * <p>La remise a zero n'est pas poussee par une tache planifiee : elle se fait
 * "paresseusement", des que {@link #resetIfNewDay} constate que la date stockee
 * n'est plus celle d'aujourd'hui (a la connexion du joueur, ou a la prochaine
 * progression). Plus simple et plus sur qu'un reveil global a minuit qui devrait
 * gerer les joueurs deja connectes ET ceux qui se connectent entre-temps.
 */
public final class QuestProgress {

    private LocalDate date;
    private final Map<QuestType, Integer> progress = new EnumMap<QuestType, Integer>(QuestType.class);

    public QuestProgress(LocalDate date) {
        this.date = date;
    }

    /** Remet la progression a zero si le jour a change ; renvoie vrai si ca a ete fait. */
    public boolean resetIfNewDay(LocalDate today) {
        if (date.equals(today)) {
            return false;
        }
        date = today;
        progress.clear();
        return true;
    }

    public int get(QuestType type) {
        Integer value = progress.get(type);
        return value == null ? 0 : value.intValue();
    }

    /** Fixe la progression brute d'une quete (utilise au chargement depuis le disque). */
    public void set(QuestType type, int value) {
        progress.put(type, Integer.valueOf(clamp(type, value)));
    }

    /** Ajoute a la progression d'une quete, sans jamais depasser son objectif. */
    public void add(QuestType type, int amount) {
        set(type, get(type) + amount);
    }

    public boolean isComplete(QuestType type) {
        return get(type) >= type.getTarget();
    }

    public LocalDate getDate() {
        return date;
    }

    /** Copie independante, remise au thread d'I/O pour la sauvegarde. */
    public QuestProgress copy() {
        QuestProgress copy = new QuestProgress(date);
        copy.progress.putAll(progress);
        return copy;
    }

    /** Vue interne pour la persistance ; ne pas modifier depuis l'exterieur. */
    Map<QuestType, Integer> asMap() {
        return progress;
    }

    private static int clamp(QuestType type, int value) {
        return Math.max(0, Math.min(type.getTarget(), value));
    }
}
