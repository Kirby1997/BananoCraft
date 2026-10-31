package banano.bananominecraft.bananoeconomy.db;

import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the database backend selection logic in {@link DBConnectorFactory}.
 *
 * <p>Only the JSON fallback branch is exercised here: the MongoDB / MySQL branches
 * construct real connectors that open live connections, which belongs in an
 * integration test (e.g. Testcontainers), not a unit test. The {@code isConfigured}
 * guard — which must treat an empty backend key as "not configured" — is verified
 * via the blank-value cases below.</p>
 */
class DBConnectorFactoryTest
{
    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown()
    {
        MockBukkit.unmock();
    }

    @Test
    void selectsJson_whenNoBackendConfigured()
    {
        IDBConnector connector = DBConnectorFactory.create(plugin);

        assertInstanceOf(JsonDBConnector.class, connector);
        connector.close();
    }

    @Test
    void selectsJson_whenMongoUriIsBlank()
    {
        FileConfiguration config = plugin.getConfig();
        config.set("mongoURI", "");

        IDBConnector connector = DBConnectorFactory.create(plugin);

        assertInstanceOf(JsonDBConnector.class, connector);
        connector.close();
    }

    @Test
    void selectsJson_whenMysqlServerNameIsBlank()
    {
        FileConfiguration config = plugin.getConfig();
        config.set("mysqlServerName", "");

        IDBConnector connector = DBConnectorFactory.create(plugin);

        assertInstanceOf(JsonDBConnector.class, connector);
        connector.close();
    }
}
