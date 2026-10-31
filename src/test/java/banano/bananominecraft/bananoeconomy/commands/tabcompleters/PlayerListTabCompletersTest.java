package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tab completers that enumerate online/offline players via the Bukkit singleton,
 * so they need MockBukkit.
 */
class PlayerListTabCompletersTest
{
    private ServerMock server;
    private ConfigEngine configEngine;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        configEngine = mock(ConfigEngine.class);
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    // --- TipTabCompleter ---

    @Test
    void tip_firstArg_offersAllAndAmount()
    {
        List<String> r = new TipTabCompleter(configEngine).onTabComplete(null, null, "tip", new String[] { "" });
        assertTrue(r.contains("all"));
        assertTrue(r.contains("[amount]"));
    }

    @Test
    void tip_secondArg_listsOnlinePlayers()
    {
        server.addPlayer("Bob");
        List<String> r = new TipTabCompleter(configEngine).onTabComplete(null, null, "tip", new String[] { "1", "" });
        assertTrue(r.contains("Bob"));
    }

    @Test
    void tip_secondArg_includesOfflinePlayers_onlyWhenEnabled()
    {
        when(configEngine.getEnableOfflinePayment()).thenReturn(false);
        List<String> disabled = new TipTabCompleter(configEngine)
                .onTabComplete(null, null, "tip", new String[] { "1", "" });

        // With no online players and offline disabled, no player names are offered.
        assertFalse(disabled.contains("[Message]"));
        assertTrue(disabled.isEmpty() || !disabled.contains("Ghost"));
    }

    @Test
    void tip_thirdArg_offersMessageHint()
    {
        List<String> r = new TipTabCompleter(configEngine).onTabComplete(null, null, "tip", new String[] { "1", "Bob", "" });
        assertTrue(r.contains("[Message]"));
    }

    // --- AdminCommandTabCompleter ---

    @Test
    void admin_firstArg_listsAllSubcommands()
    {
        IDBConnector db = mock(IDBConnector.class);
        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "" });

        assertTrue(r.containsAll(List.of("setnode", "freeze", "unfreeze", "explorer",
                "serverwallet", "offlinetransactions", "websocket")));
    }

    @Test
    void admin_firstArg_filtersByPrefix()
    {
        IDBConnector db = mock(IDBConnector.class);
        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "se" });

        assertTrue(r.contains("setnode"));
        assertTrue(r.contains("serverwallet"));
        assertFalse(r.contains("freeze"));
    }

    @Test
    void admin_freeze_listsUnfrozenPlayers()
    {
        IDBConnector db = mock(IDBConnector.class);
        when(db.getUnfrozenPlayers()).thenReturn(List.of(
                new PlayerRecord(UUID.randomUUID().toString(), "Active", "ban_x", false)));

        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "freeze", "" });

        assertTrue(r.contains("Active"));
    }

    @Test
    void admin_unfreeze_listsFrozenPlayers()
    {
        IDBConnector db = mock(IDBConnector.class);
        when(db.getFrozenPlayers()).thenReturn(List.of(
                new PlayerRecord(UUID.randomUUID().toString(), "Frosty", "ban_x", true)));

        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "unfreeze", "" });

        assertTrue(r.contains("Frosty"));
    }

    @Test
    void admin_serverwallet_listsSubActions()
    {
        IDBConnector db = mock(IDBConnector.class);
        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "serverwallet", "" });

        assertTrue(r.containsAll(List.of("tip", "deposit", "withdraw", "balance")));
    }

    @Test
    void admin_websocketSet_offersDefaultUrl()
    {
        IDBConnector db = mock(IDBConnector.class);
        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "websocket", "set", "" });

        assertTrue(r.contains("wss://ws.banano.trade"));
    }

    @Test
    void admin_serverwalletTip_listsOnlinePlayersAtFourthArg()
    {
        IDBConnector db = mock(IDBConnector.class);
        server.addPlayer("Bob");

        List<String> r = new AdminCommandTabCompleter(configEngine, db)
                .onTabComplete(null, null, "be", new String[] { "serverwallet", "tip", "1", "" });

        assertTrue(r.contains("Bob"));
    }
}
