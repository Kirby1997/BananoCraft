package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BalanceCommandTest
{
    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private TaskTracker taskTracker;
    private I18n i18n;
    private BalanceCommand command;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs = mock(EconomyFuncs.class);
        taskTracker  = mock(TaskTracker.class);
        i18n         = mock(I18n.class);

        command = new BalanceCommand(plugin, economyFuncs, taskTracker, i18n);
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    @Test
    void nonPlayerSender_doesNotQueryBalance()
    {
        CommandSender console = server.getConsoleSender();

        boolean result = command.onCommand(console, null, "balance", new String[0]);

        assertFalse(result);
        verifyNoInteractions(economyFuncs);
        verifyNoInteractions(taskTracker);
    }

    @Test
    void playerSender_fetchesBalanceAsynchronously()
    {
        PlayerMock player = server.addPlayer("Alice");
        when(economyFuncs.getBalance(player)).thenReturn(42.0);

        command.onCommand(player, null, "balance", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();

        verify(economyFuncs).getBalance(player);
        verify(taskTracker).track(any());
    }
}
