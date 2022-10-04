package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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

}