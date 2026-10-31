package banano.bananominecraft.bananoeconomy.db;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Selects and constructs the appropriate {@link IDBConnector} implementation
 * based on the plugin configuration.
 *
 * <p>Extracting this logic from {@code Main} keeps the entry-point focused on
 * lifecycle management and means adding a new backend only requires adding a
 * new branch here — not modifying {@code Main}.</p>
 */
public final class DBConnectorFactory
{
    private DBConnectorFactory() {}

    /**
     * Create the best-fit {@link IDBConnector} for the supplied plugin.
     *
     * <ul>
     *   <li>If {@code mongoURI} is set → {@link MongoDBConnector}</li>
     *   <li>Else if {@code mysqlServerName} is set → {@link MysqlDBConnector}</li>
     *   <li>Otherwise → {@link JsonDBConnector} (file-based fallback)</li>
     * </ul>
     *
     * @param plugin the owning plugin, used for config access and logging
     * @return a ready-to-use {@link IDBConnector}
     */
    public static IDBConnector create(Plugin plugin)
    {
        FileConfiguration config = plugin.getConfig();

        if (isConfigured(config, "mongoURI"))
        {
            plugin.getLogger().info("Initialising MongoDB connection...");
            return new MongoDBConnector(plugin);
        }
        else if (isConfigured(config, "mysqlServerName"))
        {
            plugin.getLogger().info("Initialising MySQL connection...");
            return new MysqlDBConnector(plugin);
        }
        else
        {
            plugin.getLogger().info("Initialising Json connection...");
            return new JsonDBConnector(plugin);
        }
    }

    private static boolean isConfigured(FileConfiguration config, String key)
    {
        String value = config.getString(key);

        return config.contains(key) && value != null && !value.isEmpty();
    }
}
