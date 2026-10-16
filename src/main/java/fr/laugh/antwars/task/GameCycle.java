package fr.laugh.antwars.task;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.lang.Messages;

/**
 * Deroulement d'une partie : phase de minage, puis deathmatch.
 *
 * <p>La bordure du monde est geree par l'arene elle-meme, a partir des valeurs
 * de config.yml, et remise a sa taille normale en fin de partie.
 */
public class GameCycle extends BukkitRunnable {

    private final Arena arena;
    private int timer;

    public GameCycle(Arena arena) {
        this.arena = arena;
        this.timer = arena.getBorderTime();
    }

    @Override
    public void run() {
        if (!arena.isRunning()) {
            cancel();
            return;
        }

        // Temps restant avant l'apparition des bordures, pour le scoreboard.
        arena.setDeathmatchTimer(Math.max(0, timer));

        if (timer <= 0) {
            arena.startDeathmatch();
            cancel();
            return;
        }

        if (timer == 300 || timer == 120 || timer == 60 || timer == 30 || timer <= 5) {
            arena.broadcast(Messages.tr("game.deathmatch_countdown", "n", timer));
        }

        // Le niveau sert de minuteur visible pour les joueurs.
        for (Player player : arena.onlinePlayers()) {
            player.setLevel(timer);
        }

        timer--;
    }

    /** Secondes -> M:SS (negatif ramene a 0). */
    public static String clock(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes + ":" + (rest < 10 ? "0" + rest : Integer.toString(rest));
    }
}
