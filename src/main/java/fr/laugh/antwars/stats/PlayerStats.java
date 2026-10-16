package fr.laugh.antwars.stats;

import java.util.UUID;

/**
 * Statistiques d'un joueur : parties, victoires, defaites, kills, morts.
 *
 * <p>Regle de threading (voir {@link StatsManager}) : cet objet n'est modifie
 * que sur le thread principal (les evenements de jeu). Ce qui traverse vers le
 * thread d'I/O n'est jamais l'objet vivant mais une {@link #copy() copie}, ce
 * qui evite tout acces concurrent sans avoir a synchroniser.
 */
public class PlayerStats {

    private final UUID uuid;
    private String name;

    private int games;
    private int wins;
    private int losses;
    private int kills;
    private int deaths;
    /** Note ELO, 1000 pour un joueur qui n'a jamais fini de partie. */
    private int rating;

    public PlayerStats(UUID uuid, String name) {
        this(uuid, name, 0, 0, 0, 0, 0, 1000);
    }

    public PlayerStats(UUID uuid, String name, int games, int wins, int losses,
            int kills, int deaths, int rating) {
        this.uuid = uuid;
        this.name = name;
        this.games = games;
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
        this.rating = rating;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getGames() {
        return games;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getRating() {
        return rating;
    }

    /** Change la note ELO, jamais en dessous de zero. */
    public void setRating(int rating) {
        this.rating = Math.max(0, rating);
    }

    public void addRating(int delta) {
        setRating(rating + delta);
    }

    public void addGame() {
        games++;
    }

    public void addWin() {
        wins++;
    }

    public void addLoss() {
        losses++;
    }

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    /** Ratio kills/morts, les morts a zero valant 1 pour ne pas diviser par zero. */
    public double kd() {
        return deaths == 0 ? kills : (double) kills / (double) deaths;
    }

    /** Copie independante, remise au thread d'I/O pour la sauvegarde. */
    public PlayerStats copy() {
        return new PlayerStats(uuid, name, games, wins, losses, kills, deaths, rating);
    }
}
