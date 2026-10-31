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
     *   <li>If {@code mongoURI} is set → {@link MongoDBConnector} (falls back to
     *       {@link JsonDBConnector}, with a loud warning, if the server is unreachable)</li>
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

            try
            {
                return new MongoDBConnector(plugin);
            }
            catch (MongoDBConnector.ConnectionFailedException ex)
            {
                // One clear line instead of a driver stack trace (issue #13).
                plugin.getLogger().severe("MongoDB is unreachable: " + ex.getMessage()
                        + " Check mongoURI in config.yml and that the MongoDB server is running.");
                plugin.getLogger().warning("**************************************************************");
                plugin.getLogger().warning("* FALLING BACK TO LOCAL JSON STORAGE. Player records created  *");
                plugin.getLogger().warning("* now will NOT be in MongoDB. Fix the connection and restart. *");
                plugin.getLogger().warning("**************************************************************");

                return new JsonDBConnector(plugin);
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
