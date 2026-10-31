package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.services.ReceiveService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;

import static org.mockito.Mockito.*;

class ReceiveCommandTest
{
    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private TaskTracker taskTracker;
    private I18n i18n;
    private ReceiveService receiveService;
    private ReceiveCommand command;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs   = mock(EconomyFuncs.class);
        taskTracker    = mock(TaskTracker.class);
        receiveService = mock(ReceiveService.class);
        // Real I18n: never returns null, so PlayerMock (which rejects null messages) is happy.
        i18n           = new I18n(getClass().getClassLoader());

        when(economyFuncs.getWallet(any())).thenReturn(WALLET);

        command = new ReceiveCommand(plugin, economyFuncs, taskTracker, i18n, receiveService);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private void runAndDrain()
    {
        command.onCommand(player, null, "receive", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();
    }

    @Test
    void frozenPlayer_cannotReceive()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(true);

        runAndDrain();

        verify(receiveService, never()).receiveAllPending(anyString());
    }

    @Test
    void noPendingBlocks_isHandledGracefully()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(receiveService.receiveAllPending(WALLET))
                .thenReturn(ReceiveService.ReceiveResult.succeeded(List.of()));

        runAndDrain();

        verify(receiveService).receiveAllPending(WALLET);
    }

    @Test
    void pendingBlocks_areReceived()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(receiveService.receiveAllPending(WALLET))
                .thenReturn(ReceiveService.ReceiveResult.succeeded(List.of("RECEIVE1", "RECEIVE2")));

        runAndDrain();

        verify(receiveService).receiveAllPending(WALLET);
    }

    @Test
    void failure_isHandledGracefully()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(receiveService.receiveAllPending(WALLET))
                .thenReturn(ReceiveService.ReceiveResult.partiallyFailed(List.of(), "Block not found"));

        runAndDrain();

        verify(receiveService).receiveAllPending(WALLET);
    }
}
