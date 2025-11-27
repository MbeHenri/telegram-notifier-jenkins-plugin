package io.github.mbehenri.jenkins.telegramnotifier;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import io.github.mbehenri.jenkins.telegramnotifier.config.TelegramConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Formate les messages de notification Telegram avec les informations de build et le contenu personnalisé.
 */
public class MessageFormatter {

    private static final String EMOJI_SUCCESS = "\u2705"; // ✅
    private static final String EMOJI_FAILURE = "\u274C"; // ❌
    private static final String EMOJI_UNSTABLE = "\u26A0\uFE0F"; // ⚠️
    private static final String EMOJI_ABORTED = "\uD83D\uDED1"; // 🛑
    private static final String EMOJI_NOT_BUILT = "\u23F8\uFE0F"; // ⏸️
    private static final String EMOJI_UNKNOWN = "\u2753"; // ❓

    /**
     * Formate le message de notification avec les informations du build.
     *
     * @param build         le build Jenkins
     * @param customMessage message personnalisé optionnel à inclure
     * @return le message formaté
     */
    public static String formatMessage(AbstractBuild<?, ?> build, String customMessage) {
        if (build == null) {
            return "Invalid build information";
        }

        StringBuilder message = new StringBuilder();

        // Ligne de statut avec emoji pour identification visuelle rapide
        Result result = build.getResult();
        String emoji = getEmojiForResult(result);
        String status = result != null ? result.toString() : "UNKNOWN";
        message.append(emoji).append(" Build *").append(status).append("*\n\n");

        // Informations principales du job
        message.append("*Job:* ").append(escapeMarkdown(build.getProject().getFullDisplayName())).append("\n");
        message.append("*Build:* #").append(build.getNumber()).append("\n");
        message.append("*Duration:* ").append(formatDuration(build.getDuration())).append("\n");

        // Cause du déclenchement (utilisateur, SCM, timer, etc.)
        String cause = formatCause(build);
        if (cause != null && !cause.isEmpty()) {
            message.append("*Started by:* ").append(escapeMarkdown(cause)).append("\n");
        }

        // Message personnalisé de l'utilisateur (si fourni)
        // Les variables ${BUILD_STATUS}, ${JOB_NAME}, etc. sont remplacées
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            String formattedCustomMessage = formatCustomMessage(customMessage, build);
            message.append("\n").append(formattedCustomMessage).append("\n");
        }

        // Lien vers le build pour accès direct depuis Telegram
        String buildUrl = build.getAbsoluteUrl();
        message.append("\n[View build](").append(buildUrl).append(")");

        return TelegramConfig.truncateMessage(message.toString());
    }

    /**
     * Formate un message personnalisé en remplaçant les variables par les informations du build.
     *
     * @param customMessage le template du message personnalisé
     * @param build         le build Jenkins
     * @return le message personnalisé formaté
     */
    private static String formatCustomMessage(String customMessage, AbstractBuild<?, ?> build) {
        String formatted = customMessage;

        Result result = build.getResult();
        String status = result != null ? result.toString() : "UNKNOWN";

        formatted = formatted.replace("${BUILD_STATUS}", status);
        formatted = formatted.replace("${JOB_NAME}", build.getProject().getFullDisplayName());
        formatted = formatted.replace("${BUILD_NUMBER}", String.valueOf(build.getNumber()));
        formatted = formatted.replace("${BUILD_DURATION}", formatDuration(build.getDuration()));
        formatted = formatted.replace("${BUILD_URL}", build.getAbsoluteUrl());

        String cause = formatCause(build);
        formatted = formatted.replace("${CAUSE}", cause != null ? cause : "Unknown");

        return formatted;
    }

    /**
     * Obtient l'emoji correspondant au résultat du build.
     *
     * @param result le résultat du build
     * @return l'emoji correspondant
     */
    private static String getEmojiForResult(Result result) {
        if (result == null) {
            return EMOJI_UNKNOWN;
        }

        if (result == Result.SUCCESS) {
            return EMOJI_SUCCESS;
        } else if (result == Result.FAILURE) {
            return EMOJI_FAILURE;
        } else if (result == Result.UNSTABLE) {
            return EMOJI_UNSTABLE;
        } else if (result == Result.ABORTED) {
            return EMOJI_ABORTED;
        } else if (result == Result.NOT_BUILT) {
            return EMOJI_NOT_BUILT;
        }

        return EMOJI_UNKNOWN;
    }

    /**
     * Formate la durée du build dans un format lisible.
     *
     * @param durationMillis la durée en millisecondes
     * @return la durée formatée
     */
    public static String formatDuration(long durationMillis) {
        if (durationMillis < 0) {
            return "N/A";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Formate la cause du build (qui ou quoi a déclenché le build).
     *
     * @param build le build Jenkins
     * @return la cause formatée
     */
    private static String formatCause(AbstractBuild<?, ?> build) {
        List<Cause> causes = build.getCauses();
        if (causes.isEmpty()) {
            return "Inconnu";
        }

        // Utilise la première cause (la plus pertinente)
        // Ex: "Started by user admin", "Started by timer", "Started by SCM change"
        Cause firstCause = causes.get(0);
        return firstCause.getShortDescription();
    }

    /**
     * Échappe les caractères spéciaux dans le texte Markdown.
     * <p>
     * Le parser Markdown de Telegram nécessite l'échappement de certains caractères.
     * Cependant, il faut faire attention à ne pas casser le formatage intentionnel.
     *
     * @param text le texte à échapper
     * @return le texte échappé
     */
    private static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }

        // Échappement minimal pour préserver la lisibilité
        // Les underscores (_) sont utilisés pour l'italique en Markdown,
        // donc ils doivent être échappés dans les noms de projets/jobs
        // Ex: "My_Project_Name" devient "My\_Project\_Name"
        // Le Markdown de Telegram est assez tolérant avec les autres caractères
        return text.replace("_", "\\_");
    }
}
