package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
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
            int port = 3306;

            if(config.isInt("mysqlPort")) {

                port = config.getInt("mysqlPort");

            } else {

                port = Integer.parseInt(config.getString("mysqlPort"));

            }

            String databaseName = config.getString("mysqlDatabaseName");
            String userName = config.getString("mysqlUsername");
            String password = config.getString("mysqlPassword");

            this.dataSource.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            this.dataSource.addDataSourceProperty("serverName", serverName);
            this.dataSource.addDataSourceProperty("port", port);
            this.dataSource.addDataSourceProperty("databaseName", databaseName);
            this.dataSource.addDataSourceProperty("user", userName);
            this.dataSource.addDataSourceProperty("password", password);
            this.dataSource.setIdleTimeout(45000);
            this.dataSource.setMaxLifetime(60000);
            this.dataSource.setMinimumIdle(5);

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
                            "    frozen         BOOLEAN NOT NULL DEFAULT FALSE, " +
                            "    PRIMARY KEY (playerUUID) " +
                            ")  ENGINE=INNODB";

        try {

            PreparedStatement userTableCreator = connection.prepareStatement(usersTable);

            userTableCreator.execute();
            userTableCreator.close();

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        String offlinePaymentsTable = "CREATE TABLE IF NOT EXISTS offlinepayments (" +
                "    playerUUID     VARCHAR(75) NOT NULL UNIQUE," +
                "    fromplayername VARCHAR(50) NOT NULL," +
                "    amount         DOUBLE NOT NULL," +
                "    blockhash      VARCHAR(250) NOT NULL, " +
                "    message        VARCHAR(250) NOT NULL, " +
                "    transdate      TIMESTAMP NOT NULL, " +
                "    PRIMARY KEY (playerUUID) " +
                ")  ENGINE=INNODB";

        try {

            PreparedStatement offlinePaymentsTableCreator = connection.prepareStatement(offlinePaymentsTable);

            offlinePaymentsTableCreator.execute();
            offlinePaymentsTableCreator.close();

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


                }

                try {
                    results.close();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }

                try {
                    query.close();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
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

            insert.close();

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

            insert.close();

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


            }

            try {
                results.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                query.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
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


            }

            try {
                results.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                query.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
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

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord) {

        boolean result = false;

        Connection connection = getConnection();

        try {

            PreparedStatement insert = connection.prepareStatement("INSERT INTO offlinepayments (playerUUID, fromplayername, amount, blockhash, message, transdate) " +
                                                                       "VALUES (?, ?, ?, ?, ?, ?)");

            insert.setString(1, paymentRecord.getTargetPlayerUUID().toString());
            insert.setString(2, paymentRecord.getFromPlayerName());
            insert.setDouble(3, paymentRecord.getPaymentAmount());
            insert.setString(4, paymentRecord.getBlockHash());
            insert.setString(5, paymentRecord.getMessage());
            insert.setTimestamp(6, Timestamp.valueOf(paymentRecord.getTransactionDate()));

            insert.executeUpdate();
            insert.close();

            result = true;

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

        return result;

    }

    @Override
    public List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer) {

        List<OfflinePaymentRecord> paymentRecords = new ArrayList<>();

        if(forPlayer == null) {

            return paymentRecords;

        }

        Connection connection = getConnection();

        try {

            PreparedStatement query = connection.prepareStatement("SELECT playerUUID, fromplayername, amount, blockhash, message, transdate  " +
                                                                      "FROM offlinepayments " +
                                                                      "WHERE playerUUID = ?");

            query.setString(1, forPlayer.getUniqueId().toString());

            ResultSet results = query.executeQuery();

            if(results != null) {

                while (results.next()) {

                    try {

                        OfflinePaymentRecord paymentRecord = new OfflinePaymentRecord(UUID.fromString(results.getString("playerUUID")),
                                                                                        results.getString("fromplayername"),
                                                                                        results.getDouble("amount"),
                                                                                        results.getString("blockhash"),
                                                                                        results.getTimestamp("transdate").toLocalDateTime(),
                                                                                        results.getString("message"));

                        paymentRecords.add(paymentRecord);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }

            }

            try {
                results.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                query.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {

            try {

                connection.close();

            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return paymentRecords;

    }

    @Override
    public void deleteOfflinePaymentRecords(Player forPlayer) {

        if(forPlayer == null) {
            return;
        }

        Connection connection = getConnection();

        try {

            PreparedStatement delete = connection.prepareStatement("DELETE FROM offlinepayments " +
                                                                       "WHERE playerUUID = ?");

            delete.setString(1, forPlayer.getUniqueId().toString());

            delete.executeUpdate();

            delete.close();

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {

            try {

                connection.close();

            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }

    }

    @Override
    public double getOfflinePaymentsTotal(Player forPlayer) {

        double result = 0;

        if(forPlayer == null) {

            return result;

        }

        Connection connection = getConnection();

        try {

            PreparedStatement query = connection.prepareStatement("SELECT SUM(amount) AS total " +
                                                                      "FROM offlinepayments " +
                                                                      "WHERE playerUUID = ?");

            query.setString(1, forPlayer.getUniqueId().toString());

            ResultSet results = query.executeQuery();

            if(results != null) {

                while (results.next()) {

                    try {

                        result = results.getDouble("total");

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }

            }

            try {
                results.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            try {
                query.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {

            try {

                connection.close();

            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return result;

    }

}
