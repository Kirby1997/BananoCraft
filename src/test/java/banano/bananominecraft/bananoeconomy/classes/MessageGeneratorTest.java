package banano.bananominecraft.bananoeconomy.classes;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageGeneratorTest
{
    private static final String HASH = "ABC123HASH";
    private static final String GOOD_BLOCK   = "https://creeper.banano.cc/explorer/block/";
    private static final String GOOD_ACCOUNT = "https://creeper.banano.cc/explorer/account/";
    private static final String MALFORMED    = "notaurl-no-protocol/"; // new URL(...) throws

    private ConfigEngine configEngine;
    private MessageGenerator generator;

    @BeforeEach
    void setUp()
    {
        I18n i18n = new I18n(getClass().getClassLoader());
        configEngine = mock(ConfigEngine.class);
        generator = new MessageGenerator(i18n, configEngine);
    }

    // --- block explorer link (locale overload) ---

    @Test
    void blockExplorerLink_opensUrl_whenExplorerValid()
    {
        when(configEngine.getExplorerBlock()).thenReturn(GOOD_BLOCK);

        TextComponent link = generator.generateBlockExplorerLink(Locale.ENGLISH, HASH);

        assertEquals(ClickEvent.Action.OPEN_URL, link.getClickEvent().getAction());
        assertEquals(GOOD_BLOCK + HASH, link.getClickEvent().getValue());
        assertTrue(link.isUnderlined());
    }

    @Test
    void blockExplorerLink_fallsBackToClipboard_whenExplorerMalformed()
    {
        when(configEngine.getExplorerBlock()).thenReturn(MALFORMED);

        TextComponent link = generator.generateBlockExplorerLink(Locale.ENGLISH, HASH);

        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, link.getClickEvent().getAction());
        assertEquals(HASH, link.getClickEvent().getValue());
    }

    // --- block explorer link (custom-text overload) ---

    @Test
    void blockExplorerLinkCustomText_opensUrl_whenValid()
    {
        when(configEngine.getExplorerBlock()).thenReturn(GOOD_BLOCK);

        TextComponent link = generator.generateBlockExplorerLink(HASH, "abc...");

        assertEquals("abc...", link.getText());
        assertEquals(ClickEvent.Action.OPEN_URL, link.getClickEvent().getAction());
        assertTrue(link.isUnderlined());
    }

    @Test
    void blockExplorerLinkCustomText_fallsBackToClipboard_whenMalformed()
    {
        when(configEngine.getExplorerBlock()).thenReturn(MALFORMED);

        TextComponent link = generator.generateBlockExplorerLink(HASH, "abc...");

        assertEquals("abc...", link.getText());
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, link.getClickEvent().getAction());
        assertEquals(HASH, link.getClickEvent().getValue());
    }

    // --- offline payments link ---

    @Test
    void clickToViewOfflinePayments_runsCommand()
    {
        TextComponent link = generator.generateClickToViewOfflinePayments(Locale.ENGLISH);

        assertEquals(ClickEvent.Action.RUN_COMMAND, link.getClickEvent().getAction());
        assertEquals("/bananoeconomy:showofflinetips", link.getClickEvent().getValue());
        assertTrue(link.isUnderlined());
    }

    // --- clickable address (deposit) ---

    @Test
    void clickableAddress_includesClipboardAndExplorer_whenValid()
    {
        when(configEngine.getExplorerAccount()).thenReturn(GOOD_ACCOUNT);

        TextComponent msg = generator.generateClickableAddressMessage("Deposit here:", "ban_wallet");

        // First extra: the clipboard-copy wallet; second extra: the explorer link.
        assertEquals(2, msg.getExtra().size());
        TextComponent clipboard = (TextComponent) msg.getExtra().get(0);
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, clipboard.getClickEvent().getAction());
        assertEquals("ban_wallet", clipboard.getClickEvent().getValue());

        TextComponent explorer = (TextComponent) msg.getExtra().get(1);
        assertEquals(ClickEvent.Action.OPEN_URL, explorer.getClickEvent().getAction());
        assertEquals(GOOD_ACCOUNT + "ban_wallet", explorer.getClickEvent().getValue());
    }

    @Test
    void clickableAddress_omitsExplorer_whenAccountMalformed()
    {
        when(configEngine.getExplorerAccount()).thenReturn(MALFORMED);

        TextComponent msg = generator.generateClickableAddressMessage("Deposit here:", "ban_wallet");

        // Only the clipboard-copy extra remains; the explorer link is skipped.
        assertEquals(1, msg.getExtra().size());
        TextComponent clipboard = (TextComponent) msg.getExtra().get(0);
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, clipboard.getClickEvent().getAction());
    }

    // --- tip messages: note vs no-note key selection ---

    @Test
    void tipReceiverMessage_withNote_includesNoteText()
    {
        BaseComponent[] comps = generator.generateTipReceiverMessage(
                Locale.ENGLISH, "Alice", 1.5, HASH, "thanks!");

        assertTrue(TextComponent.toLegacyText(comps).contains("thanks!"));
        assertTrue(TextComponent.toLegacyText(comps).contains("Alice"));
    }

    @Test
    void tipReceiverMessage_withoutNote_omitsNoteText()
    {
        BaseComponent[] comps = generator.generateTipReceiverMessage(
                Locale.ENGLISH, "Alice", 1.5, HASH, "");

        assertFalse(TextComponent.toLegacyText(comps).contains("attached message"));
    }

    @Test
    void tipReceiverMessage_fromRecord_delegates()
    {
        OfflinePaymentRecord record = new OfflinePaymentRecord(
                UUID.randomUUID(), "Bob", 2.0, HASH, java.time.LocalDateTime.now(), "hi");

        BaseComponent[] comps = generator.generateTipReceiverMessage(Locale.ENGLISH, record);

        String text = TextComponent.toLegacyText(comps);
        assertTrue(text.contains("Bob"));
        assertTrue(text.contains("hi"));
    }

    @Test
    void tipSenderMessage_withNote_includesNoteText()
    {
        BaseComponent[] comps = generator.generateTipSenderMessage(
                Locale.ENGLISH, "Bob", 3.0, HASH, "for you");

        assertTrue(TextComponent.toLegacyText(comps).contains("for you"));
    }
}
