package io.github.mbehenri.jenkins.telegramnotifier;

import hudson.model.Result;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests unitaires pour NotificationTrigger.
 *
 * Ces tests vérifient la logique de déclenchement des notifications:
 * - Chaque trigger correspond à un Result spécifique
 * - La méthode matches() identifie correctement les correspondances
 * - La méthode shouldNotify() évalue correctement un ensemble de triggers
 *
 * Les 5 triggers disponibles sont:
 * - SUCCESS: build réussi
 * - FAILURE: build échoué
 * - UNSTABLE: build instable (tests échoués mais compilation OK)
 * - ABORTED: build interrompu manuellement
 * - NOT_BUILT: build non exécuté
 */
public class NotificationTriggerTest {

    /**
     * Test que SUCCESS ne correspond qu'au Result.SUCCESS.
     *
     * Vérifie que SUCCESS matche Result.SUCCESS mais aucun autre résultat.
     */
    @Test
    public void testSuccessMatches() {
        assertTrue(NotificationTrigger.SUCCESS.matches(Result.SUCCESS));
        assertFalse(NotificationTrigger.SUCCESS.matches(Result.FAILURE));
        assertFalse(NotificationTrigger.SUCCESS.matches(Result.UNSTABLE));
        assertFalse(NotificationTrigger.SUCCESS.matches(Result.ABORTED));
        assertFalse(NotificationTrigger.SUCCESS.matches(Result.NOT_BUILT));
    }

    /**
     * Test que FAILURE ne correspond qu'au Result.FAILURE.
     *
     * Le trigger FAILURE est l'un des plus importants car il signale
     * les échecs de compilation ou d'exécution.
     */
    @Test
    public void testFailureMatches() {
        assertTrue(NotificationTrigger.FAILURE.matches(Result.FAILURE));
        assertFalse(NotificationTrigger.FAILURE.matches(Result.SUCCESS));
        assertFalse(NotificationTrigger.FAILURE.matches(Result.UNSTABLE));
        assertFalse(NotificationTrigger.FAILURE.matches(Result.ABORTED));
        assertFalse(NotificationTrigger.FAILURE.matches(Result.NOT_BUILT));
    }

    /**
     * Test que UNSTABLE ne correspond qu'au Result.UNSTABLE.
     *
     * UNSTABLE signifie généralement que:
     * - La compilation a réussi
     * - Mais certains tests ont échoué
     * - Ou des seuils de qualité ne sont pas atteints
     */
    @Test
    public void testUnstableMatches() {
        assertTrue(NotificationTrigger.UNSTABLE.matches(Result.UNSTABLE));
        assertFalse(NotificationTrigger.UNSTABLE.matches(Result.SUCCESS));
        assertFalse(NotificationTrigger.UNSTABLE.matches(Result.FAILURE));
        assertFalse(NotificationTrigger.UNSTABLE.matches(Result.ABORTED));
        assertFalse(NotificationTrigger.UNSTABLE.matches(Result.NOT_BUILT));
    }

    /**
     * Test que ABORTED ne correspond qu'au Result.ABORTED.
     *
     * ABORTED signifie que le build a été interrompu:
     * - Par un utilisateur (bouton Stop)
     * - Par un timeout
     * - Par un shutdown Jenkins
     *
     * C'est pourquoi le plugin utilise Notifier (pas Recorder) pour
     * pouvoir notifier même les builds interrompus.
     */
    @Test
    public void testAbortedMatches() {
        assertTrue(NotificationTrigger.ABORTED.matches(Result.ABORTED));
        assertFalse(NotificationTrigger.ABORTED.matches(Result.SUCCESS));
        assertFalse(NotificationTrigger.ABORTED.matches(Result.FAILURE));
        assertFalse(NotificationTrigger.ABORTED.matches(Result.UNSTABLE));
        assertFalse(NotificationTrigger.ABORTED.matches(Result.NOT_BUILT));
    }

    /**
     * Test que NOT_BUILT ne correspond qu'au Result.NOT_BUILT.
     *
     * NOT_BUILT signifie que le build n'a pas été exécuté:
     * - Condition préalable non satisfaite
     * - Branche désactivée
     * - Skip explicite
     */
    @Test
    public void testNotBuiltMatches() {
        assertTrue(NotificationTrigger.NOT_BUILT.matches(Result.NOT_BUILT));
        assertFalse(NotificationTrigger.NOT_BUILT.matches(Result.SUCCESS));
        assertFalse(NotificationTrigger.NOT_BUILT.matches(Result.FAILURE));
        assertFalse(NotificationTrigger.NOT_BUILT.matches(Result.UNSTABLE));
        assertFalse(NotificationTrigger.NOT_BUILT.matches(Result.ABORTED));
    }

