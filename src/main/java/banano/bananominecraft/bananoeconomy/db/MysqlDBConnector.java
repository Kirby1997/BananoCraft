package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MysqlDBConnector extends BaseDBConnector
{
    private static final String TABLE_USERS            = "users";
    private static final String TABLE_OFFLINE_PAYMENTS = "offlinepayments";
    private static final String TABLE_BANKS            = "banks";
    private static final String TABLE_BANK_MEMBERS     = "bank_members";

    private static final String SQL_CREATE_BANKS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_BANKS + " (" +
            "    bank_name   VARCHAR(100) NOT NULL," +
            "    address     VARCHAR(100) NOT NULL," +
            "    owner_uuid  VARCHAR(75)  NOT NULL," +
            "    created_at  BIGINT       NOT NULL," +
            "    walletIndex INT          NULL," +
            "    PRIMARY KEY (bank_name)" +
            ") ENGINE=INNODB";

    private static final String SQL_CREATE_BANK_MEMBERS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_BANK_MEMBERS + " (" +
            "    bank_name   VARCHAR(100) NOT NULL," +
            "    player_uuid VARCHAR(75)  NOT NULL," +
            "    PRIMARY KEY (bank_name, player_uuid)" +
            ") ENGINE=INNODB";

    private static final String SQL_CREATE_USERS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (" +
            "    playerUUID  VARCHAR(75)  NOT NULL UNIQUE," +
            "    name        VARCHAR(50)  NOT NULL," +
            "    wallet      VARCHAR(100) NOT NULL," +
            "    frozen      BOOLEAN      NOT NULL DEFAULT FALSE," +
            "    walletIndex INT          NULL," +
            "    PRIMARY KEY (playerUUID)" +
            ") ENGINE=INNODB";

    private static final String SQL_CREATE_OFFLINE_PAYMENTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_OFFLINE_PAYMENTS + " (" +
            "    id             INT         NOT NULL AUTO_INCREMENT," +
            "    playerUUID     VARCHAR(75) NOT NULL," +
            "    fromplayername VARCHAR(50) NOT NULL," +
            "    amount         DOUBLE      NOT NULL," +
            "    blockhash      VARCHAR(250) NOT NULL," +
            "    message        VARCHAR(250) NOT NULL," +
            "    transdate      TIMESTAMP   NOT NULL," +
            "    PRIMARY KEY (id)" +
            ") ENGINE=INNODB";

    /** Seed-derivation index column on users and banks; NULL for legacy rows. */
    private static final String COLUMN_WALLET_INDEX = "walletIndex";

    private static final String SQL_ADD_USERS_WALLET_INDEX =
            "ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_WALLET_INDEX + " INT NULL";

    private static final String SQL_ADD_BANKS_WALLET_INDEX =
            "ALTER TABLE " + TABLE_BANKS + " ADD COLUMN " + COLUMN_WALLET_INDEX + " INT NULL";

    /**
     * UNIQUE indexes that make two records owning the same wallet address / derivation index impossible,
     * even with several servers on one database. {@code walletIndex} may be NULL on many rows (legacy).
     */
    static final String INDEX_USERS_WALLET       = "uq_users_wallet";
    static final String INDEX_USERS_WALLET_INDEX = "uq_users_walletIndex";
    static final String INDEX_BANKS_ADDRESS      = "uq_banks_address";
    static final String INDEX_BANKS_WALLET_INDEX = "uq_banks_walletIndex";

    private static final int MAX_LISTED_DUPLICATES = 20;

    private final Plugin plugin;
    private final HikariDataSource dataSource;

    public MysqlDBConnector(Plugin plugin)
    {
        this.plugin     = plugin;
        this.dataSource = new HikariDataSource();
        initialiseDataSource();
        setupDatabase();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close()
    {
        try
        {
            this.dataSource.close();
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Error closing MySQL data source.", ex);
        }
    }

    // -------------------------------------------------------------------------
    // IDBConnector — player records
    // -------------------------------------------------------------------------

    @Override
    protected PlayerRecord loadPlayerRecord(Player player)
    {
        return getPlayerRecord(player.getUniqueId(), true);
    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player)
    {
        return getPlayerRecord(player.getUniqueId(), false);
    }

    @Override
    protected boolean insertPlayerRecord(PlayerRecord playerRecord)
    {
        final String sql = "INSERT INTO " + TABLE_USERS +
                           " (playerUUID, name, wallet, frozen, walletIndex) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerRecord.getPlayerUUID());
            ps.setString(2, playerRecord.getPlayerName());
            ps.setString(3, playerRecord.getWallet());
            ps.setBoolean(4, playerRecord.isFrozen());
            setNullableInt(ps, 5, playerRecord.getWalletIndex());

            return ps.executeUpdate() > 0;
        }
        catch (Exception ex)
        {
            if (isDuplicateWalletViolation(ex))
            {
                throw new DuplicateWalletException("Wallet address or index already taken: " + ex.getMessage(), ex);
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to insert player record.", ex);
        }
        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord)
    {
        final String sql = "UPDATE " + TABLE_USERS +
                           " SET name = ?, wallet = ?, frozen = ?, " + COLUMN_WALLET_INDEX
                         + " = COALESCE(?, " + COLUMN_WALLET_INDEX + ") WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerRecord.getPlayerName());
            ps.setString(2, playerRecord.getWallet());
            ps.setBoolean(3, playerRecord.isFrozen());
            // COALESCE: a null (unknown) index never erases a stored one.
            setNullableInt(ps, 4, playerRecord.getWalletIndex());
            ps.setString(5, playerRecord.getPlayerUUID());

            return ps.executeUpdate() > 0;
        }
        catch (Exception ex)
        {
            if (isDuplicateWalletViolation(ex))
            {
                throw new DuplicateWalletException("Wallet address or index already taken: " + ex.getMessage(), ex);
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to update player record.", ex);
        }
        return false;
    }

    @Override
    public boolean hasPlayerRecord(Player player)
    {
        return hasPlayerRecord(player.getUniqueId());
    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player)
    {
        return hasPlayerRecord(player.getUniqueId());
    }

    @Override
    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer)
    {
        final String sql = "SELECT COUNT(playerUUID) AS playercount FROM " + TABLE_USERS +
                           " WHERE playerUUID <> ? AND wallet = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, currentPlayer.getUniqueId().toString());
            ps.setString(2, walletAddress);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt("playercount") > 0;
            }
        }
        catch (Exception ex)
        {
            // Fail closed: "not assigned" would let account creation proceed blind.
            throw new IllegalStateException("Database unavailable: could not check wallet assignment.", ex);
        }
    }

    // -------------------------------------------------------------------------
    // IDBConnector — wallet derivation indices
    // -------------------------------------------------------------------------

    @Override
    public int getMaxPlayerWalletIndex()
    {
        return maxIndex(TABLE_USERS);
    }

    @Override
    public int getMaxBankWalletIndex()
    {
        return maxIndex(TABLE_BANKS);
    }

    private int maxIndex(String table)
    {
        final String sql = "SELECT MAX(" + COLUMN_WALLET_INDEX + ") AS maxindex FROM " + table;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery())
        {
            if (rs.next())
            {
                Integer max = readNullableInt(rs, "maxindex");
                return max == null ? 0 : Math.max(0, max);
            }
            return 0;
        }
        catch (Exception ex)
        {
            // Fail closed: guessing could make account creation re-issue an index.
            throw new IllegalStateException("Database unavailable: could not read the highest wallet index.", ex);
        }
    }

    @Override
    public Integer getPlayerWalletIndex(String address)
    {
        return indexOfAddress(TABLE_USERS, "wallet", address);
    }

    @Override
    public Integer getBankWalletIndex(String address)
    {
        return indexOfAddress(TABLE_BANKS, "address", address);
    }

    private Integer indexOfAddress(String table, String addressColumn, String address)
    {
        if (address == null)
        {
            return null;
        }

        final String sql = "SELECT " + COLUMN_WALLET_INDEX + " FROM " + table + " WHERE " + addressColumn + " = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, address);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? readNullableInt(rs, COLUMN_WALLET_INDEX) : null;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to read wallet index.", ex);
        }
        return null;
    }

    @Override
    public List<PlayerRecord> getPlayerRecordsMissingWalletIndex()
    {
        List<PlayerRecord> records = new ArrayList<>();

        final String sql = "SELECT playerUUID, name, wallet, frozen FROM " + TABLE_USERS
                         + " WHERE " + COLUMN_WALLET_INDEX + " IS NULL";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                records.add(new PlayerRecord(
                        rs.getString("playerUUID"),
                        rs.getString("name"),
                        rs.getString("wallet"),
                        rs.getBoolean("frozen")));
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to query players without a wallet index.", ex);
        }

        return records;
    }

    @Override
    protected boolean persistPlayerWalletIndex(String playerUuid, int walletIndex)
    {
        return persistIndex(TABLE_USERS, "playerUUID", playerUuid, walletIndex);
    }

    @Override
    protected boolean persistBankWalletIndex(String bankName, int walletIndex)
    {
        return persistIndex(TABLE_BANKS, "bank_name", bankName, walletIndex);
    }

    private boolean persistIndex(String table, String keyColumn, String key, int walletIndex)
    {
        final String sql = "UPDATE " + table + " SET " + COLUMN_WALLET_INDEX + " = ? WHERE " + keyColumn + " = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, walletIndex);
            ps.setString(2, key);

            return ps.executeUpdate() > 0;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to store wallet index.", ex);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // IDBConnector — offline payments
    // -------------------------------------------------------------------------

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord)
    {
        final String sql = "INSERT INTO " + TABLE_OFFLINE_PAYMENTS +
                           " (playerUUID, fromplayername, amount, blockhash, message, transdate)" +
                           " VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, paymentRecord.targetPlayerUUID().toString());
            ps.setString(2, paymentRecord.fromPlayerName());
            ps.setDouble(3, paymentRecord.paymentAmount());
            ps.setString(4, paymentRecord.blockHash());
            ps.setString(5, paymentRecord.message());
            ps.setTimestamp(6, Timestamp.valueOf(paymentRecord.transactionDate()));

            ps.executeUpdate();

            return true;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to save offline payment.", ex);
        }

        return false;
    }

    @Override
    public List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer)
    {
        List<OfflinePaymentRecord> paymentRecords = new ArrayList<>();
        if (forPlayer == null)
        {
            return paymentRecords;
        }

        final String sql = "SELECT playerUUID, fromplayername, amount, blockhash, message, transdate" +
                           " FROM " + TABLE_OFFLINE_PAYMENTS + " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, forPlayer.getUniqueId().toString());

            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    try
                    {
                        paymentRecords.add(new OfflinePaymentRecord(
                                UUID.fromString(rs.getString("playerUUID")),
                                rs.getString("fromplayername"),
                                rs.getDouble("amount"),
                                rs.getString("blockhash"),
                                rs.getTimestamp("transdate").toLocalDateTime(),
                                rs.getString("message")));
                    }
                    catch (Exception ex)
                    {
                        plugin.getLogger().log(Level.WARNING, "Skipping malformed offline payment row.", ex);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to load offline payments.", ex);
        }
        return paymentRecords;
    }

    @Override
    public void deleteOfflinePaymentRecords(Player forPlayer)
    {
        if (forPlayer == null)
        {
            return;
        }

        final String sql = "DELETE FROM " + TABLE_OFFLINE_PAYMENTS + " WHERE playerUUID = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, forPlayer.getUniqueId().toString());
            ps.executeUpdate();
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to delete offline payments.", ex);
        }
    }

    @Override
    public double getOfflinePaymentsTotal(Player forPlayer)
    {
        if (forPlayer == null)
        {
            return 0;
        }

        final String sql = "SELECT SUM(amount) AS total FROM " + TABLE_OFFLINE_PAYMENTS +
                           " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, forPlayer.getUniqueId().toString());

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getDouble("total");
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to sum offline payments.", ex);
        }

        return 0;
    }

    // -------------------------------------------------------------------------
    // IDBConnector — freeze queries
    // -------------------------------------------------------------------------

    @Override
    public List<PlayerRecord> getFrozenPlayers()
    {
        return queryPlayersByFrozen(true);
    }

    @Override
    public List<PlayerRecord> getUnfrozenPlayers()
    {
        return queryPlayersByFrozen(false);
    }

    // -------------------------------------------------------------------------
    // IDBConnector — bank accounts
    // -------------------------------------------------------------------------

    @Override
    public BankRecord getBankRecord(String bankName)
    {
        BankRecord cached = bankRecords.get(bankName);
        if (cached != null)
        {
            return cached;
        }

        final String sql = "SELECT bank_name, address, owner_uuid, created_at, " + COLUMN_WALLET_INDEX
                         + " FROM " + TABLE_BANKS
                         + " WHERE bank_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, bankName);

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    BankRecord record = new BankRecord(
                            rs.getString("bank_name"),
                            rs.getString("address"),
                            rs.getString("owner_uuid"),
                            rs.getLong("created_at"),
                            readNullableInt(rs, COLUMN_WALLET_INDEX));
                    bankRecords.put(bankName, record);

                    return record;
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bank record.", ex);
        }

        return null;
    }

    @Override
    public boolean createBankRecord(BankRecord bank)
    {
        final String sql = "INSERT INTO " + TABLE_BANKS
                         + " (bank_name, address, owner_uuid, created_at, " + COLUMN_WALLET_INDEX
                         + ") VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, bank.getBankName());
            ps.setString(2, bank.getAddress());
            ps.setString(3, bank.getOwnerUuid());
            ps.setLong(4,   bank.getCreatedAt());
            setNullableInt(ps, 5, bank.getWalletIndex());
            boolean success = ps.executeUpdate() > 0;

            if (success)
            {
                bankRecords.put(bank.getBankName(), bank);
            }

            return success;
        }
        catch (Exception ex)
        {
            if (isDuplicateWalletViolation(ex))
            {
                throw new DuplicateWalletException("Bank address or index already taken: " + ex.getMessage(), ex);
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to insert bank record.", ex);
        }

        return false;
    }

    @Override
    public boolean bankNameExists(String bankName)
    {
        if (bankRecords.containsKey(bankName))
        {
            return true;
        }

        final String sql = "SELECT COUNT(bank_name) AS cnt FROM " + TABLE_BANKS + " WHERE bank_name = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, bankName);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt("cnt") > 0;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check bank existence.", ex);
        }

        return false;
    }

    @Override
    public boolean deleteBankRecord(String bankName)
    {
        try (Connection conn = getConnection())
        {
            conn.setAutoCommit(false);

            try
            {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + TABLE_BANK_MEMBERS + " WHERE bank_name = ?"))
                {
                    ps.setString(1, bankName);
                    ps.executeUpdate();
                }

                boolean success;
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + TABLE_BANKS + " WHERE bank_name = ?"))
                {
                    ps.setString(1, bankName);
                    success = ps.executeUpdate() > 0;
                }

                conn.commit();

                if (success)
                {
                    bankRecords.remove(bankName);
                    bankMembers.remove(bankName);
                }

                return success;
            }
            catch (Exception ex)
            {
                conn.rollback();
                throw ex;
            }
            finally
            {
                conn.setAutoCommit(true);
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete bank record.", ex);
        }

        return false;
    }

    @Override
    public List<String> getAllBankNames()
    {
        List<String> names = new ArrayList<>();
        final String sql = "SELECT bank_name FROM " + TABLE_BANKS;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery())
        {
            while (rs.next()) names.add(rs.getString("bank_name"));
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to list banks.", ex);
        }

        return names;
    }

    @Override
    public boolean isBankOwner(String bankName, String playerUuid)
    {
        BankRecord bank = getBankRecord(bankName);

        return bank != null && bank.getOwnerUuid().equals(playerUuid);
    }

    @Override
    public boolean isBankMember(String bankName, String playerUuid)
    {
        // A non-null cache set is a write-through partial view — it can give a
        // definitive YES (member was added this session) but NOT a definitive NO
        // (members from a previous server session aren't pre-loaded).
        Set<String> cached = bankMembers.get(bankName);

        if (cached != null && cached.contains(playerUuid))
        {
            return true;
        }

        final String sql = "SELECT COUNT(*) AS cnt FROM " + TABLE_BANK_MEMBERS
                         + " WHERE bank_name = ? AND player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, bankName);
            ps.setString(2, playerUuid);

            try (ResultSet rs = ps.executeQuery())
            {
                boolean isMember = rs.next() && rs.getInt("cnt") > 0;

                if (isMember)
                {
                    bankMembers.computeIfAbsent(bankName, k -> ConcurrentHashMap.newKeySet())
                               .add(playerUuid);
                }

                return isMember;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check bank membership.", ex);
        }

        return false;
    }

    @Override
    public boolean addBankMember(String bankName, String playerUuid)
    {
        final String sql = "INSERT IGNORE INTO " + TABLE_BANK_MEMBERS
                         + " (bank_name, player_uuid) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, bankName);
            ps.setString(2, playerUuid);
            ps.executeUpdate();
            bankMembers.computeIfAbsent(bankName, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);

            return true;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to add bank member.", ex);
        }

        return false;
    }

    @Override
    public boolean removeBankMember(String bankName, String playerUuid)
    {
        final String sql = "DELETE FROM " + TABLE_BANK_MEMBERS
                         + " WHERE bank_name = ? AND player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, bankName);
            ps.setString(2, playerUuid);
            boolean success = ps.executeUpdate() > 0;

            if (success)
            {
                Set<String> members = bankMembers.get(bankName);

                if (members != null)
                {
                    members.remove(playerUuid);
                }
            }

            return success;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to remove bank member.", ex);
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void initialiseDataSource()
    {
        try
        {
            FileConfiguration config = this.plugin.getConfig();

            String serverName   = config.getString("mysqlServerName");
            int    port         = config.getInt("mysqlPort", 3306);
            String databaseName = config.getString("mysqlDatabaseName");
            String userName     = config.getString("mysqlUsername");
            String password     = config.getString("mysqlPassword");

            this.dataSource.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
            this.dataSource.addDataSourceProperty("serverName",   serverName);
            this.dataSource.addDataSourceProperty("port",         port);
            this.dataSource.addDataSourceProperty("databaseName", databaseName);
            this.dataSource.addDataSourceProperty("user",         userName);
            this.dataSource.addDataSourceProperty("password",     password);
            this.dataSource.setMaximumPoolSize(10);
            this.dataSource.setMinimumIdle(5);
            this.dataSource.setIdleTimeout(45_000);
            this.dataSource.setMaxLifetime(60_000);
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise MySQL data source.", ex);
        }
    }

    /** Creates the schema tables on first startup. */
    private void setupDatabase()
    {
        try (Connection conn = getConnection())
        {
            try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE_USERS))
            {
                ps.execute();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to create '" + TABLE_USERS + "' table.", ex);
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE_OFFLINE_PAYMENTS))
            {
                ps.execute();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to create '" + TABLE_OFFLINE_PAYMENTS + "' table.", ex);
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE_BANKS))
            {
                ps.execute();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to create '" + TABLE_BANKS + "' table.", ex);
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_CREATE_BANK_MEMBERS))
            {
                ps.execute();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to create '" + TABLE_BANK_MEMBERS + "' table.", ex);
            }

            // Tables created by older versions have no walletIndex column.
            addColumnIfMissing(conn, TABLE_USERS, COLUMN_WALLET_INDEX, SQL_ADD_USERS_WALLET_INDEX);
            addColumnIfMissing(conn, TABLE_BANKS, COLUMN_WALLET_INDEX, SQL_ADD_BANKS_WALLET_INDEX);

            // Two servers (or a bug) must never own the same wallet address or derivation index.
            addUniqueIndexIfMissing(conn, TABLE_USERS, INDEX_USERS_WALLET, "wallet");
            addUniqueIndexIfMissing(conn, TABLE_USERS, INDEX_USERS_WALLET_INDEX, COLUMN_WALLET_INDEX);
            addUniqueIndexIfMissing(conn, TABLE_BANKS, INDEX_BANKS_ADDRESS, "address");
            addUniqueIndexIfMissing(conn, TABLE_BANKS, INDEX_BANKS_WALLET_INDEX, COLUMN_WALLET_INDEX);
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Database setup failed — no connection available.", ex);
        }
    }

    /** Runs {@code alterSql} (ADD COLUMN ... NULL) unless {@code table} already has {@code column}. */
    private void addColumnIfMissing(Connection conn, String table, String column, String alterSql)
    {
        try
        {
            boolean present;
            try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column))
            {
                present = rs.next();
            }

            if (!present)
            {
                try (PreparedStatement ps = conn.prepareStatement(alterSql))
                {
                    ps.execute();
                }
                plugin.getLogger().info("Added column '" + column + "' to the '" + table + "' table.");
            }
        }
        catch (SQLException ex)
        {
            // 1060 = duplicate column name: another server sharing this database added it first.
            if (ex.getErrorCode() != 1060)
            {
                plugin.getLogger().log(Level.SEVERE, "Failed to add '" + column + "' column to '" + table + "'.", ex);
            }
        }
    }

    /**
     * Creates {@code UNIQUE INDEX indexName (column)} unless it already exists. If it cannot be created
     * because existing rows already share a value, the duplicates are listed at SEVERE and startup
     * continues without the constraint (nothing is deleted or changed).
     */
    private void addUniqueIndexIfMissing(Connection conn, String table, String indexName, String column)
    {
        try
        {
            boolean present;
            try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, table, true, false))
            {
                present = false;
                while (rs.next())
                {
                    if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME")))
                    {
                        present = true;
                        break;
                    }
                }
            }

            if (present)
            {
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "ALTER TABLE " + table + " ADD UNIQUE INDEX " + indexName + " (" + column + ")"))
            {
                ps.execute();
            }
            plugin.getLogger().info("Added unique index '" + indexName + "' on " + table + "(" + column + ").");
        }
        catch (SQLException ex)
        {
            // 1061 = duplicate key name: another server sharing this database created it first.
            if (ex.getErrorCode() == 1061)
            {
                return;
            }

            plugin.getLogger().log(Level.SEVERE, "Could not create the unique index '" + indexName + "' on "
                    + table + "(" + column + ") - the multi-server duplicate protection is NOT active for it."
                    + " Existing duplicates: " + describeDuplicates(conn, table, column), ex);
        }
    }

    /** Values of {@code column} shared by more than one row, for the SEVERE log; never throws. */
    private static String describeDuplicates(Connection conn, String table, String column)
    {
        final String sql = "SELECT " + column + " AS v, COUNT(*) AS c FROM " + table
                         + " WHERE " + column + " IS NOT NULL GROUP BY " + column + " HAVING COUNT(*) > 1";
        StringBuilder out = new StringBuilder();
        int count = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                if (count++ < MAX_LISTED_DUPLICATES)
                {
                    out.append(out.length() > 0 ? "; " : "").append(rs.getString("v"))
                            .append(" x").append(rs.getInt("c"));
                }
            }
        }
        catch (SQLException e)
        {
            return "(could not list: " + e.getMessage() + ")";
        }

        if (count > MAX_LISTED_DUPLICATES)
        {
            out.append("; ... and ").append(count - MAX_LISTED_DUPLICATES).append(" more");
        }
        return count == 0 ? "(none found - see the error)" : out.toString();
    }

    /**
     * True if {@code ex} (or a cause) is a unique-key violation on one of the wallet address / index
     * indexes (as opposed to e.g. a duplicate primary key).
     */
    static boolean isDuplicateWalletViolation(Throwable ex)
    {
        for (Throwable t = ex; t != null; t = t.getCause())
        {
            if (t instanceof SQLException sql)
            {
                boolean duplicateKey = sql.getErrorCode() == 1062 || "23000".equals(sql.getSQLState())
                        || t instanceof java.sql.SQLIntegrityConstraintViolationException;
                String message = sql.getMessage();

                if (duplicateKey && message != null
                        && (message.contains(INDEX_USERS_WALLET) || message.contains(INDEX_USERS_WALLET_INDEX)
                        || message.contains(INDEX_BANKS_ADDRESS) || message.contains(INDEX_BANKS_WALLET_INDEX)))
                {
                    return true;
                }
            }
            if (t.getCause() == t)
            {
                break;
            }
        }
        return false;
    }

    private static void setNullableInt(PreparedStatement ps, int position, Integer value) throws SQLException
    {
        if (value == null)
        {
            ps.setNull(position, Types.INTEGER);
        }
        else
        {
            ps.setInt(position, value);
        }
    }

    private static Integer readNullableInt(ResultSet rs, String column) throws SQLException
    {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : Integer.valueOf(value);
    }

    /**
     * Returns a connection from the pool.
     *
     * @throws SQLException if the pool cannot provide a connection
     */
    private Connection getConnection() throws SQLException
    {
        return this.dataSource.getConnection();
    }

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord)
    {
        PlayerRecord cached = playerRecords.get(playerUUID);
        if (cached != null)
        {
            return cached;
        }

        final String sql = "SELECT playerUUID, name, wallet, frozen, " + COLUMN_WALLET_INDEX + " FROM " + TABLE_USERS +
                           " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerUUID.toString());

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    PlayerRecord record = new PlayerRecord(
                            rs.getString("playerUUID"),
                            rs.getString("name"),
                            rs.getString("wallet"),
                            rs.getBoolean("frozen"),
                            readNullableInt(rs, COLUMN_WALLET_INDEX));

                    if (cacheRecord)
                    {
                        // One instance per player: a concurrent load must not replace a record that
                        // something else (e.g. a freeze) is already using.
                        PlayerRecord existing = playerRecords.putIfAbsent(playerUUID, record);
                        if (existing != null)
                        {
                            record = existing;
                        }
                    }

                    return record;
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player record.", ex);
        }

        return null;
    }

    private boolean hasPlayerRecord(UUID playerUUID)
    {
        if (playerRecords.containsKey(playerUUID))
        {
            return true;
        }

        final String sql = "SELECT COUNT(playerUUID) AS playercount FROM " + TABLE_USERS +
                           " WHERE playerUUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerUUID.toString());

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() && rs.getInt("playercount") > 0;
            }
        }
        catch (Exception ex)
        {
            // Fail closed: "no record" would make account creation issue a duplicate wallet.
            throw new IllegalStateException("Database unavailable: could not check player record existence.", ex);
        }
    }

    private List<PlayerRecord> queryPlayersByFrozen(boolean frozen)
    {
        List<PlayerRecord> records = new ArrayList<>();

        final String sql = "SELECT playerUUID, name, wallet, frozen, " + COLUMN_WALLET_INDEX + " FROM " + TABLE_USERS +
                           " WHERE frozen = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setBoolean(1, frozen);

            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    records.add(new PlayerRecord(
                            rs.getString("playerUUID"),
                            rs.getString("name"),
                            rs.getString("wallet"),
                            rs.getBoolean("frozen"),
                            readNullableInt(rs, COLUMN_WALLET_INDEX)));
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to query players by frozen=" + frozen + ".", ex);
        }

        return records;
    }
}
