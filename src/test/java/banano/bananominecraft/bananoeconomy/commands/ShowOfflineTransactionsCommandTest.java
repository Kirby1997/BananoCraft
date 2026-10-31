package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class ShowOfflineTransactionsCommandTest
{
    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private IDBConnector db;
    private ConfigEngine configEngine;
    private TaskTracker taskTracker;
    private MessageGenerator messageGenerator;
    private ShowOfflineTransactionsCommand command;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs     = mock(EconomyFuncs.class);
        db               = mock(IDBConnector.class);
        configEngine     = mock(ConfigEngine.class);
        taskTracker      = mock(TaskTracker.class);
        messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateTipReceiverMessage(any(), any(OfflinePaymentRecord.class)))
                .thenReturn(new BaseComponent[0]);
        when(messageGenerator.generateBlockExplorerLink(any(java.util.Locale.class), anyString()))
                .thenReturn(new TextComponent("link"));

        I18n i18n = new I18n(getClass().getClassLoader());
        command = new ShowOfflineTransactionsCommand(plugin, economyFuncs, db, configEngine, taskTracker, i18n, messageGenerator);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private void run()
    {
        command.onCommand(player, null, "showofflinetips", new String[0]);
        server.getScheduler().waitAsyncTasksFinished();
    }

    @Test
    void frozenPlayer_isRejected()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(true);

        run();

        verify(db, never()).getOfflinePaymentRecords(any());
    }

    @Test
    void offlineDisabled_isRejected()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(configEngine.getEnableOfflinePayment()).thenReturn(false);

        run();

        verify(db, never()).getOfflinePaymentRecords(any());
    }

    @Test
    void withPayments_rendersAndClears()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(configEngine.getEnableOfflinePayment()).thenReturn(true);
        when(db.getOfflinePaymentRecords(player)).thenReturn(List.of(
                new OfflinePaymentRecord(player.getUniqueId(), "Bob", 1.0, "HASH", LocalDateTime.now(), "")));

        run();

        verify(db).getOfflinePaymentRecords(player);
        verify(db).deleteOfflinePaymentRecords(player);
    }

    @Test
    void withNoPayments_doesNotDelete()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(configEngine.getEnableOfflinePayment()).thenReturn(true);
        when(db.getOfflinePaymentRecords(player)).thenReturn(List.of());

        run();

        verify(db).getOfflinePaymentRecords(player);
        verify(db, never()).deleteOfflinePaymentRecords(any());
    }

    @Test
    void nonPlayerSender_isIgnored()
    {
        command.onCommand(server.getConsoleSender(), null, "showofflinetips", new String[0]);
        verifyNoInteractions(db);
    }
}
