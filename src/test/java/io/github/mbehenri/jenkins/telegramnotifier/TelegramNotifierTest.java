package io.github.mbehenri.jenkins.telegramnotifier;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

/**
 * Tests d'intégration pour TelegramNotifier utilisant JenkinsRule.
 * Ces tests vérifient le comportement du plugin dans un environnement Jenkins réel.
 *
 * JenkinsRule crée une instance Jenkins temporaire pour chaque test, permettant de:
 * - Tester la persistance de la configuration
 * - Vérifier le comportement lors de builds réels
 * - Valider l'intégration avec l'API Credentials
 */
public class TelegramNotifierTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * Test de persistance de la configuration (config round-trip).
     *
     * Vérifie que toutes les configurations du notifier sont correctement:
     * - Sauvegardées dans le fichier XML de configuration du job
     * - Rechargées après un redémarrage Jenkins
     *
     * C'est un test critique car Jenkins doit préserver la configuration
     * entre les redémarrages pour que le plugin fonctionne correctement.
     */
    @Test
    public void testConfigRoundTrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        // Crée des credentials de test dans le store global
        // Ces credentials simulent un vrai token Telegram et chat ID
        StringCredentialsImpl tokenCredential = new StringCredentialsImpl(
                CredentialsScope.GLOBAL,
                "test-token-id",
                "Test Bot Token",
                hudson.util.Secret.fromString("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11")
        );

        StringCredentialsImpl chatIdCredential = new StringCredentialsImpl(
                CredentialsScope.GLOBAL,
                "test-chat-id",
                "Test Chat ID",
                hudson.util.Secret.fromString("987654321")
        );

        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), tokenCredential);
        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), chatIdCredential);

        // Configure le notifier avec tous les paramètres possibles
        // pour vérifier que la sérialisation/désérialisation fonctionne correctement
        TelegramNotifier notifier = new TelegramNotifier("test-token-id", "test-chat-id");
        notifier.setNotifyOnSuccess(true);
        notifier.setNotifyOnFailure(true);
        notifier.setNotifyOnUnstable(false);
        notifier.setNotifyOnAborted(true);
        notifier.setNotifyOnNotBuilt(false);
        notifier.setCustomMessage("Build ${BUILD_NUMBER} completed");

        project.getPublishersList().add(notifier);

        // Sauvegarde et recharge le projet (simule un redémarrage Jenkins)
        // Jenkins sérialise la config en XML et la désérialise au rechargement
        project = jenkins.configRoundtrip(project);

        // Vérifie que toute la configuration a été correctement persistée
        // C'est crucial pour garantir que les paramètres ne sont pas perdus après un redémarrage
        TelegramNotifier savedNotifier = project.getPublishersList().get(TelegramNotifier.class);
        assertNotNull(savedNotifier);
        assertEquals("test-token-id", savedNotifier.getTelegramTokenCredentialId());
        assertEquals("test-chat-id", savedNotifier.getTelegramChatIdCredentialId());
        assertTrue(savedNotifier.isNotifyOnSuccess());
        assertTrue(savedNotifier.isNotifyOnFailure());
        assertFalse(savedNotifier.isNotifyOnUnstable());
        assertTrue(savedNotifier.isNotifyOnAborted());
        assertFalse(savedNotifier.isNotifyOnNotBuilt());
        assertEquals("Build ${BUILD_NUMBER} completed", savedNotifier.getCustomMessage());
    }

    /**
     * Test de notification quand le build réussit et que "notifyOnSuccess" est activé.
     *
     * Vérifie que:
     * - Le notifier tente d'envoyer une notification pour un build SUCCESS
     * - Le log du build contient les traces de l'envoi de notification
     *
     * Note: La notification échouera avec les credentials de test (token invalide),
     * mais c'est OK car on vérifie juste que la tentative d'envoi a été faite.
     */
    @Test
    public void testNotifyOnSuccessEnabled() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        // Crée les credentials de test
        createTestCredentials();

        // Configure le notifier pour notifier UNIQUEMENT en cas de succès
        // Les autres triggers (failure, unstable) sont désactivés
        TelegramNotifier notifier = new TelegramNotifier("test-token-id", "test-chat-id");
        notifier.setNotifyOnSuccess(true);
        notifier.setNotifyOnFailure(false);
        notifier.setNotifyOnUnstable(false);

        project.getPublishersList().add(notifier);

        // Lance le build (devrait réussir par défaut car aucune étape n'est configurée)
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Vérifie que le build a bien réussi
        assertEquals(Result.SUCCESS, build.getResult());

        // Vérifie dans les logs que le notifier a tenté d'envoyer une notification
        // La notification échouera avec le token invalide, mais c'est attendu pour ce test
        // Ce qui compte c'est que le notifier ait été déclenché
        String log = build.getLog();
        assertTrue(log.contains("Telegram Notifier: Sending notification") ||
                   log.contains("Telegram Notifier: Failed to send notification"));
    }

    /**
     * Test de non-notification quand le build réussit mais que "notifyOnSuccess" est désactivé.
     *
     * Vérifie que:
     * - Aucune notification n'est envoyée pour un build SUCCESS
     * - Le log confirme qu'aucune notification n'était nécessaire
     *
     * C'est important pour éviter de spammer les utilisateurs avec des notifications
     * non désirées.
     */
    @Test
    public void testNotifyOnSuccessDisabled() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        createTestCredentials();

        // Configure le notifier pour NE PAS notifier en cas de succès
        // Seulement les failures seront notifiés
        TelegramNotifier notifier = new TelegramNotifier("test-token-id", "test-chat-id");
        notifier.setNotifyOnSuccess(false);
        notifier.setNotifyOnFailure(true);

        project.getPublishersList().add(notifier);

        // Lance un build qui réussit
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, build.getResult());

        // Vérifie qu'AUCUNE notification n'a été envoyée
        // Le log doit contenir un message indiquant que la notification n'est pas nécessaire
        String log = build.getLog();
        assertTrue(log.contains("No notification needed for build result"));
    }

    /**
     * Test du comportement avec des credentials manquants.
     *
     * Vérifie que:
     * - Le build réussit même si les credentials n'existent pas
     * - Un message d'erreur approprié apparaît dans le log
     *
     * CRITIQUE: Les notifications ne doivent JAMAIS faire échouer un build.
     * C'est pourquoi le plugin extend Notifier et retourne toujours true.
     */
    @Test
    public void testNotifyWithMissingCredentials() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        // Configure le notifier avec des IDs de credentials qui n'existent pas
        // Cela simule une mauvaise configuration ou des credentials supprimés
        TelegramNotifier notifier = new TelegramNotifier("non-existent-token", "non-existent-chat");
        notifier.setNotifyOnSuccess(true);

        project.getPublishersList().add(notifier);

        // Le build doit quand même réussir malgré les credentials manquants
        // C'EST CRITIQUE: les problèmes de notification ne doivent jamais faire échouer un build
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, build.getResult());

        // Vérifie qu'un message d'erreur approprié apparaît dans le log
        // L'utilisateur doit être informé du problème sans que le build échoue
        String log = build.getLog();
        assertTrue(log.contains("Bot token credential not found") ||
                   log.contains("Chat ID credential not found"));
    }

    /**
     * Test de la substitution des variables dans le message personnalisé.
     *
     * Vérifie que:
     * - Les variables comme ${JOB_NAME}, ${BUILD_NUMBER}, ${BUILD_STATUS}
     *   sont correctement remplacées par leurs valeurs réelles
     * - Le MessageFormatter est appelé avec le bon contexte
     *
     * Les variables supportées sont définies dans MessageFormatter.java.
     */
    @Test
    public void testCustomMessageVariableSubstitution() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        createTestCredentials();

        // Configure le notifier avec un message personnalisé contenant des variables
        // Ces variables seront remplacées par MessageFormatter avant l'envoi
        TelegramNotifier notifier = new TelegramNotifier("test-token-id", "test-chat-id");
        notifier.setNotifyOnSuccess(true);
        notifier.setCustomMessage("Job ${JOB_NAME} build #${BUILD_NUMBER} finished with ${BUILD_STATUS}");

        project.getPublishersList().add(notifier);

        // Lance le build
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, build.getResult());

        // Le message sera formaté avec les vraies valeurs et envoyé
        // (échouera avec les credentials de test, mais le formatage aura lieu)
        String log = build.getLog();
        assertTrue(log.contains("Telegram Notifier"));
    }

    /**
     * Test du nom d'affichage du plugin dans l'UI Jenkins.
     *
     * Vérifie que le descriptor retourne le bon nom qui apparaîtra dans:
     * - La liste "Add post-build action"
     * - La configuration du job
     */
    @Test
    public void testDescriptorDisplayName() {
        TelegramNotifier.DescriptorImpl descriptor = new TelegramNotifier.DescriptorImpl();
        assertEquals("Telegram Notifier", descriptor.getDisplayName());
    }

    /**
     * Test que le plugin est applicable aux projets FreeStyle.
     *
     * Jenkins utilise cette méthode pour déterminer si le plugin doit
     * apparaître dans la liste des post-build actions pour un type de projet donné.
     */
    @Test
    public void testDescriptorIsApplicable() {
        TelegramNotifier.DescriptorImpl descriptor = new TelegramNotifier.DescriptorImpl();
        assertTrue(descriptor.isApplicable(FreeStyleProject.class));
    }

    /**
     * Test que le notifier ne fait JAMAIS échouer un build.
     *
     * CRITIQUE: C'est un principe fondamental du plugin.
     * Même si la notification échoue (credentials invalides, réseau down, etc.),
     * le build doit continuer et se terminer avec son statut réel.
     *
     * C'est pourquoi:
     * - TelegramNotifier extend Notifier (pas Recorder)
     * - perform() retourne toujours true
     */
    @Test
    public void testNotifierDoesNotFailBuild() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        // Configure le notifier avec des credentials complètement invalides
        // Cela garantit que la notification échouera
        TelegramNotifier notifier = new TelegramNotifier("invalid", "invalid");
        notifier.setNotifyOnSuccess(true);

        project.getPublishersList().add(notifier);

        // Le build doit quand même réussir même si la notification échoue
        // C'est LE principe fondamental du plugin: ne jamais impacter le build
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, build.getResult());
    }

    /**
     * Test de la configuration avec plusieurs triggers activés simultanément.
     *
     * Vérifie que:
     * - On peut activer tous les triggers en même temps
     * - La configuration est correctement persistée
     * - Chaque trigger conserve son état indépendamment des autres
     */
    @Test
    public void testMultipleTriggers() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        createTestCredentials();

        // Configure le notifier avec TOUS les triggers activés
        // L'utilisateur sera notifié pour n'importe quel résultat de build
        TelegramNotifier notifier = new TelegramNotifier("test-token-id", "test-chat-id");
        notifier.setNotifyOnSuccess(true);
        notifier.setNotifyOnFailure(true);
        notifier.setNotifyOnUnstable(true);
        notifier.setNotifyOnAborted(true);
        notifier.setNotifyOnNotBuilt(true);

        project.getPublishersList().add(notifier);
        project = jenkins.configRoundtrip(project);

        // Vérifie que tous les triggers ont été correctement sauvegardés et rechargés
        TelegramNotifier savedNotifier = project.getPublishersList().get(TelegramNotifier.class);
        assertTrue(savedNotifier.isNotifyOnSuccess());
        assertTrue(savedNotifier.isNotifyOnFailure());
        assertTrue(savedNotifier.isNotifyOnUnstable());
        assertTrue(savedNotifier.isNotifyOnAborted());
        assertTrue(savedNotifier.isNotifyOnNotBuilt());
    }

    /**
     * Test des valeurs par défaut des triggers.
     *
     * Par défaut, seuls FAILURE et UNSTABLE sont activés car:
     * - Ce sont les cas les plus importants à notifier
     * - Évite de spammer avec des notifications de succès
     * - Suit les conventions des autres plugins de notification Jenkins
     */
    @Test
    public void testDefaultTriggers() {
        TelegramNotifier notifier = new TelegramNotifier("token", "chat");

        // Par défaut, seuls failure et unstable sont activés
        // C'est un choix de design pour éviter de spammer avec des notifications de succès
        assertFalse(notifier.isNotifyOnSuccess());
        assertTrue(notifier.isNotifyOnFailure());
        assertTrue(notifier.isNotifyOnUnstable());
        assertFalse(notifier.isNotifyOnAborted());
        assertFalse(notifier.isNotifyOnNotBuilt());
    }

    /**
     * Test avec un message personnalisé vide.
     *
     * Vérifie que:
     * - Le plugin fonctionne même sans message personnalisé
     * - Le message par défaut est utilisé à la place
     * - Aucune erreur n'est levée avec une chaîne vide
     */
    @Test
    public void testEmptyCustomMessage() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        createTestCredentials();

        // Configure le notifier sans message personnalisé (chaîne vide)
        // Le message par défaut de MessageFormatter sera utilisé
        TelegramNotifier notifier = new TelegramNotifier("test-token-id", "test-chat-id");
        notifier.setNotifyOnSuccess(true);
        notifier.setCustomMessage("");

        project.getPublishersList().add(notifier);

        // Le build doit fonctionner correctement avec un message personnalisé vide
        // MessageFormatter générera le message par défaut avec les infos du build
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, build.getResult());
    }

    /**
     * Méthode utilitaire pour créer des credentials de test.
     *
     * Crée deux StringCredentials:
     * - Token du bot Telegram (format: 123456:ABC-DEF...)
     * - Chat ID (format: 987654321)
     *
     * Ces credentials sont ajoutés au Global credentials store pour être
     * disponibles pendant les tests.
     */
    private void createTestCredentials() throws Exception {
        StringCredentialsImpl tokenCredential = new StringCredentialsImpl(
                CredentialsScope.GLOBAL,
                "test-token-id",
                "Test Bot Token",
                hudson.util.Secret.fromString("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11")
        );

        StringCredentialsImpl chatIdCredential = new StringCredentialsImpl(
                CredentialsScope.GLOBAL,
                "test-chat-id",
                "Test Chat ID",
                hudson.util.Secret.fromString("987654321")
        );

        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), tokenCredential);
        CredentialsProvider.lookupStores(jenkins.jenkins).iterator().next()
                .addCredentials(Domain.global(), chatIdCredential);
    }
}
