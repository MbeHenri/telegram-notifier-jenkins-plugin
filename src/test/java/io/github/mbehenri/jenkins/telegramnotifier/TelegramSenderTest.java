package io.github.mbehenri.jenkins.telegramnotifier;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests unitaires pour TelegramSender.
 *
 * Ces tests vérifient la validation des paramètres et la robustesse du sender.
 * Note: Ces tests n'utilisent pas de mock HttpClient, donc ils tenteront de vraies
 * connexions (qui échoueront avec des tokens invalides, ce qui est attendu).
 *
 * Pour des tests plus approfondis avec mocked HTTP responses, il faudrait:
 * - Injecter HttpClient via constructeur
 * - Mocker les réponses HTTP avec Mockito
 *
 * Design actuel: TelegramSender est stateless et thread-safe, donc on peut
 * réutiliser la même instance pour tous les tests.
 */
public class TelegramSenderTest {

    private TelegramSender sender;

    @Before
    public void setUp() {
        sender = new TelegramSender();
    }

    /**
     * Test avec un token null.
     *
     * Vérifie que le sender rejette les tokens null sans tenter d'envoi.
     * Important pour éviter les NullPointerException.
     */
    @Test
    public void testSendMessageWithNullToken() {
        boolean result = sender.sendMessage(null, "12345", "Test message");
        assertFalse(result);
    }

    /**
     * Test avec un token vide.
     *
     * Vérifie que les chaînes vides sont rejetées (pas seulement null).
     */
    @Test
    public void testSendMessageWithEmptyToken() {
        boolean result = sender.sendMessage("", "12345", "Test message");
        assertFalse(result);
    }

    /**
     * Test avec un chat ID null.
     *
     * Le chat ID est requis pour identifier le destinataire.
     */
    @Test
    public void testSendMessageWithNullChatId() {
        boolean result = sender.sendMessage("test-token", null, "Test message");
        assertFalse(result);
    }

    /**
     * Test avec un chat ID vide.
     */
    @Test
    public void testSendMessageWithEmptyChatId() {
        boolean result = sender.sendMessage("test-token", "", "Test message");
        assertFalse(result);
    }

    /**
     * Test avec un message null.
     *
     * Impossible d'envoyer une notification sans contenu.
     */
    @Test
    public void testSendMessageWithNullMessage() {
        boolean result = sender.sendMessage("test-token", "12345", null);
        assertFalse(result);
    }

    /**
     * Test avec un message vide.
     *
     * Les messages vides ne devraient pas être envoyés.
     */
    @Test
    public void testSendMessageWithEmptyMessage() {
        boolean result = sender.sendMessage("test-token", "12345", "");
        assertFalse(result);
    }

    /**
     * Test avec un token contenant uniquement des espaces.
     *
     * Les whitespaces doivent être traités comme des valeurs invalides.
     */
    @Test
    public void testSendMessageWithWhitespaceToken() {
        boolean result = sender.sendMessage("   ", "12345", "Test message");
        assertFalse(result);
    }

    /**
     * Test avec un chat ID contenant uniquement des espaces.
     */
    @Test
    public void testSendMessageWithWhitespaceChatId() {
        boolean result = sender.sendMessage("test-token", "   ", "Test message");
        assertFalse(result);
    }

    /**
     * Test avec un message contenant uniquement des espaces.
     */
    @Test
    public void testSendMessageWithWhitespaceMessage() {
        boolean result = sender.sendMessage("test-token", "12345", "   ");
        assertFalse(result);
    }

    /**
     * Test avec un token invalide.
     *
     * Ce test fait une vraie requête HTTP qui échouera avec une erreur 401 Unauthorized.
     * Le sender doit gérer cette erreur gracieusement et retourner false.
     */
    @Test
    public void testSendMessageWithInvalidToken() {
        // Tente d'envoyer une requête avec des credentials invalides
        // Attendu: échec avec 401 ou erreur de connexion
        boolean result = sender.sendMessage("invalid-token-12345", "12345", "Test message");
        assertFalse(result);
    }

    /**
     * Test avec des caractères spéciaux dans le message.
     *
     * Vérifie que les caractères spéciaux sont correctement encodés en URL:
     * - & = ? # % (caractères réservés dans les URLs)
     * - € (caractères unicode)
     *
     * Le sender doit utiliser URLEncoder pour encoder le message.
     * Ne doit pas lever d'exception même avec un token invalide.
     */
    @Test
    public void testSendMessageWithSpecialCharactersInMessage() {
        // Test que les caractères spéciaux sont correctement encodés en URL
        // Ne doit pas lever d'exception même avec un token invalide
        boolean result = sender.sendMessage("test-token", "12345",
                "Test message with special chars: & = ? # % €");
        // Échouera à cause du token invalide, mais ne doit pas lever d'exception
        // L'important est que l'encodage URL ne cause pas d'erreur
        assertFalse(result);
    }

