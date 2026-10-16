package fr.laugh.antwars.discord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.lang.Messages;

/**
 * Annonce de fin de partie sur un webhook Discord (config {@code discord.*}) :
 * qui a gagne, sur quelle arene, combien de joueurs.
 *
 * <p>Requete HTTP brute via {@code java.net.HttpURLConnection}, present depuis
 * toujours dans le JDK : aucune dependance ajoutee au pom pour un besoin aussi
 * simple (pas de bibliotheque JSON non plus, le corps est construit a la main).
 *
 * <p>L'appel part sur un thread asynchrone du planificateur Bukkit, jamais sur
 * le thread principal : si Discord met du temps a repondre ou est injoignable,
 * le serveur ne doit pas en souffrir.
 */
public final class DiscordNotifier {

    private final Main main;
    private final boolean enabled;
    private final String webhookUrl;

    public DiscordNotifier(Main main) {
        this.main = main;
        this.enabled = main.getConfig().getBoolean("discord.enabled", false);
        this.webhookUrl = main.getConfig().getString("discord.webhook-url", "");
    }

    /** Annonce la fin d'une partie : arene, equipe gagnante (ou {@code null}), nombre de joueurs. */
    public void announceEnd(Arena arena, String winningTeam, int playerCount) {
        if (!enabled || webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }
        final String message = winningTeam == null
                ? Messages.tr("discord.game_no_winner", "arena", arena.getName(), "players", playerCount)
                : Messages.tr("discord.game_won",
                        "team", winningTeam, "arena", arena.getName(), "players", playerCount);

        Bukkit.getScheduler().runTaskAsynchronously(main, new Runnable() {
            @Override
            public void run() {
                send(message);
            }
        });
    }

    /** Envoie effectivement la requete. Ne tourne jamais sur le thread principal. */
    private void send(String content) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            String json = "{\"username\":\"AntWars\",\"content\":\"" + escape(content) + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }

            int status = connection.getResponseCode();
            if (status >= 300) {
                main.getLogger().warning("Webhook Discord : reponse HTTP " + status
                        + " (verifie discord.webhook-url dans config.yml).");
            }
        } catch (IOException e) {
            main.getLogger().warning("Webhook Discord injoignable : " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Echappe ce qui casserait le JSON : antislash, guillemets, retours a la ligne. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}
