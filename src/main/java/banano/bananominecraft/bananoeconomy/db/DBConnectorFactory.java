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
     *   <li>If {@code mongoURI} is set → {@link MongoDBConnector}, or {@code null} if the
     *       server is unreachable (the caller must disable the plugin)</li>
     *   <li>Else if {@code mysqlServerName} is set → {@link MysqlDBConnector}</li>
     *   <li>Otherwise → {@link JsonDBConnector} (file-based fallback)</li>
     * </ul>
     *
     * @param plugin the owning plugin, used for config access and logging
     * @return a ready-to-use {@link IDBConnector}, or {@code null} if the configured
     *         MongoDB server could not be reached
     */
    public static IDBConnector create(Plugin plugin)
    {
        FileConfiguration config = plugin.getConfig();

        if (isConfigured(config, "mongoURI"))
        {
            plugin.getLogger().info("Initialising MongoDB connection...");

            try
            {
                return new MongoDBConnector(plugin);
            }
            catch (MongoDBConnector.ConnectionFailedException ex)
            {
                // One clear line instead of a driver stack trace (issue #13). Fail closed rather than
                // falling back to JSON: a fallback store has none of the existing UUID -> wallet records,
                // so every player joining during the outage would be issued a new, orphaned wallet.
                plugin.getLogger().severe("MongoDB is unreachable: " + ex.getMessage()
                        + " Check mongoURI in config.yml and that the MongoDB server is running,"
                        + " then restart the server. BananoEconomy will be disabled.");

                return null;
            }
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