    /**
     * Test avec du formatage Markdown dans le message.
     *
     * Le sender utilise parse_mode=Markdown, donc le message peut contenir:
     * - *Bold* (gras)
     * - _Italic_ (italique)
     * - `Code` (code inline)
     * - [Link](url) (liens)
     *
     * Ces caractères doivent être correctement envoyés à Telegram.
     */
    @Test
    public void testSendMessageWithMarkdownInMessage() {
        // Test le formatage Markdown dans le message
        boolean result = sender.sendMessage("test-token", "12345",
                "*Bold* _Italic_ `Code` [Link](http://example.com)");
        // Échouera à cause du token invalide, mais ne doit pas lever d'exception
        // Le formatage Markdown doit passer à travers l'encodage URL sans problème
        assertFalse(result);
    }

    /**
     * Test avec un message très long proche de la limite Telegram.
     *
     * Telegram limite les messages à 4096 caractères.
     * Ce test vérifie que le sender peut gérer de longs messages sans crasher.
     *
     * Note: La troncature est gérée par MessageFormatter, pas par TelegramSender.
     */
    @Test
    public void testSendMessageWithVeryLongMessage() {
        // Test avec un message proche de la limite Telegram (4096 chars)
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longMessage.append("Test message ");
        }

        boolean result = sender.sendMessage("test-token", "12345", longMessage.toString());
        // Échouera à cause du token invalide, mais ne doit pas lever d'exception
        // Le sender doit pouvoir gérer de grands messages sans crasher
        // (La troncature est gérée en amont par MessageFormatter)
        assertFalse(result);
    }

    /**
     * Test avec des caractères Unicode et emojis.
     *
     * Vérifie le support des:
     * - Emojis (✅ ❌ ⚠️ 🛑)
     * - Caractères chinois (你好)
     * - Caractères arabes (مرحبا)
     *
     * Important car le plugin utilise des emojis pour les statuts de build.
     */
    @Test
    public void testSendMessageWithUnicodeCharacters() {
        // Test Unicode emoji et caractères
        boolean result = sender.sendMessage("test-token", "12345",
                "Test with emoji: ✅ ❌ ⚠️ 🛑 and unicode: 你好 مرحبا");
        // Échouera à cause du token invalide, mais ne doit pas lever d'exception
        // URLEncoder doit correctement encoder les caractères Unicode en UTF-8
        assertFalse(result);
    }

    /**
     * Test avec des retours à la ligne dans le message.
     *
     * Les messages Jenkins contiennent souvent plusieurs lignes.
     * Les \n doivent être correctement encodés et préservés.
     */
    @Test
    public void testSendMessageWithNewlines() {
        // Test message avec des retours à la ligne
        boolean result = sender.sendMessage("test-token", "12345",
                "Line 1\nLine 2\nLine 3");
        // Échouera à cause du token invalide, mais ne doit pas lever d'exception
        // Les \n doivent être encodés en %0A par URLEncoder
        assertFalse(result);
    }

    /**
     * Test de thread-safety pour les builds parallèles.
     *
     * Jenkins peut exécuter plusieurs builds en parallèle, donc TelegramSender
     * doit être thread-safe. Ce test vérifie que plusieurs envois simultanés:
     * - Ne s'interfèrent pas mutuellement
     * - N'ont pas de race conditions
     * - Chaque requête est indépendante
     *
     * Design actuel: TelegramSender est stateless, donc naturellement thread-safe.
     */
    @Test
    public void testThreadSafety() throws InterruptedException {
        // Test que plusieurs envois concurrents ne s'interfèrent pas
        // Jenkins peut exécuter plusieurs builds en parallèle sur différents threads
        final boolean[] results = new boolean[3];

        // Crée 3 threads qui envoient des messages simultanément
        Thread t1 = new Thread(() -> results[0] = sender.sendMessage("token1", "chat1", "msg1"));
        Thread t2 = new Thread(() -> results[1] = sender.sendMessage("token2", "chat2", "msg2"));
        Thread t3 = new Thread(() -> results[2] = sender.sendMessage("token3", "chat3", "msg3"));

        // Lance les 3 threads en parallèle
        t1.start();
        t2.start();
        t3.start();

        // Attend que tous les threads se terminent
        t1.join();
        t2.join();
        t3.join();

        // Tous devraient échouer avec des tokens invalides, mais ne devraient pas s'interférer
        // TelegramSender est stateless donc thread-safe par design
        assertFalse(results[0]);
        assertFalse(results[1]);
        assertFalse(results[2]);
    }
}
