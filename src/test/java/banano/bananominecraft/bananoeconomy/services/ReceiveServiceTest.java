package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiveServiceTest
{
    // A genuinely Validator-valid ban_ address, matching the constants used in RPCTest.
    private static final String ACCOUNT = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String WALLET  = "WALLET_ID";

    private RPC rpc;
    private ConfigEngine configEngine;
    private ReceiveService service;

    @BeforeEach
    void setUp()
    {
        rpc = mock(RPC.class);
        configEngine = mock(ConfigEngine.class);
        when(configEngine.getWalletId()).thenReturn(WALLET);
        service = new ReceiveService(rpc, configEngine);
    }

    @Test
    void receiveAllPending_returnsSucceededEmpty_whenNothingPending() throws TransactionError
    {
        when(rpc.receivablePending(ACCOUNT)).thenReturn(List.of());

        ReceiveService.ReceiveResult result = service.receiveAllPending(ACCOUNT, WALLET);

        assertTrue(result.success());
        assertTrue(result.receivedBlocks().isEmpty());
        assertNull(result.userError());
        verify(rpc, never()).receiveBlock(anyString(), anyString(), anyString());
    }

    @Test
    void receiveAllPending_receivesEveryPendingBlock() throws TransactionError
    {
        when(rpc.receivablePending(ACCOUNT)).thenReturn(List.of("PENDING1", "PENDING2"));
        when(rpc.receiveBlock(WALLET, ACCOUNT, "PENDING1")).thenReturn("RECEIVE1");
        when(rpc.receiveBlock(WALLET, ACCOUNT, "PENDING2")).thenReturn("RECEIVE2");

        ReceiveService.ReceiveResult result = service.receiveAllPending(ACCOUNT, WALLET);

        assertTrue(result.success());
        assertEquals(List.of("RECEIVE1", "RECEIVE2"), result.receivedBlocks());
        verify(rpc).receiveBlock(WALLET, ACCOUNT, "PENDING1");
        verify(rpc).receiveBlock(WALLET, ACCOUNT, "PENDING2");
    }

    @Test
    void receiveAllPending_usesConfiguredWalletId_whenNotExplicitlyPassed()
    {
        when(rpc.receivablePending(ACCOUNT)).thenReturn(List.of());

        service.receiveAllPending(ACCOUNT);

        verify(configEngine).getWalletId();
    }

    @Test
    void receiveAllPending_stopsAndReportsFailure_whenAPendingBlockFailsToReceive() throws TransactionError
    {
        when(rpc.receivablePending(ACCOUNT)).thenReturn(List.of("PENDING1", "PENDING2", "PENDING3"));
        when(rpc.receiveBlock(WALLET, ACCOUNT, "PENDING1")).thenReturn("RECEIVE1");
        when(rpc.receiveBlock(WALLET, ACCOUNT, "PENDING2")).thenThrow(new TransactionError("Block not found"));

        ReceiveService.ReceiveResult result = service.receiveAllPending(ACCOUNT, WALLET);

        assertFalse(result.success());
        assertEquals("Block not found", result.userError());
        // The block that succeeded before the failure is still reported as received.
        assertEquals(List.of("RECEIVE1"), result.receivedBlocks());
        verify(rpc, never()).receiveBlock(WALLET, ACCOUNT, "PENDING3");
    }
}
