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
    //Plugin plugin = banano.bananominecraft.helloworld.HelloWorld.getPlugin(HelloWorld.class);

    private static MongoClient mongoClient = MongoClients.create("INSERT MONGODB URI HERE");


    public static MongoClient getMongoClient(){ return mongoClient;

    }

    public static String getWallet(String UUID){

        MongoCollection<Document> collection = mongoClient.getDatabase("BananoCraft").getCollection("users");
        collection.createIndex(Indexes.text("user"));
        try{
            Document wallet = collection.find(eq("UUID", UUID)).first();


            return wallet.getString("Wallet");}

        catch (Exception e){
            e.printStackTrace();
        }

    return "SOMETHING BORKED";

    }


    public static void storeAccount(Player player, String account){
        MongoCollection<Document> collection = mongoClient.getDatabase("BananoCraft").getCollection("users");
        collection.createIndex(Indexes.text("user"));
        String UUID = player.getUniqueId().toString();

        Document document1 = new Document("name", player.getName())
                .append("UUID", UUID)
                .append("Wallet", account);
        collection.insertOne(document1);

    }

    public static boolean accountExists(String UUID){
        MongoCollection<Document> collection = mongoClient.getDatabase("BananoCraft").getCollection("users");
        collection.createIndex(Indexes.text("user"));
        System.out.println("COLLECTION" + collection);
        long matchCount = collection.countDocuments(Filters.text(UUID));
        Document result = collection.find(eq("UUID", UUID)).first();
        if(result != null){
            System.out.println(UUID + " has the wallet: " + result.getString("Wallet"));
            return true;
        }
        else{
            return false;
        }

    }
}
