package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.MongoWriteException;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.mongodb.client.model.Filters.*;

public class MongoDBConnector extends BaseDBConnector
{
    private static final String DATABASE_NAME            = "BananoCraft";
    private static final String COLLECTION_USERS         = "users";
    private static final String COLLECTION_OFFLINE_PAYS  = "offlinepayments";
    private static final String COLLECTION_BANKS         = "banks";
    private static final String COLLECTION_BANK_MEMBERS  = "bank_members";

    /** Fail fast instead of the driver's 30s default when the server is unreachable. */
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    /** Logger name used by the MongoDB Java driver for all its components. */
    private static final String DRIVER_LOGGER_NAME = "org.mongodb.driver";

    /**
     * Strong reference to the driver's JUL logger: JUL only holds loggers weakly,
     * so without this the level set in {@link #quietDriverLogging()} could be lost.
     */
    private static final java.util.logging.Logger DRIVER_JUL_LOGGER =
            java.util.logging.Logger.getLogger(DRIVER_LOGGER_NAME);

    private final Plugin plugin;
    private final MongoClient mongoClient;
    private final MongoDatabase db;

    /**
     * Thrown when the MongoDB server cannot be reached (or the URI is invalid)
     * while constructing the connector.
     */
    public static class ConnectionFailedException extends RuntimeException
    {
        public ConnectionFailedException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    /**
     * @throws ConnectionFailedException if the server does not answer a ping
     *                                   within a few seconds
     */
    public MongoDBConnector(Plugin plugin)
    {
        this.plugin      = plugin;
        quietDriverLogging();
        this.mongoClient = connect(getMongoURI());
        this.db          = mongoClient.getDatabase(DATABASE_NAME);
        createUserCollectionIndex();
    }

    private String getMongoURI()
    {
        return this.plugin.getConfig().getString("mongoURI");
    }

    /**
     * Create the client with short timeouts and verify the server is reachable.
     * The client is closed again if the ping fails so no background threads leak.
     */
    private static MongoClient connect(String uri)
    {
        MongoClient client = null;

        try
        {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .build();

            client = MongoClients.create(settings);
            client.getDatabase(DATABASE_NAME).runCommand(new Document("ping", 1));

            return client;
        }
        catch (RuntimeException ex)
        {
            if (client != null)
            {
                try
                {
                    client.close();
                }
                catch (RuntimeException ignored)
                {
                    // already failing; nothing more to do
                }
            }

            // Deliberately does not include the URI: it may contain credentials.
            throw new ConnectionFailedException(
                    "Could not connect to MongoDB using the configured mongoURI: " + ex.getMessage(), ex);
        }
    }

