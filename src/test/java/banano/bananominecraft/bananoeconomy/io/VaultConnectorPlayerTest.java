package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.classes.TransferOutcome;
import banano.bananominecraft.bananoeconomy.services.BankService;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static net.milkbowl.vault.economy.EconomyResponse.ResponseType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultConnectorPlayerTest
{
    private EconomyFuncs   economyFuncs;
    private OfflinePlayer  player;
    private VaultConnector connector;

    @BeforeEach
    void setUp()
    {
        economyFuncs = mock(EconomyFuncs.class);
        player       = mock(OfflinePlayer.class);
        connector    = new VaultConnector(mock(Plugin.class), economyFuncs, mock(RPC.class), mock(BankService.class));

        when(economyFuncs.hasWallet(player)).thenReturn(true);
    }

    @Test
    void WithdrawRejectsNonFiniteAndNegativeAmounts()
    {
        for (double amount : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0 })
        {
            assertEquals(FAILURE, connector.withdrawPlayer(player, amount).type);
        }

        verify(economyFuncs, never()).removeBalanceFPOutcome(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void DepositRejectsNonFiniteAndNegativeAmounts()
    {
        for (double amount : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0 })
        {
            assertEquals(FAILURE, connector.depositPlayer(player, amount).type);
        }

        verify(economyFuncs, never()).addBalanceTPOutcome(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void WithdrawFrozenAccountReportsFrozen()
    {
        when(economyFuncs.isFrozen(player)).thenReturn(true);

        EconomyResponse r = connector.withdrawPlayer(player, 5.0);
        assertEquals(FAILURE, r.type);
        assertTrue(r.errorMessage.contains("frozen"), r.errorMessage);
        verify(economyFuncs, never()).removeBalanceFPOutcome(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void WithdrawInsufficientFundsDoesNotSend()
    {
        when(economyFuncs.getBalance(player)).thenReturn(2.0);

        EconomyResponse r = connector.withdrawPlayer(player, 5.0);
        assertEquals(FAILURE, r.type);
        assertEquals("Insufficient funds.", r.errorMessage);
        verify(economyFuncs, never()).removeBalanceFPOutcome(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void WithdrawSuccessComputesNewBalanceWithoutRefetch()
    {
        when(economyFuncs.getBalance(player)).thenReturn(10.0);
        when(economyFuncs.removeBalanceFPOutcome(player, 4.0)).thenReturn(TransferOutcome.OK);

        EconomyResponse r = connector.withdrawPlayer(player, 4.0);
        assertEquals(SUCCESS, r.type);
        assertEquals(6.0, r.balance);
        verify(economyFuncs, times(1)).getBalance(player);
    }

    @Test
    void WithdrawUnknownOutcomeIsSuccessWithNote_neverFailure()
    {
        when(economyFuncs.getBalance(player)).thenReturn(10.0);
        when(economyFuncs.removeBalanceFPOutcome(player, 4.0)).thenReturn(TransferOutcome.UNKNOWN);

        EconomyResponse r = connector.withdrawPlayer(player, 4.0);
        assertEquals(SUCCESS, r.type);
        assertEquals(6.0, r.balance);
        assertTrue(r.errorMessage.contains("outcome unknown"), r.errorMessage);
        assertTrue(r.errorMessage.contains("/history"), r.errorMessage);
    }

    @Test
    void WithdrawFailureStillReportsFailure()
    {
        when(economyFuncs.getBalance(player)).thenReturn(10.0);
        when(economyFuncs.removeBalanceFPOutcome(player, 4.0)).thenReturn(TransferOutcome.FAILED);

        EconomyResponse r = connector.withdrawPlayer(player, 4.0);
        assertEquals(FAILURE, r.type);
        assertEquals(10.0, r.balance);
        assertEquals("Withdrawal failed.", r.errorMessage);
    }

    @Test
    void DepositUnknownOutcomeIsSuccessWithNote_neverFailure()
    {
        when(economyFuncs.addBalanceTPOutcome(player, 4.0)).thenReturn(TransferOutcome.UNKNOWN);
        when(economyFuncs.getBalance(player)).thenReturn(10.0);

        EconomyResponse r = connector.depositPlayer(player, 4.0);
        assertEquals(SUCCESS, r.type);
        assertTrue(r.errorMessage.contains("outcome unknown"), r.errorMessage);
    }

    @Test
    void DepositSuccessHasNoMessage_andFailureStillFails()
    {
        when(economyFuncs.getBalance(player)).thenReturn(10.0);
        when(economyFuncs.addBalanceTPOutcome(player, 4.0)).thenReturn(TransferOutcome.OK);
        assertEquals(SUCCESS, connector.depositPlayer(player, 4.0).type);
        assertNull(connector.depositPlayer(player, 4.0).errorMessage);

        when(economyFuncs.addBalanceTPOutcome(player, 5.0)).thenReturn(TransferOutcome.FAILED);
        assertEquals(FAILURE, connector.depositPlayer(player, 5.0).type);
    }
}
