package io.github.mbehenri.jenkins.telegramnotifier;

import io.github.mbehenri.jenkins.telegramnotifier.config.TelegramConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe de service pour l'envoi de messages à Telegram via l'API Bot.
 * <p>
 * Cette classe est sans état et thread-safe, adaptée à l'exécution parallèle de builds.
 */
public class TelegramSender {

    private static final Logger LOGGER = Logger.getLogger(TelegramSender.class.getName());

    /**
     * Envoie un message à Telegram.
     *
     * @param botToken le token du bot Telegram
     * @param chatId   l'ID du chat cible
     * @param message  le message à envoyer
     * @return true si le message a été envoyé avec succès, false sinon
     */
    public boolean sendMessage(String botToken, String chatId, String message) {
        if (botToken == null || botToken.trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Le token du bot est vide, impossible d'envoyer le message");
            return false;
        }

        if (chatId == null || chatId.trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "L'ID du chat est vide, impossible d'envoyer le message");
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            LOGGER.log(Level.WARNING, "Le message est vide, rien à envoyer");
            return false;
        }

        try {
            String apiUrl = buildApiUrl(botToken);
            String requestBody = buildRequestBody(chatId, message);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TelegramConfig.REQUEST_TIMEOUT_SECONDS))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TelegramConfig.REQUEST_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            LOGGER.log(Level.FINE, "Envoi du message à l'API Telegram");

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.log(Level.INFO, "Message envoyé avec succès à Telegram");
                return true;
            } else {
                LOGGER.log(Level.WARNING, "Échec de l'envoi du message à Telegram. Code statut: {0}, Réponse: {1}",
                        new Object[]{response.statusCode(), response.body()});
                return false;
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erreur IO lors de l'envoi du message à Telegram", e);
            return false;
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "La requête à Telegram a été interrompue", e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue lors de l'envoi du message à Telegram", e);
            return false;
        }
    }

    /**
     * Construit l'URL de l'API Telegram pour l'envoi de messages.
     *
     * @param botToken le token du bot
     * @return l'URL de l'API
     */
    private String buildApiUrl(String botToken) {
        return TelegramConfig.TELEGRAM_API_URL + botToken + "/sendMessage";
    }

    /**
     * Construit le corps de la requête pour l'API Telegram.
     *
     * @param chatId  l'ID du chat
     * @param message le message
     * @return le corps de la requête encodé en URL
     */
    private String buildRequestBody(String chatId, String message) {
        StringBuilder body = new StringBuilder();
        body.append("chat_id=").append(urlEncode(chatId));
        body.append("&text=").append(urlEncode(message));
        body.append("&parse_mode=").append(urlEncode(TelegramConfig.PARSE_MODE));
        return body.toString();
    }

    /**
     * Encode une valeur en URL.
     *
     * @param value la valeur à encoder
     * @return la valeur encodée en URL
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Échec de l'encodage URL, utilisation de la valeur originale", e);
            return value;
        }
    }
}
