package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class MysqlDBConnector extends BaseDBConnector {

    private final Plugin plugin;
    private final HikariDataSource dataSource;

    public MysqlDBConnector(Plugin plugin) {

        this.plugin = plugin;
        this.dataSource = new HikariDataSource();

        initialiseDataSource();

        setupDatabase();

    }

    private void initialiseDataSource() {

        try {

            FileConfiguration config = this.plugin.getConfig();

            String serverName = config.getString("mysqlServerName");
            int port = config.getInt("mysqlPort");
            String databaseName = config.getString("mysqlDatabaseName");
            String userName = config.getString("mysqlUsername");
            String password = config.getString("mysqlPassword");

            this.dataSource.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            this.dataSource.addDataSourceProperty("serverName", serverName);
            this.dataSource.addDataSourceProperty("port", port);
            this.dataSource.addDataSourceProperty("databaseName", databaseName);
            this.dataSource.addDataSourceProperty("user", userName);
            this.dataSource.addDataSourceProperty("password", password);

        }
        catch (Exception ex) {

            ex.printStackTrace();

        }

    }

    @Override
    public void close() {

        try {
            this.dataSource.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private Connection getConnection() {

        try {

            if (this.dataSource != null) {

                return this.dataSource.getConnection();

            }

        }
        catch (Exception ex) {

            ex.printStackTrace();

        }

        return null;

    }

    private void setupDatabase() {

        // If it's the first time connecting, let's set up the tables etc.
        Connection connection = getConnection();

        String usersTable = "CREATE TABLE IF NOT EXISTS users (" +
                            "    playerUUID     VARCHAR(75) NOT NULL UNIQUE," +
                            "    name           VARCHAR(50) NOT NULL," +
                            "    wallet         VARCHAR(100) NOT NULL," +
                            "    frozen         BOOLEAN NOT NULL DEFAULT FALSE" +
                            ")  ENGINE=INNODB";

        try {

            PreparedStatement userTableCreator = connection.prepareStatement(usersTable);

            userTableCreator.execute();

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        try {

            connection.close();

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    @Override
    protected PlayerRecord loadPlayerRecord(Player player) {

        return getPlayerRecord(player.getUniqueId(), true);

    }

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord) {

        PlayerRecord playerRecord = null;

        if(this.playerRecords.containsKey(playerUUID)) {

            playerRecord = this.playerRecords.get(playerUUID);

        }
        else {

            Connection connection = getConnection();

            try {

                // Query the database

                PreparedStatement query = connection.prepareStatement("SELECT playerUUID, name, wallet, frozen " +
                                                                          "FROM users " +
                                                                          "WHERE playerUUID = ?");

                query.setString(1, playerUUID.toString());

                ResultSet results = query.executeQuery();

                // We're only expecting one row - and should only ever have one per player!
                if (results != null
                        && results.next()) {

                    playerRecord = new PlayerRecord(playerUUID.toString(),
                                                    results.getString("name"),
                                                    results.getString("wallet"),
                                                    results.getBoolean("frozen"));

                    results.close();

                }

                if(playerRecord != null
                     && cacheRecord
                     && !playerRecords.containsKey(playerUUID)) {

                    playerRecords.put(playerUUID, playerRecord);

                }

            } catch (Exception ex) {

                ex.printStackTrace();

            } finally {

                try {

                    connection.close();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }

        }

        return playerRecord;

    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player) {

        return getPlayerRecord(player.getUniqueId(), false);

    }

    @Override
    protected boolean insertPlayerRecord(PlayerRecord playerRecord) {

        boolean success = false;
        Connection connection = getConnection();

        try {

            PreparedStatement insert = connection.prepareStatement("INSERT INTO users (playerUUID, name, wallet, frozen) " +
                                                                       "VALUES (?, ?, ?, ?)");

            insert.setString(1, playerRecord.getPlayerUUID());
            insert.setString(2, playerRecord.getPlayerName());
            insert.setString(3, playerRecord.getWallet());
            insert.setBoolean(4, playerRecord.isFrozen());

            success = insert.executeUpdate() > 0;

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {

            try {

                connection.close();

            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return success;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord) {

        boolean success = false;
        Connection connection = getConnection();

        try {

            PreparedStatement insert = connection.prepareStatement("UPDATE users " +
                                                                       "SET frozen = ? " +
                                                                       "WHERE playerUUID = ?");

            insert.setBoolean(1, playerRecord.isFrozen());
            insert.setString(2, playerRecord.getPlayerUUID());

            success = insert.executeUpdate() > 0;

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {

            try {

                connection.close();

            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return success;

    }

    @Override
    public boolean hasPlayerRecord(Player player) {

        return hasPlayerRecord(player.getUniqueId());

    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player) {

        return hasPlayerRecord(player.getUniqueId());

    }

    private boolean hasPlayerRecord(UUID playerUUID) {

        if(playerRecords.containsKey(playerUUID)) {

            return true;

        }

        boolean hasRecord = false;
        Connection connection = getConnection();

        try {

            // Query the database

            PreparedStatement query = connection.prepareStatement("SELECT COUNT(playerUUID) AS playercount " +
                    "FROM users " +
                    "WHERE playerUUID = ?");

            query.setString(1, playerUUID.toString());

            ResultSet results = query.executeQuery();

            // We're only expecting one row - and should only ever have one per player!
            if (results != null
                    && results.next()) {

                hasRecord = results.getInt("playercount") > 0;

                results.close();

            }

        } catch (Exception ex) {

            ex.printStackTrace();

        } finally {

            try {

                connection.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return hasRecord;

    }

    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer) {

        boolean hasRecord = false;
        Connection connection = getConnection();

        try {

            // Query the database

            PreparedStatement query = connection.prepareStatement("SELECT COUNT(playerUUID) AS playercount " +
                                                                      "FROM users " +
                                                                      "WHERE playerUUID <> ? AND wallet = ?");

            query.setString(1, currentPlayer.getUniqueId().toString());
            query.setString(2, walletAddress);

            ResultSet results = query.executeQuery();

            // We're only expecting one row - and should only ever have one per player!
            if (results != null
                    && results.next()) {

                hasRecord = results.getInt("playercount") > 0;

                results.close();

            }

        } catch (Exception ex) {

            ex.printStackTrace();

        } finally {

            try {

                connection.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return hasRecord;

    }

}
