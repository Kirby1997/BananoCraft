package banano.bananominecraft.bananoeconomy.classes;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRecordTest {

    private static final String UUID_STR = UUID.randomUUID().toString();
    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    @Test
    void constructor_setsAllFields() {
        PlayerRecord record = new PlayerRecord(UUID_STR, "Alice", WALLET, false);

        assertEquals(UUID_STR, record.getPlayerUUID());
        assertEquals("Alice", record.getPlayerName());
        assertEquals(WALLET, record.getWallet());
        assertFalse(record.isFrozen());
    }

    @Test
    void setFrozen_true_makesRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID_STR, "Alice", WALLET, false);
        record.setFrozen(true);
        assertTrue(record.isFrozen());
    }

    @Test
    void setFrozen_false_unfreezes() {
        PlayerRecord record = new PlayerRecord(UUID_STR, "Alice", WALLET, true);
        record.setFrozen(false);
        assertFalse(record.isFrozen());
    }

    @Test
    void constructor_frozenTrue_recordIsFrozen() {
        PlayerRecord record = new PlayerRecord(UUID_STR, "Bob", WALLET, true);
        assertTrue(record.isFrozen());
    }
}
