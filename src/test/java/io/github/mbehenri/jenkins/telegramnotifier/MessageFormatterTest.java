package io.github.mbehenri.jenkins.telegramnotifier;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Result;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour MessageFormatter.
 *
 * Ces tests vérifient:
 * - Le formatage des messages de notification
 * - La substitution des variables (${BUILD_STATUS}, ${JOB_NAME}, etc.)
 * - La présence des emojis selon le statut
 * - La troncature des messages longs
 * - L'échappement des caractères Markdown spéciaux
 *
 * Utilise Mockito pour mocker les objets Jenkins (AbstractBuild, AbstractProject)
 * car on ne veut pas dépendre d'une instance Jenkins réelle pour ces tests unitaires.
 */
public class MessageFormatterTest {

    @Mock
    private AbstractBuild<?, ?> build;

    @Mock
    private AbstractProject<?, ?> project;

    /**
     * Configuration des mocks avant chaque test.
     *
     * Configure un build de test avec des valeurs réalistes:
     * - Job: TestJob
     * - Build number: 42
     * - Duration: 65 secondes (1m 5s)
     * - URL: http://jenkins.example.com/job/TestJob/42/
     * - Cause: Started by user admin
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(build.getProject()).thenReturn((AbstractProject) project);
        when(project.getFullDisplayName()).thenReturn("TestJob");
        when(build.getNumber()).thenReturn(42);
        when(build.getDuration()).thenReturn(65000L); // 1m 5s
        when(build.getAbsoluteUrl()).thenReturn("http://jenkins.example.com/job/TestJob/42/");

        List<Cause> causes = new ArrayList<>();
        Cause.UserIdCause userCause = mock(Cause.UserIdCause.class);
        when(userCause.getShortDescription()).thenReturn("Started by user admin");
        causes.add(userCause);
        when(build.getCauses()).thenReturn(causes);
    }

    /**
     * Test du formatage d'un message pour un build SUCCESS.
     *
     * Vérifie que le message contient:
     * - Le statut en gras: *SUCCESS*
     * - Le nom du job
     * - Le numéro de build
     * - La durée formatée
     * - La cause du déclenchement
     * - Le lien vers le build
     */
    @Test
    public void testFormatMessageWithSuccess() {
        when(build.getResult()).thenReturn(Result.SUCCESS);

        String message = MessageFormatter.formatMessage(build, null);

        assertTrue(message.contains("Build *SUCCESS*"));
        assertTrue(message.contains("*Job:* TestJob"));
        assertTrue(message.contains("*Build:* #42"));
        assertTrue(message.contains("*Duration:* 1m 5s"));
        assertTrue(message.contains("*Started by:* Started by user admin"));
        assertTrue(message.contains("[View build](http://jenkins.example.com/job/TestJob/42/)"));
    }

    /**
     * Test du formatage d'un message pour un build FAILURE.
     *
     * Vérifie que le statut FAILURE est correctement affiché.
     */
    @Test
    public void testFormatMessageWithFailure() {
        when(build.getResult()).thenReturn(Result.FAILURE);

        String message = MessageFormatter.formatMessage(build, null);

        assertTrue(message.contains("Build *FAILURE*"));
        assertTrue(message.contains("TestJob"));
    }

    /**
     * Test de la substitution des variables dans un message personnalisé.
     *
     * Le message personnalisé peut contenir des variables comme:
     * - ${BUILD_NUMBER}
     * - ${JOB_NAME}
     * - ${BUILD_STATUS}
     *
     * Ces variables doivent être remplacées par leurs valeurs réelles.
     */
    @Test
    public void testFormatMessageWithCustomMessage() {
        when(build.getResult()).thenReturn(Result.SUCCESS);

        String customMessage = "Build ${BUILD_NUMBER} of ${JOB_NAME} completed with status ${BUILD_STATUS}";
        String message = MessageFormatter.formatMessage(build, customMessage);

        assertTrue(message.contains("Build 42 of TestJob completed with status SUCCESS"));
    }

    /**
     * Test de la substitution de TOUTES les variables disponibles.
     *
     * Variables supportées:
     * - ${BUILD_STATUS}: Le résultat du build (SUCCESS, FAILURE, etc.)
     * - ${JOB_NAME}: Le nom du job
     * - ${BUILD_NUMBER}: Le numéro du build
     * - ${BUILD_DURATION}: La durée formatée (ex: 1m 5s)
     * - ${BUILD_URL}: L'URL du build
     * - ${CAUSE}: La cause du déclenchement
     */
    @Test
    public void testFormatMessageWithAllVariables() {
        when(build.getResult()).thenReturn(Result.SUCCESS);

        String customMessage = "Status: ${BUILD_STATUS}, Job: ${JOB_NAME}, " +
                "Build: ${BUILD_NUMBER}, Duration: ${BUILD_DURATION}, " +
                "URL: ${BUILD_URL}, Cause: ${CAUSE}";

        String message = MessageFormatter.formatMessage(build, customMessage);

        assertTrue(message.contains("Status: SUCCESS"));
        assertTrue(message.contains("Job: TestJob"));
        assertTrue(message.contains("Build: 42"));
        assertTrue(message.contains("Duration: 1m 5s"));
        assertTrue(message.contains("URL: http://jenkins.example.com/job/TestJob/42/"));
        assertTrue(message.contains("Cause: Started by user admin"));
    }

