package banano.bananominecraft.bananoeconomy.db;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.*;

/** Classification of MySQL unique-key violations as "wallet address / index taken". */
class MysqlDuplicateWalletTest
{
    private static SQLException duplicate(String key)
    {
        return new SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key '" + key + "'", "23000", 1062);
    }

    @Test
    void walletAddressAndIndexViolations_areRecognised()
    {
        assertTrue(MysqlDBConnector.isDuplicateWalletViolation(duplicate("users." + MysqlDBConnector.INDEX_USERS_WALLET)));
        assertTrue(MysqlDBConnector.isDuplicateWalletViolation(duplicate("users." + MysqlDBConnector.INDEX_USERS_WALLET_INDEX)));
        assertTrue(MysqlDBConnector.isDuplicateWalletViolation(duplicate("banks." + MysqlDBConnector.INDEX_BANKS_ADDRESS)));
        assertTrue(MysqlDBConnector.isDuplicateWalletViolation(duplicate("banks." + MysqlDBConnector.INDEX_BANKS_WALLET_INDEX)));
    }

    @Test
    void duplicatePrimaryKey_isNotAWalletViolation()
    {
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(duplicate("users.PRIMARY")));
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(duplicate("banks.PRIMARY")));
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(duplicate("users.playerUUID")));
    }

    @Test
    void otherErrors_areNotWalletViolations()
    {
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(new SQLException("connection lost", "08S01", 0)));
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(new RuntimeException("x")));
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(null));
    }

    @Test
    void aWrappedCauseIsFound()
    {
        Exception wrapped = new RuntimeException("insert failed",
                duplicate("users." + MysqlDBConnector.INDEX_USERS_WALLET));
        assertTrue(MysqlDBConnector.isDuplicateWalletViolation(wrapped));
    }

    @Test
    void sameMessageWithoutDuplicateKeyCode_isNotAViolation()
    {
        SQLException other = new SQLException("deadlock on " + MysqlDBConnector.INDEX_USERS_WALLET, "40001", 1213);
        assertFalse(MysqlDBConnector.isDuplicateWalletViolation(other));
    }
}
