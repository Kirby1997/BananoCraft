package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.mockito.Mockito.*;

class WithdrawCommandTest
{
    private static final String SENDER    = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String RECIPIENT = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String BLOCK_EXPLORER = "https://creeper.banano.cc/explorer/block/";

    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private RPC rpc;
    private ConfigEngine configEngine;
    private TaskTracker taskTracker;
    private I18n i18n;
    private WithdrawCommand command;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs = mock(EconomyFuncs.class);
        rpc          = mock(RPC.class);
        configEngine = mock(ConfigEngine.class);
        taskTracker  = mock(TaskTracker.class);
        // Real I18n: never returns null, so PlayerMock (which rejects null messages) is happy.
        i18n         = new I18n(getClass().getClassLoader());

        when(configEngine.getExplorerBlock()).thenReturn(BLOCK_EXPLORER);
        when(economyFuncs.getWallet(any())).thenReturn(SENDER);

        command = new WithdrawCommand(plugin, economyFuncs, rpc, configEngine, taskTracker, i18n);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private void runAndDrain(String... args)
    {
        command.onCommand(player, null, "withdraw", args);
        server.getScheduler().waitAsyncTasksFinished();
    }

    @Test
    void frozenPlayer_cannotWithdraw() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(true);

        runAndDrain("1.5", RECIPIENT);

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void nonPositiveAmount_isRejected() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);

        runAndDrain("0", RECIPIENT);

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void missingDestination_showsUsage() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);

        runAndDrain("1.5");

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void emptyArgs_areHandledGracefully() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);

        runAndDrain(); // args[0] access would throw; outer catch -> usage

        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void explicitAmount_sendsTransaction() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(rpc.sendTransaction(SENDER, RECIPIENT, 1.5)).thenReturn("BLOCKHASH");

        runAndDrain("1.5", RECIPIENT);

        verify(rpc).sendTransaction(SENDER, RECIPIENT, 1.5);
    }

    @Test
    void allKeyword_withdrawsFullBalance() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(rpc.getBalance(SENDER)).thenReturn(2.0);
        when(rpc.sendTransaction(SENDER, RECIPIENT, 2.0)).thenReturn("BLOCKHASH");

        runAndDrain("all", RECIPIENT);

        verify(rpc).getBalance(SENDER);
        verify(rpc).sendTransaction(SENDER, RECIPIENT, 2.0);
    }

    @Test
    void transactionError_isHandled() throws TransactionError
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(rpc.sendTransaction(SENDER, RECIPIENT, 1.5))
                .thenThrow(new TransactionError("Insufficient balance"));

        runAndDrain("1.5", RECIPIENT);

        // The send was attempted exactly once and the error path swallowed the failure.
        verify(rpc, times(1)).sendTransaction(SENDER, RECIPIENT, 1.5);
    }
}
