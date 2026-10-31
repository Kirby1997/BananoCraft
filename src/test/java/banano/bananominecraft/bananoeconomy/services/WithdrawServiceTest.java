package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawServiceTest
{
    private static final String WALLET = "ban_source";
    private static final String DEST   = "ban_dest";

    private RPC rpc;
    private WithdrawService service;

    @BeforeEach
    void setUp()
    {
        rpc = mock(RPC.class);
        service = new WithdrawService(rpc);
    }

    @Test
    void resolveAmount_all_returnsWalletBalance()
    {
        when(rpc.getBalance(WALLET)).thenReturn(7.5);
        assertEquals(7.5, service.resolveAmount(WALLET, "all"));
    }

    @Test
    void resolveAmount_all_isCaseInsensitive()
    {
        when(rpc.getBalance(WALLET)).thenReturn(7.5);
        assertEquals(7.5, service.resolveAmount(WALLET, "ALL"));
    }

    @Test
    void resolveAmount_number_isParsed()
    {
        assertEquals(3.25, service.resolveAmount(WALLET, "3.25"));
        verify(rpc, never()).getBalance(anyString());
    }

    @Test
    void resolveAmount_nonNumber_throws()
    {
        assertThrows(NumberFormatException.class, () -> service.resolveAmount(WALLET, "abc"));
    }

    @Test
    void withdraw_success_returnsBlockHash() throws TransactionError
    {
        when(rpc.sendTransaction(WALLET, DEST, 2.0)).thenReturn("BLOCK");

        WithdrawService.WithdrawResult result = service.withdraw(WALLET, DEST, 2.0);

        assertTrue(result.success());
        assertEquals("BLOCK", result.blockHash());
        assertNull(result.userError());
    }

    @Test
    void withdraw_transactionError_isReportedAsFailure() throws TransactionError
    {
        when(rpc.sendTransaction(WALLET, DEST, 2.0))
                .thenThrow(new TransactionError("Invalid address"));

        WithdrawService.WithdrawResult result = service.withdraw(WALLET, DEST, 2.0);

        assertFalse(result.success());
        assertEquals("Invalid address", result.userError());
        assertNull(result.blockHash());
    }
}
