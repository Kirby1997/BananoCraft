package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminCommandTest
{
    private ServerMock server;
    private PluginMock plugin;

    private ConfigEngine configEngine;
    private EconomyFuncs economyFuncs;
    private IDBConnector db;
    private RPC rpc;
    private BananoWebSocket webSocket;
    private TaskTracker taskTracker;
    private MessageGenerator messageGenerator;
    private AdminCommand command;

    private PlayerMock admin;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        configEngine     = mock(ConfigEngine.class);
        economyFuncs     = mock(EconomyFuncs.class);
        db               = mock(IDBConnector.class);
        rpc              = mock(RPC.class);
        webSocket        = mock(BananoWebSocket.class);
        taskTracker      = mock(TaskTracker.class);
        messageGenerator = mock(MessageGenerator.class);

        // Keep rich-message builders non-null for paths that reach spigot().sendMessage().
        // any() (not anyString()) so the stub also covers a null wallet argument.
        when(messageGenerator.generateClickableAddressMessage(any(), any()))
                .thenReturn(new TextComponent("addr"));
        when(messageGenerator.generateTipSenderMessage(any(), anyString(), anyDouble(), anyString(), any()))
                .thenReturn(new BaseComponent[0]);
        when(messageGenerator.generateBlockExplorerLink(any(java.util.Locale.class), anyString()))
                .thenReturn(new TextComponent("link"));
        when(messageGenerator.generateTipReceiverMessage(any(), anyString(), anyDouble(), anyString(), any()))
                .thenReturn(new BaseComponent[0]);

        command = new AdminCommand(plugin, configEngine, economyFuncs, db, rpc, webSocket, taskTracker, messageGenerator);

        admin = server.addPlayer("Admin");
        admin.setOp(true); // grants AdminCommand permission
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private boolean run(String... args)
    {
        boolean result = command.onCommand(admin, null, "be", args);
        server.getScheduler().waitAsyncTasksFinished();
        return result;
    }

    // --- permission gating ---

    @Test
    void nonOpPlayerWithoutPermission_isRejected()
    {
        PlayerMock peasant = server.addPlayer("Peasant"); // not op, no permission

        boolean result = command.onCommand(peasant, null, "be", new String[] { "freeze", "Bob" });

        assertFalse(result);
        verify(economyFuncs, never()).freezePlayer(anyString());
    }

    @Test
    void opPlayer_isAllowed()
    {
        boolean result = run("setnode", "http://node:7072");

        assertTrue(result);
        verify(configEngine).setNodeAddress("http://node:7072");
    }

    @Test
    void consoleSender_bypassesPermissionCheck()
    {
        ConsoleCommandSender console = server.getConsoleSender();

        boolean result = command.onCommand(console, null, "be", new String[] { "setnode", "http://node:7072" });

        assertTrue(result);
        verify(configEngine).setNodeAddress("http://node:7072");
    }

    @Test
    void noArguments_isRejected()
    {
        assertFalse(run());
    }

    @Test
    void unknownSubcommand_isReported()
    {
        // Routing still "handled" the command (returns true) but performs no action.
        assertTrue(run("bogus"));
        verifyNoInteractions(rpc);
    }

    // --- setnode ---

    @Test
    void setnode_persistsNodeAddress()
    {
        when(rpc.wallet_exists()).thenReturn(true);

        run("setnode", "http://node:7072");

        verify(configEngine).setNodeAddress("http://node:7072");
        verify(configEngine, atLeastOnce()).save();
    }

    @Test
    void setnode_createsMasterWallet_whenWalletMissing()
    {
        when(rpc.wallet_exists()).thenReturn(false);
        when(rpc.accountCreate(0)).thenReturn("ban_newmaster");

        run("setnode", "http://node:7072");

        verify(rpc).walletCreate();
        verify(rpc).accountCreate(0);
        verify(configEngine).setMasterWallet("ban_newmaster");
    }

    @Test
    void setnode_wrongArgCount_doesNotPersist()
    {
        run("setnode"); // missing address

        verify(configEngine, never()).setNodeAddress(anyString());
    }

    // --- offlinetransactions ---

    @Test
    void offlineTransactions_enable()
    {
        run("offlinetransactions", "enable");

        verify(configEngine).setEnableOfflinePayment(true);
        verify(configEngine).save();
    }

    @Test
    void offlineTransactions_disable()
    {
        run("offlinetransactions", "disable");

        verify(configEngine).setEnableOfflinePayment(false);
    }

    // --- websocket ---

    @Test
    void websocket_noSubcommand_showsUsage()
    {
        assertFalse(run("websocket"));
        verifyNoInteractions(webSocket);
    }

    @Test
    void websocket_status_queriesCurrentUrl()
    {
        when(webSocket.getCurrentUrl()).thenReturn("wss://ws.banano.trade");

        run("websocket", "status");

        verify(webSocket).getCurrentUrl();
    }

    @Test
    void websocket_reconnect_callsReconnect()
    {
        run("websocket", "reconnect");

        verify(webSocket).reconnect();
    }

    @Test
    void websocket_setValidUrl_updatesSocket()
    {
        run("websocket", "set", "wss://new.host");

        verify(webSocket).setUrl("wss://new.host");
    }

    @Test
    void websocket_setInvalidScheme_isRejected()
    {
        run("websocket", "set", "http://insecure");

        verify(webSocket, never()).setUrl(anyString());
    }

    // --- explorer ---

    @Test
    void explorer_tooFewArgs_isReported()
    {
        // "/be explorer account" has no view/set verb.
        assertTrue(run("explorer", "account"));
        verify(configEngine, never()).setExplorerAccount(anyString());
    }

    @Test
    void explorer_setAccount_persists()
    {
        run("explorer", "account", "set", "https://example/account/");

        verify(configEngine).setExplorerAccount("https://example/account/");
        verify(configEngine).save();
    }

    @Test
    void explorer_viewBlock_readsConfig()
    {
        when(configEngine.getExplorerBlock()).thenReturn("https://creeper.banano.cc/explorer/block/");

        run("explorer", "block", "view");

        verify(configEngine).getExplorerBlock();
    }

    // --- serverwallet ---

    @Test
    void serverwallet_noSubcommand_showsUsage()
    {
        assertFalse(run("serverwallet"));
    }

    @Test
    void serverwallet_balance_queriesMasterWalletBalance()
    {
        when(rpc.getMasterWallet()).thenReturn("ban_master");
        when(rpc.getBalance("ban_master")).thenReturn(123.0);

        run("serverwallet", "balance");

        verify(rpc).getBalance("ban_master");
    }

    // --- serverwallet withdraw (delegates to WithdrawService) ---

    @Test
    void serverwallet_withdraw_sendsFromMasterWallet() throws TransactionError
    {
        when(configEngine.getMasterWallet()).thenReturn("ban_master");
        when(configEngine.getExplorerBlock()).thenReturn("https://explorer/block/");
        when(rpc.sendTransaction("ban_master", "ban_dest", 5.0)).thenReturn("BLOCK");

        run("serverwallet", "withdraw", "5", "ban_dest");

        verify(rpc).sendTransaction("ban_master", "ban_dest", 5.0);
    }

    @Test
    void serverwallet_withdraw_all_resolvesMasterBalance() throws TransactionError
    {
        when(configEngine.getMasterWallet()).thenReturn("ban_master");
        when(configEngine.getExplorerBlock()).thenReturn("https://explorer/block/");
        when(rpc.getBalance("ban_master")).thenReturn(2.0);
        when(rpc.sendTransaction("ban_master", "ban_dest", 2.0)).thenReturn("BLOCK");

        run("serverwallet", "withdraw", "all", "ban_dest");

        verify(rpc).getBalance("ban_master");
        verify(rpc).sendTransaction("ban_master", "ban_dest", 2.0);
    }

    @Test
    void serverwallet_withdraw_transactionError_doesNotThrow() throws TransactionError
    {
        when(configEngine.getMasterWallet()).thenReturn("ban_master");
        when(rpc.sendTransaction("ban_master", "ban_dest", 5.0))
                .thenThrow(new TransactionError("Invalid address"));

        run("serverwallet", "withdraw", "5", "ban_dest");

        verify(rpc).sendTransaction("ban_master", "ban_dest", 5.0);
    }

    @Test
    void serverwallet_withdraw_missingAddress_showsUsage() throws TransactionError
    {
        when(configEngine.getMasterWallet()).thenReturn("ban_master");

        run("serverwallet", "withdraw", "5");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    // --- serverwallet tip (delegates to TipService) ---

    @Test
    void serverwallet_tip_onlineRecipient_sendsAndDoesNotPersist() throws TransactionError
    {
        Player bob = server.addPlayer("Bob");
        PlayerRecord record = new PlayerRecord(bob.getUniqueId().toString(), "Bob", "ban_bob", false);
        when(db.getPlayerRecord(any(Player.class))).thenReturn(record);
        when(configEngine.getMasterWallet()).thenReturn("ban_master");
        when(configEngine.getExplorerBlock()).thenReturn("https://explorer/block/");
        when(rpc.sendTransaction("ban_master", "ban_bob", 1.5)).thenReturn("BLOCK");

        run("serverwallet", "tip", "1.5", "Bob");

        verify(rpc).sendTransaction("ban_master", "ban_bob", 1.5);
        verify(db, never()).saveOfflinePayment(any());
    }

    @Test
    void serverwallet_tip_offlineRecipient_persistsPayment() throws TransactionError
    {
        // Found by name, but the record's UUID maps to no online player → offline.
        Player bob = server.addPlayer("Bob");
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", "ban_bob", false);
        when(db.getPlayerRecord(any(Player.class))).thenReturn(record);
        when(configEngine.getMasterWallet()).thenReturn("ban_master");
        when(rpc.sendTransaction("ban_master", "ban_bob", 1.5)).thenReturn("BLOCK");

        run("serverwallet", "tip", "1.5", "Bob");

        verify(rpc).sendTransaction("ban_master", "ban_bob", 1.5);
        verify(db).saveOfflinePayment(any());
    }

    @Test
    void serverwallet_tip_frozenTarget_doesNotSend() throws TransactionError
    {
        Player bob = server.addPlayer("Bob");
        PlayerRecord frozen = new PlayerRecord(bob.getUniqueId().toString(), "Bob", "ban_bob", true);
        when(db.getPlayerRecord(any(Player.class))).thenReturn(frozen);
        when(configEngine.getMasterWallet()).thenReturn("ban_master");

        run("serverwallet", "tip", "1.5", "Bob");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void serverwallet_tip_nonPositiveAmount_isRejected() throws TransactionError
    {
        when(configEngine.getMasterWallet()).thenReturn("ban_master");

        run("serverwallet", "tip", "0", "Bob");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    // --- freeze / unfreeze ---

    @Test
    void freeze_unknownPlayer_delegatesByName()
    {
        when(economyFuncs.freezePlayer("Ghost")).thenReturn(true);

        run("freeze", "Ghost");

        verify(economyFuncs).freezePlayer("Ghost");
    }

    @Test
    void freeze_noPlayer_showsUsage()
    {
        run("freeze");

        verify(economyFuncs, never()).freezePlayer(anyString());
    }

    @Test
    void unfreeze_unknownPlayer_delegatesByName()
    {
        when(economyFuncs.unfreezePlayer("Ghost")).thenReturn(true);

        run("unfreeze", "Ghost");

        verify(economyFuncs).unfreezePlayer("Ghost");
    }
}
