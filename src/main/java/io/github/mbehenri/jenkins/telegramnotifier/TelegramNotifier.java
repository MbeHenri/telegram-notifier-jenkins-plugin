package io.github.mbehenri.jenkins.telegramnotifier;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notificateur Jenkins qui envoie des notifications Telegram après l'exécution d'un build.
 * <p>
 * Cette classe étend {@link Notifier} (pas Recorder) pour garantir que les notifications
 * sont envoyées même si le build est interrompu.
 */
public class TelegramNotifier extends Notifier {

    private static final Logger LOGGER = Logger.getLogger(TelegramNotifier.class.getName());

    private final String telegramTokenCredentialId;
    private final String telegramChatIdCredentialId;

    private boolean notifyOnSuccess = false;
    private boolean notifyOnFailure = true;
    private boolean notifyOnUnstable = true;
    private boolean notifyOnAborted = false;
    private boolean notifyOnNotBuilt = false;

    private String customMessage = "";

    /**
     * Constructeur pour TelegramNotifier.
     *
     * @param telegramTokenCredentialId  l'ID du credential pour le token du bot
     * @param telegramChatIdCredentialId l'ID du credential pour l'ID du chat
     */
    @DataBoundConstructor
    public TelegramNotifier(String telegramTokenCredentialId, String telegramChatIdCredentialId) {
        this.telegramTokenCredentialId = telegramTokenCredentialId;
        this.telegramChatIdCredentialId = telegramChatIdCredentialId;
    }

    public String getTelegramTokenCredentialId() {
        return telegramTokenCredentialId;
    }

    public String getTelegramChatIdCredentialId() {
        return telegramChatIdCredentialId;
    }

    public boolean isNotifyOnSuccess() {
        return notifyOnSuccess;
    }

    @DataBoundSetter
    public void setNotifyOnSuccess(boolean notifyOnSuccess) {
        this.notifyOnSuccess = notifyOnSuccess;
    }

    public boolean isNotifyOnFailure() {
        return notifyOnFailure;
    }

    @DataBoundSetter
    public void setNotifyOnFailure(boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }

    public boolean isNotifyOnUnstable() {
        return notifyOnUnstable;
    }

    @DataBoundSetter
    public void setNotifyOnUnstable(boolean notifyOnUnstable) {
        this.notifyOnUnstable = notifyOnUnstable;
    }

    public boolean isNotifyOnAborted() {
        return notifyOnAborted;
    }

    @DataBoundSetter
    public void setNotifyOnAborted(boolean notifyOnAborted) {
        this.notifyOnAborted = notifyOnAborted;
    }

    public boolean isNotifyOnNotBuilt() {
        return notifyOnNotBuilt;
    }

    @DataBoundSetter
    public void setNotifyOnNotBuilt(boolean notifyOnNotBuilt) {
        this.notifyOnNotBuilt = notifyOnNotBuilt;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    @DataBoundSetter
    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        Result result = build.getResult();

        // Vérifie si une notification doit être envoyée pour ce résultat de build
        // selon les triggers configurés par l'utilisateur (Success, Failure, etc.)
        Set<NotificationTrigger> triggers = getConfiguredTriggers();
        if (!NotificationTrigger.shouldNotify(triggers, result)) {
            listener.getLogger().println("Telegram Notifier: No notification needed for build result: " + result);
            return true;
        }

        // Récupère les credentials depuis le Jenkins Credentials Store
        // Les valeurs sont chiffrées au repos et ne sont jamais loggées
        String botToken = retrieveCredential(telegramTokenCredentialId, build.getProject());
        String chatId = retrieveCredential(telegramChatIdCredentialId, build.getProject());

        if (botToken == null || botToken.trim().isEmpty()) {
            listener.error("Telegram Notifier: Bot token credential not found or empty");
            LOGGER.log(Level.WARNING, "Bot token credential not found for ID: {0}", telegramTokenCredentialId);
            // Ne jamais faire échouer le build à cause d'un problème de notification
            return true;
        }

        if (chatId == null || chatId.trim().isEmpty()) {
            listener.error("Telegram Notifier: Chat ID credential not found or empty");
            LOGGER.log(Level.WARNING, "Chat ID credential not found for ID: {0}", telegramChatIdCredentialId);
            // Ne jamais faire échouer le build à cause d'un problème de notification
            return true;
        }

        // Formate le message avec les informations du build et le message personnalisé
        String message = MessageFormatter.formatMessage(build, customMessage);

        // Envoie la notification via l'API Telegram
        listener.getLogger().println("Telegram Notifier: Sending notification...");
        TelegramSender sender = new TelegramSender();
        boolean success = sender.sendMessage(botToken, chatId, message);

        if (success) {
            listener.getLogger().println("Telegram Notifier: Notification sent successfully");
        } else {
            listener.error("Telegram Notifier: Failed to send notification");
        }

        // Retourne toujours true pour ne pas faire échouer le build
        return true;
    }

