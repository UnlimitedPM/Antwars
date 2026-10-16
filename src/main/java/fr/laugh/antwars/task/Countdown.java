package fr.laugh.antwars.task;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.State;
import fr.laugh.antwars.compat.Sounds;
import fr.laugh.antwars.compat.Titles;
import fr.laugh.antwars.lang.Messages;

/**
 * Compte a rebours avant le lancement d'une partie.
 *
 * <p>Lance par {@link Arena} des que le minimum de joueurs est atteint (duree
 * complete), ou raccourci a quelques secondes quand le vote de demarrage est
 * atteint. Les trois dernieres secondes s'affichent en gros au centre de
 * l'ecran (titre rouge), puis {@link Arena#start()} teleporte les equipes.
 */
public class Countdown extends BukkitRunnable {

    private final Arena arena;
    private int timer;

    public Countdown(Arena arena, int seconds) {
        this.arena = arena;
        this.timer = seconds;
    }

    @Override
    public void run() {
        // Securite : si l'arene a change d'etat entre-temps (joueurs partis,
        // arene reinitialisee), la tache s'arrete d'elle-meme.
        if (!arena.isState(State.STARTING)) {
            cancel();
            return;
        }

        if (timer <= 0) {
            // start() annule cette tache au passage.
            arena.start();
            return;
        }

        for (Player player : arena.onlinePlayers()) {
            player.setLevel(timer);
        }

        // Les trois dernieres secondes : gros compte a rebours a l'ecran, en
        // rouge. Chaque nombre remplace le precedent (voir Titles).
        if (timer <= 3) {
            for (Player player : arena.onlinePlayers()) {
                Titles.send(player, Messages.tr("title.countdown", "n", timer), "");
                Sounds.countdownTick(player, timer);
            }
        } else if (timer == 60 || timer == 30 || timer == 15 || timer == 10 || timer <= 5) {
            arena.broadcast(Messages.tr("game.countdown_broadcast", "n", timer));
        }

        timer--;
    }
}
