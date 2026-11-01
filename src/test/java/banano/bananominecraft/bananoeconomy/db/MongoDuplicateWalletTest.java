package banano.bananominecraft.bananoeconomy.db;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Classification of MongoDB E11000 errors as "wallet address / index taken". */
class MongoDuplicateWalletTest
{
    private static MongoWriteException write(int code, String message)
    {
        return new MongoWriteException(new WriteError(code, message, new BsonDocument()), new ServerAddress());
    }

    private static String e11000(String collection, String index)
    {
        return "E11000 duplicate key error collection: db." + collection + " index: " + index
                + " dup key: { : \"x\" }";
    }

    @Test
    void walletAddressAndIndexViolations_areRecognised()
    {
        assertTrue(MongoDBConnector.isDuplicateWalletViolation(
                write(11000, e11000("users", MongoDBConnector.INDEX_USERS_WALLET))));
        assertTrue(MongoDBConnector.isDuplicateWalletViolation(
                write(11000, e11000("users", MongoDBConnector.INDEX_USERS_WALLET_INDEX))));
        assertTrue(MongoDBConnector.isDuplicateWalletViolation(
                write(11000, e11000("banks", MongoDBConnector.INDEX_BANKS_ADDRESS))));
        assertTrue(MongoDBConnector.isDuplicateWalletViolation(
                write(11000, e11000("banks", MongoDBConnector.INDEX_BANKS_WALLET_INDEX))));
    }

    @Test
    void duplicateId_isNotAWalletViolation()
    {
        assertFalse(MongoDBConnector.isDuplicateWalletViolation(write(11000, e11000("banks", "_id_"))));
    }

    @Test
    void otherWriteErrors_areNotWalletViolations()
    {
        assertFalse(MongoDBConnector.isDuplicateWalletViolation(
                write(121, e11000("users", MongoDBConnector.INDEX_USERS_WALLET))));
        assertFalse(MongoDBConnector.isDuplicateWalletViolation(new RuntimeException("x")));
        assertFalse(MongoDBConnector.isDuplicateWalletViolation(null));
    }

    @Test
    void aWrappedCauseIsFound()
    {
        Exception wrapped = new RuntimeException("insert failed",
                write(11000, e11000("users", MongoDBConnector.INDEX_USERS_WALLET)));
        assertTrue(MongoDBConnector.isDuplicateWalletViolation(wrapped));
    }
}
