package fr.laugh.antwars.compat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.entity.Player;

import fr.laugh.antwars.Main;

/**
 * Compatibilite proxy (BungeeCord / Waterfall / Velocity) : renvoi d'un joueur
 * vers un autre serveur du reseau, via le canal de message plugin standard.
 *
 * <p>Une seule divergence de version, isolee ici : le nom du canal a ete
 * normalise en 1.13. Avant, c'est {@code "BungeeCord"} ; a partir de la 1.13,
 * les canaux doivent etre en minuscules et prefixes, d'ou {@code "bungeecord:main"}.
 * Enregistrer le mauvais nom leve une exception sur la version concernee. Le
 * meme canal est compris par BungeeCord, Waterfall et Velocity (avec son option
 * de compatibilite BungeeCord activee).
 *
 * <p>Le protocole est un sous-message {@code Connect} : deux chaines UTF, le mot
 * {@code "Connect"} puis le nom du serveur cible tel qu'il figure dans la config
 * du proxy. Rien de tout cela n'a bouge entre la 1.8 et la 1.21.
 */
public final class Proxy {

    private static final String CHANNEL_LEGACY = "BungeeCord";
    private static final String CHANNEL_MODERN = "bungeecord:main";

    private final Main main;
    private final boolean enabled;
    private final String fallbackServer;
    private final String channel;

    public Proxy(Main main) {
        this.main = main;
        this.enabled = main.getConfig().getBoolean("bungeecord.enabled", false);
        this.fallbackServer = main.getConfig().getString("bungeecord.fallback-server", "lobby");
        this.channel = MCVersion.atLeast(13) ? CHANNEL_MODERN : CHANNEL_LEGACY;
        if (enabled) {
            main.getServer().getMessenger().registerOutgoingPluginChannel(main, channel);
            main.getLogger().info("Mode proxy actif : renvoi vers le serveur '"
                    + fallbackServer + "' en fin de partie (canal " + channel + ").");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Renvoie le joueur vers le serveur hub configure. */
    public void sendToFallback(Player player) {
        sendToServer(player, fallbackServer);
    }

    /** Renvoie le joueur vers un serveur donne du reseau. */
    public void sendToServer(Player player, String server) {
        if (!enabled || player == null || !player.isOnline()
                || server == null || server.isEmpty()) {
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (IOException e) {
            // Un ByteArrayOutputStream ne leve jamais : securite de forme.
            return;
        }
        player.sendPluginMessage(main, channel, bytes.toByteArray());
    }
}
