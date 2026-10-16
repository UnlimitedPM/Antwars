package fr.laugh.antwars.compat;

import org.bukkit.entity.Player;

import com.cryptomorin.xseries.XSound;

/**
 * Sons courts joues pendant le compte a rebours et au lancement de la partie.
 *
 * <p>Les noms de son ont autant change entre les versions que les materiaux :
 * {@code Sound.CLICK} et {@code Sound.NOTE_PLING} de la 1.8 deviennent
 * {@code Sound.UI_BUTTON_CLICK} et {@code Sound.BLOCK_NOTE_BLOCK_PLING} a partir
 * de la 1.9. Ecrire {@code Sound.XXX} en dur ne compilerait meme pas contre
 * l'API 1.8.8 du projet pour un nom recent (le champ n'existe pas encore dans
 * cette version de l'enum), et planterait au chargement pour un nom disparu.
 *
 * <p>On delegue donc a {@code XSound} (XSeries), qui a ses PROPRES constantes
 * (independantes de la version de Bukkit compilee) et sait vers quel nom reel
 * les faire correspondre a l'execution. Troisieme et dernier point de contact
 * avec XSeries apres {@link AWMaterial} et {@link Heads}, pour la meme raison :
 * changer de bibliotheque un jour ne demanderait de toucher qu'a ces trois-la.
 */
public final class Sounds {

    private Sounds() {
    }

    /**
     * Tic du compte a rebours (3, 2, 1) : un timbre qui monte a l'approche de
     * zero, comme sur la plupart des serveurs de mini-jeux.
     *
     * <p>Passe par {@code record()/soundPlayer()} : {@code XSound.play(Entity,
     * float, float)} est deprecie depuis XSeries 9.x au profit de cette forme.
     */
    public static void countdownTick(Player player, int secondsLeft) {
        int step = 3 - Math.max(0, Math.min(3, secondsLeft));
        XSound.BLOCK_NOTE_BLOCK_PLING.record()
                .withVolume(1f)
                .withPitch(1f + step * 0.2f)
                .soundPlayer()
                .forPlayers(player)
                .play();
    }

    /** Fanfare au lancement reel de la partie ("C'est parti !"). */
    public static void gameStart(Player player) {
        XSound.ENTITY_PLAYER_LEVELUP.record()
                .soundPlayer()
                .forPlayers(player)
                .play();
    }

    /** Serie de kills : plus la serie est longue, plus le son est grave et menacant. */
    public static void killStreak(Player player, int streak) {
        if (streak >= 5) {
            play(XSound.ENTITY_ENDERMAN_SCREAM, player, 1f);
        } else if (streak >= 3) {
            play(XSound.ENTITY_ENDER_DRAGON_GROWL, player, 1.5f);
        } else {
            play(XSound.ENTITY_ZOMBIE_AMBIENT, player, 1f);
        }
    }

    private static void play(XSound sound, Player player, float pitch) {
        sound.record()
                .withVolume(1f)
                .withPitch(pitch)
                .soundPlayer()
                .forPlayers(player)
                .play();
    }
}
