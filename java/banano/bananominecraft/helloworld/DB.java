package banano.bananominecraft.helloworld;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bukkit.entity.Player;

import static com.mongodb.client.model.Filters.eq;

public class DB {


    private static MongoClient mongoClient = MongoClients.create("MONGODB URI");



    public static MongoClient getMongoClient(){ return mongoClient;

    }

    public static String getWallet(String UUID){

        MongoCollection<Document> collection = mongoClient.getDatabase("BananoCraft").getCollection("users");
        collection.createIndex(Indexes.text("UUID"));
        Document wallet = collection.find(eq("UUID", UUID)).first();
        System.out.println(wallet.toJson());
        System.out.println(wallet.getString("Wallet"));

        return wallet.getString("Wallet");
    }


    public static void storeAccount(Player player, String account){
        MongoCollection<Document> collection = mongoClient.getDatabase("BananoCraft").getCollection("users");
        collection.createIndex(Indexes.text("UUID"));
        String UUID = player.getUniqueId().toString();

        Document document1 = new Document("name", player.getName())
                .append("UUID", UUID)
                .append("Wallet", account);
        collection.insertOne(document1);

    }

    public static boolean accountExists(String UUID){
        MongoCollection<Document> collection = mongoClient.getDatabase("BananoCraft").getCollection("users");
        collection.createIndex(Indexes.text("UUID"));
        long matchCount = collection.countDocuments(Filters.text(UUID));
        if(matchCount > 0){
            System.out.println("Text search matches: " + matchCount);
            return true;
        }
        else{
            return false;
        }

    }
}
