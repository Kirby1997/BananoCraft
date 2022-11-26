package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.eq;

public class MongoDBConnector extends BaseDBConnector {

    private final Plugin plugin;
    private final MongoClient mongoClient;
    private final MongoDatabase db;

    public MongoDBConnector(Plugin plugin) {

        this.plugin = plugin;
        this.mongoClient = MongoClients.create(getMongoURI());

        this.db = mongoClient.getDatabase("BananoCraft");

        createUserCollectionIndex();

    }

    private String getMongoURI(){

        return this.plugin.getConfig().getString("mongoURI");

    }

    @Override
    public void close() {

        try {

            mongoClient.close();

        }
        catch (Exception ex) {

        }

    }

    @Override
    protected PlayerRecord loadPlayerRecord(Player player) {

        return getPlayerRecord(player.getUniqueId(), true);

    }

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord) {

        if(this.playerRecords.containsKey(playerUUID)) {

            return this.playerRecords.get(playerUUID);

        }
        else {

            Document query = new Document("_id", playerUUID.toString());
            Document user = db.getCollection("users").find(query).first();

            if (user != null) {

                String playerName = user.getString("name");
                String walletAddress = user.getString("wallet");
                boolean isFrozen = user.getBoolean("frozen");

                PlayerRecord playerRecord = new PlayerRecord(playerUUID.toString(), playerName, walletAddress, isFrozen);

                if (cacheRecord
                        && !this.playerRecords.containsKey(playerUUID)) {

                    this.playerRecords.put(playerUUID, playerRecord);

                }

                return playerRecord;

            }

        }

        return null;

    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player) {

        return getPlayerRecord(player.getUniqueId(), false);

    }

    @Override
    protected boolean insertPlayerRecord(PlayerRecord playerRecord) {

        try {

            Document document = new Document("_id", playerRecord.getPlayerUUID())
                    .append("name", playerRecord.getPlayerName())
                    .append("wallet", playerRecord.getWallet())
                    .append("frozen", playerRecord.isFrozen());

            db.getCollection("users").insertOne(document);

            return true;

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord) {

        try {

            String playerUUID = playerRecord.getPlayerUUID();

            BasicDBObject searchQuery = new BasicDBObject("_id", playerUUID);

            BasicDBObject updateFields = new BasicDBObject();
            updateFields.append("frozen", playerRecord.isFrozen());

            BasicDBObject setQuery = new BasicDBObject();
            setQuery.append("$set", updateFields);

            UpdateResult updateResult = this.db.getCollection("users").updateMany(searchQuery, setQuery);

            return updateResult.getModifiedCount() > 0;

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
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

        if(this.playerRecords.containsKey(playerUUID)) {

            return true;

        }

        Document query = new Document("_id", playerUUID.toString());
        return db.getCollection("users").find(query).first() != null;

    }

    private void createUserCollectionIndex() {
        this.db.getCollection("users").createIndex(Indexes.hashed("name"));
    }

    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer) {

        String playerUUID = currentPlayer.getUniqueId().toString();

        try {
            return db.getCollection("users")
                    .find(and(eq("wallet", walletAddress), not(eq("_id", playerUUID)))).first() != null;
        }
        catch (Exception e) {
            return false;
        }

    }

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord) {

        try {

            String playerUUID = paymentRecord.getTargetPlayerUUID().toString();

            Document document = new Document("_id", new ObjectId())
                    .append("playerid", playerUUID)
                    .append("transdate", paymentRecord.getTransactionDate().toInstant(ZoneOffset.UTC).toEpochMilli())
                    .append("fromplayername", paymentRecord.getFromPlayerName())
                    .append("amount", paymentRecord.getPaymentAmount())
                    .append("blockhash", paymentRecord.getBlockHash())
                    .append("message", paymentRecord.getMessage());

            db.getCollection("offlinepayments").insertOne(document);

            return true;

        }
        catch (Exception ex) {

            ex.printStackTrace();

        }

        return false;
    }

    @Override
    public List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer) {

        List<OfflinePaymentRecord> paymentRecords = new ArrayList<>();

        if(forPlayer != null) {

            Document query = new Document("playerid", forPlayer.getUniqueId().toString());
            FindIterable<Document> offlinePayments = db.getCollection("offlinepayments").find(query);

            for (Document document: offlinePayments) {

                try {

                    OfflinePaymentRecord paymentRecord = new OfflinePaymentRecord(
                        UUID.fromString(document.getString("playerid")),
                        document.getString("fromplayername"),
                        document.getDouble("amount"),
                        document.getString("blockhash"),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(document.getLong("transdate")), ZoneOffset.UTC),
                        document.getString("message")
                    );

                    paymentRecords.add(paymentRecord);

                }
                catch (Exception ex) {

                    ex.printStackTrace();

                }

            }

        }

        return paymentRecords;
    }

    @Override
    public void deleteOfflinePaymentRecords(Player forPlayer) {

        if(forPlayer == null) {

            return;

        }

        try {

            this.db.getCollection("offlinepayments")
                    .deleteMany(eq("playerid", forPlayer.getUniqueId().toString()));

        }
        catch (Exception e) {

            e.printStackTrace();

        }

    }

    @Override
    public double getOfflinePaymentsTotal(Player forPlayer) {

        double result = 0;

        if(forPlayer != null) {

            Document query = new Document("playerid", forPlayer.getUniqueId().toString());
            FindIterable<Document> offlinePayments = db.getCollection("offlinepayments").find(query);

            for(Document document : offlinePayments) {

                try {

                    result += document.getDouble("amount");

                }
                catch (Exception ex) {

                    ex.printStackTrace();

                }

            }

        }

        return result;
    }

}
