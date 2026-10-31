package banano.bananominecraft.bananoeconomy.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionDirectionTest
{
    @Test
    void hasSendAndReceive()
    {
        assertEquals(2, TransactionDirection.values().length);
        assertEquals(TransactionDirection.Send, TransactionDirection.valueOf("Send"));
        assertEquals(TransactionDirection.Receive, TransactionDirection.valueOf("Receive"));
    }
}
