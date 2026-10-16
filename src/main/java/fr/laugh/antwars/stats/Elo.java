package fr.laugh.antwars.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calcul ELO simple, en equipes : chaque equipe a une note moyenne, et l'issue
 * d'une partie (l'equipe gagnante contre chacune des autres encore en lice)
 * ajuste cette note comme un ELO d'echecs classique.
 *
 * <p>Une equipe perdante n'est comparee qu'a la gagnante, faute de savoir dans
 * quel ordre les autres equipes ont ete eliminees (Arena ne connait que le nom
 * du vainqueur). L'equipe gagnante, elle, est comparee a chaque adversaire
 * encore represente, et son gain est la moyenne des ecarts obtenus.
 *
 * <p>Pure logique, sans aucune dependance a Bukkit : c'est ce qui la rend
 * testable en JUnit sans avoir a simuler un serveur.
 */
public final class Elo {

    /** Facteur K : ampleur maximale d'un changement de note en une partie. */
    private static final int K = 32;

    private Elo() {
    }

    /** Probabilite attendue de victoire de {@code rating} face a {@code opponent}. */
    static double expectedScore(int rating, int opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponent - rating) / 400.0));
    }

    /**
     * Changement de note d'une equipe de note {@code rating} face a une equipe de
     * note {@code opponent}, ayant reellement gagne ({@code won}) ou perdu.
     */
    static int delta(int rating, int opponent, boolean won) {
        double expected = expectedScore(rating, opponent);
        double actual = won ? 1.0 : 0.0;
        return (int) Math.round(K * (actual - expected));
    }

    /**
     * Ajustement de note pour chaque equipe encore representee en fin de partie.
     *
     * @param winningTeam nom de l'equipe gagnante ; doit etre une cle de
     *                     {@code teamRatings}, sinon la map renvoyee est vide
     * @param teamRatings note moyenne actuelle de chaque equipe encore en lice
     * @return le delta (peut etre negatif) a appliquer a chaque equipe presente
     */
    public static Map<String, Integer> deltas(String winningTeam, Map<String, Integer> teamRatings) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        Integer winnerRatingBoxed = teamRatings.get(winningTeam);
        if (winnerRatingBoxed == null) {
            return result;
        }
        int winnerRating = winnerRatingBoxed.intValue();

        int sum = 0;
        int opponents = 0;
        for (Map.Entry<String, Integer> entry : teamRatings.entrySet()) {
            if (entry.getKey().equals(winningTeam)) {
                continue;
            }
            int opponentRating = entry.getValue().intValue();
            result.put(entry.getKey(), Integer.valueOf(delta(opponentRating, winnerRating, false)));
            sum += delta(winnerRating, opponentRating, true);
            opponents++;
        }
        result.put(winningTeam, Integer.valueOf(opponents == 0 ? 0 : sum / opponents));
        return result;
    }
}
