package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.mockito.Mockito.*;

class OnJoinTest
{
    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private IDBConnector db;
    private ConfigEngine configEngine;
    private BananoWebSocket webSocket;
    private TaskTracker taskTracker;
    private MessageGenerator messageGenerator;
    private OnJoin listener;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs     = mock(EconomyFuncs.class);
        db               = mock(IDBConnector.class);
        configEngine     = mock(ConfigEngine.class);
        webSocket        = mock(BananoWebSocket.class);
        taskTracker      = mock(TaskTracker.class);
        messageGenerator = mock(MessageGenerator.class);
        when(messageGenerator.generateClickToViewOfflinePayments(any())).thenReturn(new TextComponent("link"));

        I18n i18n = new I18n(getClass().getClassLoader());
        listener = new OnJoin(plugin, economyFuncs, db, configEngine, webSocket, taskTracker, i18n, messageGenerator);

        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    private void fireJoin()
    {
        listener.onJoinServer(new PlayerJoinEvent(player, Component.text("joined")));
        server.getScheduler().waitAsyncTasksFinished();
    }

    @Test
    void join_createsAccountAndWatchesWallet()
    {
        when(economyFuncs.accountCreate(player)).thenReturn(true);
        when(economyFuncs.getWallet(player)).thenReturn("ban_wallet");

        fireJoin();

        verify(economyFuncs).accountCreate(player);
        verify(webSocket).watchAccount("ban_wallet", player.getUniqueId());
    }

    @Test
    void join_accountCreateFails_doesNotWatch()
    {
        when(economyFuncs.accountCreate(player)).thenReturn(false);

        fireJoin();

        verify(webSocket, never()).watchAccount(anyString(), any());
    }

    @Test
    void join_withOfflinePaymentsEnabled_notifiesWhenTotalPositive()
    {
        when(economyFuncs.accountCreate(player)).thenReturn(true);
        when(economyFuncs.getWallet(player)).thenReturn("ban_wallet");
        when(configEngine.getEnableOfflinePayment()).thenReturn(true);
        when(db.getOfflinePaymentsTotal(player)).thenReturn(5.0);

        fireJoin();

        verify(db).getOfflinePaymentsTotal(player);
        verify(messageGenerator).generateClickToViewOfflinePayments(any());
    }

    @Test
    void join_withOfflinePaymentsEnabled_silentWhenZero()
    {
        when(economyFuncs.accountCreate(player)).thenReturn(true);
        when(economyFuncs.getWallet(player)).thenReturn("ban_wallet");
        when(configEngine.getEnableOfflinePayment()).thenReturn(true);
        when(db.getOfflinePaymentsTotal(player)).thenReturn(0.0);

        fireJoin();

        verify(messageGenerator, never()).generateClickToViewOfflinePayments(any());
    }
}
