package banano.bananominecraft.bananoeconomy.classes;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfflinePaymentRecordTest {

    @Test
    void constructor_setsAllFields() {
        UUID target = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        OfflinePaymentRecord record = new OfflinePaymentRecord(target, "Alice", 5.5, "abc123", now, "hello");

        assertEquals(target, record.targetPlayerUUID());
        assertEquals("Alice", record.fromPlayerName());
        assertEquals(5.5, record.paymentAmount());
        assertEquals("abc123", record.blockHash());
        assertEquals(now, record.transactionDate());
        assertEquals("hello", record.message());
    }

    @Test
    void emptyMessage_isPreserved() {
        UUID target = UUID.randomUUID();
        OfflinePaymentRecord record = new OfflinePaymentRecord(target, "Bob", 1.0, "xyz", LocalDateTime.now(), "");
        assertEquals("", record.message());
    }

    @Test
    void zeroAmount_isPreserved() {
        UUID target = UUID.randomUUID();
        OfflinePaymentRecord record = new OfflinePaymentRecord(target, "Bob", 0.0, "xyz", LocalDateTime.now(), "");
        assertEquals(0.0, record.paymentAmount());
    }
}