    /**
     * Obtient l'ensemble des déclencheurs de notification configurés.
     *
     * @return l'ensemble des déclencheurs
     */
    private Set<NotificationTrigger> getConfiguredTriggers() {
        Set<NotificationTrigger> triggers = new HashSet<>();

        if (notifyOnSuccess) {
            triggers.add(NotificationTrigger.SUCCESS);
        }
        if (notifyOnFailure) {
            triggers.add(NotificationTrigger.FAILURE);
        }
        if (notifyOnUnstable) {
            triggers.add(NotificationTrigger.UNSTABLE);
        }
        if (notifyOnAborted) {
            triggers.add(NotificationTrigger.ABORTED);
        }
        if (notifyOnNotBuilt) {
            triggers.add(NotificationTrigger.NOT_BUILT);
        }

        return triggers;
    }

    /**
     * Récupère la valeur d'un credential par son ID.
     *
     * @param credentialId l'ID du credential
     * @param project      le contexte du projet
     * @return la valeur du credential, ou null si non trouvé
     */
    private String retrieveCredential(String credentialId, AbstractProject<?, ?> project) {
        if (credentialId == null || credentialId.trim().isEmpty()) {
            return null;
        }

        List<StringCredentials> credentials = CredentialsProvider.lookupCredentials(
                StringCredentials.class,
                project,
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()
        );

        StringCredentials credential = CredentialsMatchers.firstOrNull(
                credentials,
                CredentialsMatchers.withId(credentialId)
        );

        return credential != null ? credential.getSecret().getPlainText() : null;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Descripteur pour TelegramNotifier.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Telegram Notifier";
        }

        /**
         * Remplit le menu déroulant pour les credentials du token du bot.
         */
        @POST
        public ListBoxModel doFillTelegramTokenCredentialIdItems(
                @AncestorInPath Item context,
                @QueryParameter String telegramTokenCredentialId) {

            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel().includeCurrentValue(telegramTokenCredentialId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, context, StringCredentials.class)
                    .includeCurrentValue(telegramTokenCredentialId);
        }

        /**
         * Remplit le menu déroulant pour les credentials de l'ID du chat.
         */
        @POST
        public ListBoxModel doFillTelegramChatIdCredentialIdItems(
                @AncestorInPath Item context,
                @QueryParameter String telegramChatIdCredentialId) {

            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel().includeCurrentValue(telegramChatIdCredentialId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, context, StringCredentials.class)
                    .includeCurrentValue(telegramChatIdCredentialId);
        }

        /**
         * Valide la sélection du credential du token du bot.
         */
        @POST
        public FormValidation doCheckTelegramTokenCredentialId(
                @AncestorInPath Item context,
                @QueryParameter String value) {

            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Veuillez sélectionner un credential de token du bot");
            }

            return FormValidation.ok();
        }

        /**
         * Valide la sélection du credential de l'ID du chat.
         */
        @POST
        public FormValidation doCheckTelegramChatIdCredentialId(
                @AncestorInPath Item context,
                @QueryParameter String value) {

            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Veuillez sélectionner un credential d'ID du chat");
            }

            return FormValidation.ok();
        }
    }
}
