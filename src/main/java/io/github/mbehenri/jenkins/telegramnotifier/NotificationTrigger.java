package io.github.mbehenri.jenkins.telegramnotifier;

import hudson.model.Result;
import java.util.Set;

/**
 * Enum représentant les différents déclencheurs de notification Telegram selon le résultat du build.
 */
public enum NotificationTrigger {
    /**
     * Déclenche une notification quand le build réussit.
     */
    SUCCESS("Success"),

    /**
     * Déclenche une notification quand le build échoue.
     */
    FAILURE("Failure"),

    /**
     * Déclenche une notification quand le build est instable (ex: tests échouent mais build réussit).
     */
    UNSTABLE("Unstable"),

    /**
     * Déclenche une notification quand le build est interrompu.
     */
    ABORTED("Aborted"),

    /**
     * Déclenche une notification quand le build n'est pas exécuté (ex: sauté).
     */
    NOT_BUILT("Not Built");

    private final String displayName;

    NotificationTrigger(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Obtient le nom d'affichage du déclencheur.
     *
     * @return le nom d'affichage
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Vérifie si ce déclencheur correspond au résultat du build donné.
     *
     * @param result le résultat du build à vérifier
     * @return true si le déclencheur correspond au résultat, false sinon
     */
    public boolean matches(Result result) {
        if (result == null) {
            return false;
        }

        switch (this) {
            case SUCCESS:
                return result == Result.SUCCESS;
            case FAILURE:
                return result == Result.FAILURE;
            case UNSTABLE:
                return result == Result.UNSTABLE;
            case ABORTED:
                return result == Result.ABORTED;
            case NOT_BUILT:
                return result == Result.NOT_BUILT;
            default:
                return false;
        }
    }

    /**
     * Vérifie si une notification doit être envoyée selon les déclencheurs configurés et le résultat du build.
     *
     * @param triggers l'ensemble des déclencheurs configurés
     * @param result   le résultat du build
     * @return true si une notification doit être envoyée, false sinon
     */
    public static boolean shouldNotify(Set<NotificationTrigger> triggers, Result result) {
        if (triggers == null || triggers.isEmpty() || result == null) {
            return false;
        }

        return triggers.stream().anyMatch(trigger -> trigger.matches(result));
    }
}