    /**
     * The driver logs every connection / server-monitor event at INFO, which floods
     * the console (issue #12). Raise its threshold to WARN. On Paper the driver logs
     * through the server's SLF4J → Log4j2 binding, so Log4j2 is configured via
     * reflection (log4j-core is not a compile dependency); java.util.logging is set
     * too as a fallback for other setups.
     */
    static void quietDriverLogging()
    {
        try
        {
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            Object warn = levelClass.getField("WARN").get(null);
            Class<?> configurator = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            configurator.getMethod("setLevel", String.class, levelClass).invoke(null, DRIVER_LOGGER_NAME, warn);
        }
        catch (Throwable ignored)
        {
            // Log4j2 core not available — fall through to JUL.
        }

        try
        {
            DRIVER_JUL_LOGGER.setLevel(Level.WARNING);
        }
        catch (Throwable ignored)
        {
            // e.g. SecurityException; noise is preferable to failing startup
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close()
    {
        try
        {
            mongoClient.close();
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Error closing MongoDB client.", ex);
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
        try
        {
            Document document = new Document("_id", playerRecord.getPlayerUUID())
                    .append("name",   playerRecord.getPlayerName())
                    .append("wallet", playerRecord.getWallet())
                    .append("frozen", playerRecord.isFrozen());

            db.getCollection(COLLECTION_USERS).insertOne(document);

            return true;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert player record.", ex);
        }
        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord)
    {
        try
        {
            BasicDBObject searchQuery = new BasicDBObject("_id", playerRecord.getPlayerUUID());

            BasicDBObject updateFields = new BasicDBObject()
                    .append("name",   playerRecord.getPlayerName())
                    .append("wallet", playerRecord.getWallet())
                    .append("frozen", playerRecord.isFrozen());

            BasicDBObject setQuery = new BasicDBObject("$set", updateFields);

            UpdateResult result = db.getCollection(COLLECTION_USERS).updateOne(searchQuery, setQuery);

            // Matched, not modified: saving an unchanged record is still a success.
            return result.getMatchedCount() > 0;
        }
        catch (Exception ex)
        {
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
        String playerUUID = currentPlayer.getUniqueId().toString();

        try
        {
            return db.getCollection(COLLECTION_USERS)
                    .find(and(eq("wallet", walletAddress), not(eq("_id", playerUUID))))
                    .first() != null;
        }
        catch (Exception ex)
        {
            // Fail closed: "not assigned" would let account creation proceed blind.
            throw new IllegalStateException("Database unavailable: could not check wallet assignment.", ex);
        }
    }

    // -------------------------------------------------------------------------
    // IDBConnector — offline payments
    // -------------------------------------------------------------------------

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord)
    {
        try
        {
            String playerUUID = paymentRecord.targetPlayerUUID().toString();

            Document document = new Document("_id", new ObjectId())
                    .append("playerid",       playerUUID)
                    .append("transdate",      paymentRecord.transactionDate().toInstant(ZoneOffset.UTC).toEpochMilli())
                    .append("fromplayername", paymentRecord.fromPlayerName())
                    .append("amount",         paymentRecord.paymentAmount())
                    .append("blockhash",      paymentRecord.blockHash())
                    .append("message",        paymentRecord.message());

            db.getCollection(COLLECTION_OFFLINE_PAYS).insertOne(document);

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

        try
        {
            Document query = new Document("playerid", forPlayer.getUniqueId().toString());
            FindIterable<Document> offlinePayments = db.getCollection(COLLECTION_OFFLINE_PAYS).find(query);

            for (Document document : offlinePayments)
            {
                try
                {
                    paymentRecords.add(new OfflinePaymentRecord(
                            UUID.fromString(document.getString("playerid")),
                            document.getString("fromplayername"),
                            document.getDouble("amount"),
                            document.getString("blockhash"),
                            LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(document.getLong("transdate")), ZoneOffset.UTC),
                            document.getString("message")));
                }
                catch (Exception ex)
                {
                    plugin.getLogger().log(Level.WARNING, "Skipping malformed offline payment document.", ex);
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to read offline payments.", ex);
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

        try
        {
            db.getCollection(COLLECTION_OFFLINE_PAYS)
                    .deleteMany(eq("playerid", forPlayer.getUniqueId().toString()));
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

        double result = 0;

        try
        {
            Document query = new Document("playerid", forPlayer.getUniqueId().toString());
            FindIterable<Document> offlinePayments = db.getCollection(COLLECTION_OFFLINE_PAYS).find(query);

            for (Document document : offlinePayments)
            {
                try
                {
                    result += document.getDouble("amount");
                }
                catch (Exception ex)
                {
                    plugin.getLogger().log(Level.WARNING, "Skipping malformed amount in offline payment document.", ex);
                }
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to total offline payments.", ex);

            return 0;
        }

        return result;
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

        try
        {
            Document doc = db.getCollection(COLLECTION_BANKS).find(eq("_id", bankName)).first();
            if (doc != null)
            {
                BankRecord record = documentToBankRecord(doc);
                bankRecords.put(bankName, record);
                return record;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to read bank record.", ex);
        }

        return null;
    }

    @Override
    public boolean createBankRecord(BankRecord bank)
    {
        try
        {
            Document doc = new Document("_id",       bank.getBankName())
                    .append("address",   bank.getAddress())
                    .append("ownerUuid", bank.getOwnerUuid())
                    .append("createdAt", bank.getCreatedAt());
            db.getCollection(COLLECTION_BANKS).insertOne(doc);
            bankRecords.put(bank.getBankName(), bank);

            return true;
        }
        catch (MongoWriteException ex)
        {
            if (ex.getCode() == 11000)
            {
                // Duplicate _id — bank already exists (race between bankNameExists and insert).
                return false;
            }

            plugin.getLogger().log(Level.SEVERE, "Failed to insert bank record.", ex);
        }
        catch (Exception ex)
        {
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

        try
        {
            return db.getCollection(COLLECTION_BANKS).find(eq("_id", bankName)).first() != null;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check bank name.", ex);

            // Fail closed: reporting "exists" stops a duplicate bank being created.
            return true;
        }
    }

    @Override
    public boolean deleteBankRecord(String bankName)
    {
        try
        {
            DeleteResult result = db.getCollection(COLLECTION_BANKS).deleteOne(eq("_id", bankName));
            db.getCollection(COLLECTION_BANK_MEMBERS).deleteMany(eq("bankName", bankName));
            bankRecords.remove(bankName);
            bankMembers.remove(bankName);

            return result.getDeletedCount() > 0;
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

        try
        {
            for (Document doc : db.getCollection(COLLECTION_BANKS).find())
            {
                names.add(doc.getString("_id"));
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to list bank names.", ex);
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

        boolean isMember;

        try
        {
            isMember = db.getCollection(COLLECTION_BANK_MEMBERS)
                    .find(and(eq("bankName", bankName), eq("playerUuid", playerUuid)))
                    .first() != null;
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check bank membership.", ex);

            return false;
        }

        if (isMember)
        {
            bankMembers.computeIfAbsent(bankName, k -> ConcurrentHashMap.newKeySet())
                       .add(playerUuid);
        }

        return isMember;
    }

    @Override
    public boolean addBankMember(String bankName, String playerUuid)
    {
        try
        {
            if (!isBankMember(bankName, playerUuid))
            {
                Document doc = new Document("bankName", bankName).append("playerUuid", playerUuid);
                db.getCollection(COLLECTION_BANK_MEMBERS).insertOne(doc);
            }

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
        try
        {
            // deleteMany: older versions could insert duplicate membership documents.
            DeleteResult result = db.getCollection(COLLECTION_BANK_MEMBERS)
                    .deleteMany(and(eq("bankName", bankName), eq("playerUuid", playerUuid)));

            if (result.getDeletedCount() > 0)
            {
                Set<String> members = bankMembers.get(bankName);

                if (members != null)
                {
                    members.remove(playerUuid);
                }

                return true;
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to remove bank member.", ex);
        }

        return false;
    }

    private BankRecord documentToBankRecord(Document doc)
    {
        return new BankRecord(
                doc.getString("_id"),
                doc.getString("address"),
                doc.getString("ownerUuid"),
                doc.getLong("createdAt"));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord)
    {
        if (this.playerRecords.containsKey(playerUUID))
        {
            return this.playerRecords.get(playerUUID);
        }

        Document user;

        try
        {
            Document query = new Document("_id", playerUUID.toString());
            user = db.getCollection(COLLECTION_USERS).find(query).first();
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to read player record.", ex);

            return null;
        }

        if (user != null)
        {
            PlayerRecord record = new PlayerRecord(
                    playerUUID.toString(),
                    user.getString("name"),
                    user.getString("wallet"),
                    user.getBoolean("frozen", false));

            if (cacheRecord)
            {
                // One instance per player: a concurrent load must not replace a record that
                // something else (e.g. a freeze) is already using.
                PlayerRecord cached = this.playerRecords.putIfAbsent(playerUUID, record);
                if (cached != null)
                {
                    record = cached;
                }
            }

            return record;
        }

        return null;
    }

    private boolean hasPlayerRecord(UUID playerUUID)
    {
        if (this.playerRecords.containsKey(playerUUID))
        {
            return true;
        }

        try
        {
            Document query = new Document("_id", playerUUID.toString());

            return db.getCollection(COLLECTION_USERS).find(query).first() != null;
        }
        catch (Exception ex)
        {
            // Fail closed: "no record" would make account creation issue a duplicate wallet.
            throw new IllegalStateException("Database unavailable: could not check for player record.", ex);
        }
    }

    private void createUserCollectionIndex()
    {
        try
        {
            db.getCollection(COLLECTION_USERS).createIndex(Indexes.hashed("name"));
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to create users index.", ex);
        }
    }

    private List<PlayerRecord> queryPlayersByFrozen(boolean frozen)
    {
        List<PlayerRecord> records = new ArrayList<>();

        try
        {
            Document query = new Document("frozen", frozen);
            FindIterable<Document> queryResult = db.getCollection(COLLECTION_USERS).find(query);

            for (Document user : queryResult)
            {
                records.add(new PlayerRecord(
                        user.getString("_id"),
                        user.getString("name"),
                        user.getString("wallet"),
                        user.getBoolean("frozen", false)));
            }
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to query players by frozen state.", ex);
        }

        return records;
    }
}
