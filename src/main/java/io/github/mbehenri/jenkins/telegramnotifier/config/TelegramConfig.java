package io.github.mbehenri.jenkins.telegramnotifier.config;

/**
 * Constantes de configuration pour la communication avec l'API Telegram.
 */
public final class TelegramConfig {

    /**
     * URL de base de l'API Telegram Bot.
     */
    public static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";

    /**
     * Timeout des requêtes HTTP en secondes.
     */
    public static final int REQUEST_TIMEOUT_SECONDS = 30;

    /**
     * Mode de parsing pour le formatage des messages.
     */
    public static final String PARSE_MODE = "Markdown";

    /**
     * Longueur maximale de message autorisée par l'API Telegram.
     */
    public static final int MAX_MESSAGE_LENGTH = 4096;

    private TelegramConfig() {
        // Empêche l'instanciation
    }

    /**
     * Tronque un message pour respecter la longueur maximale de Telegram.
     *
     * @param message le message à tronquer
     * @return le message tronqué
     */
    public static String truncateMessage(String message) {
        if (message == null) {
            return "";
        }

        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }

        String truncationSuffix = "\n\n... (message truncated)";
        int maxLength = MAX_MESSAGE_LENGTH - truncationSuffix.length();
        return message.substring(0, maxLength) + truncationSuffix;
    }
}