    /**
     * Test du comportement avec un build null.
     *
     * Doit retourner un message d'erreur gracieux au lieu de crasher.
     */
    @Test
    public void testFormatMessageWithNullBuild() {
        String message = MessageFormatter.formatMessage(null, null);
        assertEquals("Invalid build information", message);
    }

    /**
     * Test du formatage des durées en format lisible.
     *
     * Formats attendus:
     * - 5000ms → "5s"
     * - 65000ms → "1m 5s"
     * - 3723000ms → "1h 2m 3s"
     * - -1ms → "N/A" (durée invalide)
     */
    @Test
    public void testFormatDuration() {
        assertEquals("5s", MessageFormatter.formatDuration(5000));
        assertEquals("1m 5s", MessageFormatter.formatDuration(65000));
        assertEquals("1h 2m 3s", MessageFormatter.formatDuration(3723000));
        assertEquals("N/A", MessageFormatter.formatDuration(-1));
    }

    /**
     * Test du formatage d'une durée de 0 seconde.
     */
    @Test
    public void testFormatDurationZero() {
        assertEquals("0s", MessageFormatter.formatDuration(0));
    }

    /**
     * Test du formatage d'une durée contenant uniquement des minutes.
     *
     * 2 minutes = 120000ms → "2m 0s"
     */
    @Test
    public void testFormatDurationWithOnlyMinutes() {
        assertEquals("2m 0s", MessageFormatter.formatDuration(120000));
    }

    /**
     * Test du formatage d'une durée contenant uniquement des heures.
     *
     * 2 heures = 7200000ms → "2h 0m 0s"
     */
    @Test
    public void testFormatDurationWithOnlyHours() {
        assertEquals("2h 0m 0s", MessageFormatter.formatDuration(7200000));
    }

    /**
     * Test de l'échappement des underscores dans les noms de jobs.
     *
     * En Markdown, _ est utilisé pour l'italique, donc il faut échapper
     * les underscores dans les noms de jobs avec \_
     *
     * Exemple: "Test_Job_With_Underscores" → "Test\_Job\_With\_Underscores"
     */
    @Test
    public void testMessageWithUnderscoreInJobName() {
        when(project.getFullDisplayName()).thenReturn("Test_Job_With_Underscores");
        when(build.getResult()).thenReturn(Result.SUCCESS);

        String message = MessageFormatter.formatMessage(build, null);

        // Les underscores doivent être échappés car ils ont une signification spéciale en Markdown
        // Telegram interprète _ comme début/fin d'italique, donc "My_Job" deviendrait "My<italic>Job"
        // L'échappement produit "My\_Job" qui s'affiche correctement
        assertTrue(message.contains("Test\\_Job\\_With\\_Underscores"));
    }

    /**
     * Test de la troncature des messages trop longs.
     *
     * Telegram limite les messages à 4096 caractères.
     * Si le message dépasse cette limite, il doit être tronqué avec
     * un indicateur "(message truncated)" à la fin.
     *
     * C'est important pour éviter les erreurs 400 Bad Request de l'API Telegram.
     */
    @Test
    public void testMessageTruncation() {
        when(build.getResult()).thenReturn(Result.SUCCESS);

        // Crée un message personnalisé très long pour tester la troncature
        // Environ 30000 caractères, bien au-delà de la limite Telegram de 4096
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longMessage.append("This is a very long message. ");
        }

        String message = MessageFormatter.formatMessage(build, longMessage.toString());

        // Le message doit être tronqué à la longueur maximale Telegram (4096 caractères)
        assertTrue(message.length() <= 4096);
        if (message.length() == 4096) {
            // Doit contenir l'indicateur de troncature à la fin
            assertTrue(message.contains("(message truncated)"));
        }
    }

    /**
     * Test de la présence des emojis appropriés selon le statut.
     *
     * Chaque statut a son emoji:
     * - SUCCESS: ✅ (U+2705)
     * - FAILURE: ❌ (U+274C)
     * - UNSTABLE: ⚠️ (U+26A0)
     * - ABORTED: 🛑 (U+1F6D1)
     * - NOT_BUILT: ⏸️ (U+23F8)
     *
     * Les emojis permettent une identification visuelle rapide du statut.
     */
    @Test
    public void testEmojiPresence() {
        // Test SUCCESS: doit contenir ✅ (U+2705)
        when(build.getResult()).thenReturn(Result.SUCCESS);
        String successMessage = MessageFormatter.formatMessage(build, null);
        assertTrue(successMessage.contains("\u2705")); // ✅

        // Test FAILURE: doit contenir ❌ (U+274C)
        when(build.getResult()).thenReturn(Result.FAILURE);
        String failureMessage = MessageFormatter.formatMessage(build, null);
        assertTrue(failureMessage.contains("\u274C")); // ❌

        // Test UNSTABLE: doit contenir ⚠️ (U+26A0)
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        String unstableMessage = MessageFormatter.formatMessage(build, null);
        assertTrue(unstableMessage.contains("\u26A0")); // ⚠️

        // Test ABORTED: doit contenir 🛑 (U+1F6D1)
        when(build.getResult()).thenReturn(Result.ABORTED);
        String abortedMessage = MessageFormatter.formatMessage(build, null);
        assertTrue(abortedMessage.contains("\uD83D\uDED1")); // 🛑

        // Test NOT_BUILT: doit contenir ⏸️ (U+23F8)
        when(build.getResult()).thenReturn(Result.NOT_BUILT);
        String notBuiltMessage = MessageFormatter.formatMessage(build, null);
        assertTrue(notBuiltMessage.contains("\u23F8")); // ⏸️
    }
}