    /**
     * Test que matches() retourne false pour un Result null.
     *
     * Un Result null peut arriver si:
     * - Le build est en cours
     * - Il y a eu une erreur interne Jenkins
     *
     * Aucune notification ne doit être envoyée dans ce cas.
     */
    @Test
    public void testMatchesWithNull() {
        assertFalse(NotificationTrigger.SUCCESS.matches(null));
        assertFalse(NotificationTrigger.FAILURE.matches(null));
        assertFalse(NotificationTrigger.UNSTABLE.matches(null));
        assertFalse(NotificationTrigger.ABORTED.matches(null));
        assertFalse(NotificationTrigger.NOT_BUILT.matches(null));
    }

    /**
     * Test de shouldNotify() avec un seul trigger activé.
     *
     * shouldNotify() est la méthode centrale qui détermine si une notification
     * doit être envoyée en fonction:
     * - Des triggers configurés par l'utilisateur
     * - Du résultat du build
     *
     * Retourne true si AU MOINS UN trigger matche le résultat.
     */
    @Test
    public void testShouldNotifyWithSuccessTrigger() {
        Set<NotificationTrigger> triggers = new HashSet<>();
        triggers.add(NotificationTrigger.SUCCESS);

        assertTrue(NotificationTrigger.shouldNotify(triggers, Result.SUCCESS));
        assertFalse(NotificationTrigger.shouldNotify(triggers, Result.FAILURE));
        assertFalse(NotificationTrigger.shouldNotify(triggers, Result.UNSTABLE));
    }

    /**
     * Test de shouldNotify() avec plusieurs triggers activés.
     *
     * Un utilisateur peut activer plusieurs triggers simultanément,
     * par exemple SUCCESS et FAILURE pour être notifié de tout.
     *
     * shouldNotify() doit retourner true si le résultat matche
     * N'IMPORTE QUEL trigger de l'ensemble (logique OR).
     */
    @Test
    public void testShouldNotifyWithMultipleTriggers() {
        Set<NotificationTrigger> triggers = new HashSet<>();
        triggers.add(NotificationTrigger.SUCCESS);
        triggers.add(NotificationTrigger.FAILURE);

        assertTrue(NotificationTrigger.shouldNotify(triggers, Result.SUCCESS));
        assertTrue(NotificationTrigger.shouldNotify(triggers, Result.FAILURE));
        assertFalse(NotificationTrigger.shouldNotify(triggers, Result.UNSTABLE));
    }

    /**
     * Test de shouldNotify() avec un ensemble de triggers vide.
     *
     * Si aucun trigger n'est activé, aucune notification ne doit être envoyée,
     * quel que soit le résultat du build.
     *
     * Cela peut arriver si l'utilisateur décoche toutes les options.
     */
    @Test
    public void testShouldNotifyWithEmptyTriggers() {
        Set<NotificationTrigger> triggers = new HashSet<>();
        assertFalse(NotificationTrigger.shouldNotify(triggers, Result.SUCCESS));
    }

    /**
     * Test de shouldNotify() avec un ensemble de triggers null.
     *
     * Cas de défense contre les erreurs de configuration.
     * Doit retourner false sans crasher.
     */
    @Test
    public void testShouldNotifyWithNullTriggers() {
        assertFalse(NotificationTrigger.shouldNotify(null, Result.SUCCESS));
    }

    /**
     * Test de shouldNotify() avec un Result null.
     *
     * Même si des triggers sont configurés, on ne peut pas notifier
     * sans résultat de build valide.
     */
    @Test
    public void testShouldNotifyWithNullResult() {
        Set<NotificationTrigger> triggers = new HashSet<>();
        triggers.add(NotificationTrigger.SUCCESS);
        assertFalse(NotificationTrigger.shouldNotify(triggers, null));
    }

    /**
     * Test des noms d'affichage des triggers.
     *
     * Ces noms sont affichés dans l'UI Jenkins (config.jelly).
     * Ils doivent être:
     * - En anglais (langue standard de Jenkins)
     * - Courts et descriptifs
     * - Avec la première lettre en majuscule
     */
    @Test
    public void testDisplayNames() {
        assertEquals("Success", NotificationTrigger.SUCCESS.getDisplayName());
        assertEquals("Failure", NotificationTrigger.FAILURE.getDisplayName());
        assertEquals("Unstable", NotificationTrigger.UNSTABLE.getDisplayName());
        assertEquals("Aborted", NotificationTrigger.ABORTED.getDisplayName());
        assertEquals("Not Built", NotificationTrigger.NOT_BUILT.getDisplayName());
    }
}
