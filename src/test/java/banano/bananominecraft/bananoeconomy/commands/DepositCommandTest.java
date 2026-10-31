package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
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

class DepositCommandTest
{
    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String MASTER = "ban_1t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String ACCOUNT_EXPLORER = "https://creeper.banano.cc/explorer/account/";

    private ServerMock server;
    private PluginMock plugin;

    private EconomyFuncs economyFuncs;
    private ConfigEngine configEngine;
    private RPC rpc;
    private I18n i18n;
    private DepositCommand command;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        economyFuncs = mock(EconomyFuncs.class);
        configEngine = mock(ConfigEngine.class);
        rpc          = mock(RPC.class);
        // Real I18n: never returns null, so PlayerMock (which rejects null messages) is happy.
        i18n         = new I18n(getClass().getClassLoader());

        when(configEngine.getExplorerAccount()).thenReturn(ACCOUNT_EXPLORER);

        command = new DepositCommand(plugin, economyFuncs, configEngine, rpc, i18n);
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    @Test
    void frozenPlayer_isRejected_withoutRevealingWallet()
    {
        PlayerMock player = server.addPlayer("Alice");
        when(economyFuncs.isFrozen(player)).thenReturn(true);

        boolean result = command.onCommand(player, null, "deposit", new String[0]);

        assertFalse(result);
        verify(economyFuncs, never()).getWallet(player);
    }

    @Test
    void normalPlayer_showsOwnWallet()
    {
        PlayerMock player = server.addPlayer("Alice");
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(economyFuncs.getWallet(player)).thenReturn(WALLET);

        command.onCommand(player, null, "deposit", new String[0]);

        verify(economyFuncs).getWallet(player);
        verify(rpc, never()).getMasterWallet();
    }

    @Test
    void serverArg_showsMasterWallet()
    {
        PlayerMock player = server.addPlayer("Alice");
        when(economyFuncs.isFrozen(player)).thenReturn(false);
        when(economyFuncs.getWallet(player)).thenReturn(WALLET);
        when(rpc.getMasterWallet()).thenReturn(MASTER);

        command.onCommand(player, null, "deposit", new String[] { "server" });

        verify(rpc).getMasterWallet();
    }

    @Test
    void nonPlayerSender_isIgnored()
    {
        CommandSender console = server.getConsoleSender();

        boolean result = command.onCommand(console, null, "deposit", new String[0]);

        assertFalse(result);
        verifyNoInteractions(economyFuncs);
    }
}
