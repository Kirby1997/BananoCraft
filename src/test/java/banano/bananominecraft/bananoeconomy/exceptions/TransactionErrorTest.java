package banano.bananominecraft.bananoeconomy.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionErrorTest
{
    @Test
    void userError_matchesMessage()
    {
        TransactionError error = new TransactionError("Insufficient balance");

        assertEquals("Insufficient balance", error.getUserError());
        assertEquals("Insufficient balance", error.getMessage());
    }

    @Test
    void isCheckedException()
    {
        assertTrue(Exception.class.isAssignableFrom(TransactionError.class));
        assertFalse(RuntimeException.class.isAssignableFrom(TransactionError.class));
    }
}
