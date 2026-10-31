package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionHistoryCommandTest
{
    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private TaskTracker taskTracker;
    private ConfigEngine configEngine;
    private MessageGenerator messageGenerator;
    private TransactionHistoryCommand command;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs     = mock(EconomyFuncs.class);
        taskTracker      = mock(TaskTracker.class);
        configEngine     = mock(ConfigEngine.class);
        messageGenerator = mock(MessageGenerator.class);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(10);

        I18n i18n = new I18n(getClass().getClassLoader());
        command = new TransactionHistoryCommand(plugin, economyFuncs, taskTracker, configEngine, i18n, messageGenerator);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private boolean run(String... args)
    {
        boolean result = command.onCommand(player, null, "history", args);
        server.getScheduler().waitAsyncTasksFinished();
        return result;
    }

    @Test
    void nonPlayerSender_isIgnored()
    {
        command.onCommand(server.getConsoleSender(), null, "history", new String[0]);
        verifyNoInteractions(economyFuncs);
    }

    @Test
    void defaultCount_queriesHistory()
    {
        when(economyFuncs.getTransactionHistory(player.getUniqueId(), ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS))
                .thenReturn(List.of());

        run();

        verify(economyFuncs).getTransactionHistory(player.getUniqueId(), ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS);
    }

    @Test
    void allKeyword_usesConfiguredMaximum()
    {
        run("all");

        verify(economyFuncs).getTransactionHistory(player.getUniqueId(), 10);
    }

    @Test
    void explicitCount_isUsed()
    {
        run("5");

        verify(economyFuncs).getTransactionHistory(player.getUniqueId(), 5);
    }

    @Test
    void nonNumericCount_isRejected()
    {
        assertTrue(run("abc"));
        verify(economyFuncs, never()).getTransactionHistory(any(), anyInt());
    }

    @Test
    void countBelowMinimum_isRejected()
    {
        run("0");
        verify(economyFuncs, never()).getTransactionHistory(any(), anyInt());
    }

    @Test
    void countAboveMaximum_isRejected()
    {
        run("999");
        verify(economyFuncs, never()).getTransactionHistory(any(), anyInt());
    }

    @Test
    void recordsReturned_areRendered()
    {
        TransactionRecord record = new TransactionRecord(
                LocalDateTime.now(), "ban_someaddress0000000000000000000", TransactionDirection.Receive,
                1.0, "HASH", true);
        when(economyFuncs.getTransactionHistory(player.getUniqueId(), ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS))
                .thenReturn(List.of(record));
        when(messageGenerator.generateBlockExplorerLink(anyString(), anyString()))
                .thenReturn(new net.md_5.bungee.api.chat.TextComponent("x"));

        run();

        verify(economyFuncs).getTransactionHistory(player.getUniqueId(), ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS);
    }
}
