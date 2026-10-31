package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Locale;
import java.util.UUID;

import static org.mockito.Mockito.*;

class TipCommandTest
{
    private static final String SENDER_WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String TARGET_WALLET = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private ConfigEngine configEngine;
    private IDBConnector db;
    private RPC rpc;
    private TaskTracker taskTracker;
    private I18n i18n;
    private MessageGenerator messageGenerator;
    private TipCommand command;

    private PlayerMock alice;
    private PlayerRecord senderRecord;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs     = mock(EconomyFuncs.class);
        configEngine     = mock(ConfigEngine.class);
        db               = mock(IDBConnector.class);
        rpc              = mock(RPC.class);
        taskTracker      = mock(TaskTracker.class);
        i18n             = mock(I18n.class);
        messageGenerator = mock(MessageGenerator.class);

        // Keep the rich-message builders non-null so the success path does not trip an
        // internal NPE before reaching the branch under test.
        when(messageGenerator.generateTipSenderMessage(any(), anyString(), anyDouble(), anyString(), any()))
                .thenReturn(new BaseComponent[0]);
        when(messageGenerator.generateTipReceiverMessage(any(Locale.class), anyString(), anyDouble(), anyString(), any()))
                .thenReturn(new BaseComponent[0]);
        when(messageGenerator.generateBlockExplorerLink(any(Locale.class), anyString()))
                .thenReturn(new TextComponent("link"));

        command = new TipCommand(plugin, economyFuncs, configEngine, db, rpc, taskTracker, i18n, messageGenerator);

        alice = server.addPlayer("Alice");
        senderRecord = new PlayerRecord(alice.getUniqueId().toString(), "Alice", SENDER_WALLET, false);
        when(db.getPlayerRecord(alice)).thenReturn(senderRecord);
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private void runAndDrain(String... args)
    {
        command.onCommand(alice, null, "tip", args);
        server.getScheduler().waitAsyncTasksFinished();
    }

    // --- synchronous guard paths ---

    @Test
    void nonPlayerSender_isRejected() throws TransactionError
    {
        command.onCommand(server.getConsoleSender(), null, "tip", new String[] { "1", "Bob" });
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void noWallet_isRejected() throws TransactionError
    {
        when(db.getPlayerRecord(alice)).thenReturn(null);

        runAndDrain("1", "Bob");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void tooFewArgs_showsUsage() throws TransactionError
    {
        runAndDrain("1");
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void nonPositiveAmount_isRejected() throws TransactionError
    {
        runAndDrain("0", "Bob");
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void nonNumericAmount_isRejected() throws TransactionError
    {
        runAndDrain("abc", "Bob");
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void unknownTarget_isRejected() throws TransactionError
    {
        when(configEngine.getEnableOfflinePayment()).thenReturn(false);

        runAndDrain("1", "Ghost");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void tippingYourself_isRejected() throws TransactionError
    {
        // findPlayer resolves "Alice" back to the sender's own record.
        runAndDrain("1", "Alice");
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    // --- async success paths ---

    @Test
    void tipToOnlinePlayer_sendsTransaction() throws TransactionError
    {
        PlayerMock bob = server.addPlayer("Bob");
        PlayerRecord bobRecord = new PlayerRecord(bob.getUniqueId().toString(), "Bob", TARGET_WALLET, false);
        when(db.getPlayerRecord(bob)).thenReturn(bobRecord);
        when(rpc.sendTransaction(SENDER_WALLET, TARGET_WALLET, 1.5)).thenReturn("BLOCKHASH");

        runAndDrain("1.5", "Bob");

        verify(rpc).sendTransaction(SENDER_WALLET, TARGET_WALLET, 1.5);
        verify(db, never()).saveOfflinePayment(any());
    }

    @Test
    void tipWithAllKeyword_usesWalletBalance() throws TransactionError
    {
        PlayerMock bob = server.addPlayer("Bob");
        PlayerRecord bobRecord = new PlayerRecord(bob.getUniqueId().toString(), "Bob", TARGET_WALLET, false);
        when(db.getPlayerRecord(bob)).thenReturn(bobRecord);
        when(rpc.getBalance(SENDER_WALLET)).thenReturn(3.0);
        when(rpc.sendTransaction(SENDER_WALLET, TARGET_WALLET, 3.0)).thenReturn("BLOCKHASH");

        runAndDrain("all", "Bob");

        verify(rpc).getBalance(SENDER_WALLET);
        verify(rpc).sendTransaction(SENDER_WALLET, TARGET_WALLET, 3.0);
    }

    @Test
    void tipToOfflineRecipient_savesOfflinePayment() throws TransactionError
    {
        // Target is resolvable for validation (online), but its stored UUID does not
        // match any online player, so the recipient is treated as offline.
        PlayerMock bob = server.addPlayer("Bob");
        PlayerRecord bobRecord = new PlayerRecord(UUID.randomUUID().toString(), "Bob", TARGET_WALLET, false);
        when(db.getPlayerRecord(bob)).thenReturn(bobRecord);
        when(rpc.sendTransaction(SENDER_WALLET, TARGET_WALLET, 1.5)).thenReturn("BLOCKHASH");

        runAndDrain("1.5", "Bob");

        verify(rpc).sendTransaction(SENDER_WALLET, TARGET_WALLET, 1.5);
        verify(db).saveOfflinePayment(any());
    }

    @Test
    void senderFrozen_doesNotSend() throws TransactionError
    {
        PlayerMock bob = server.addPlayer("Bob");
        PlayerRecord bobRecord = new PlayerRecord(bob.getUniqueId().toString(), "Bob", TARGET_WALLET, false);
        when(db.getPlayerRecord(bob)).thenReturn(bobRecord);
        senderRecord.setFrozen(true);

        runAndDrain("1.5", "Bob");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }
}
