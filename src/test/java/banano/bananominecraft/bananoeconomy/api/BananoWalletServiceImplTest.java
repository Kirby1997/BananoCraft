package banano.bananominecraft.bananoeconomy.api;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BananoWalletServiceImplTest
{
    private static final UUID   PLAYER_ID = UUID.randomUUID();
    // Must be a Validator-valid address: the service treats anything else as "no wallet".
    private static final String WALLET    = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String DEST      = "ban_dest";

    private IDBConnector db;
    private RPC rpc;
    private RepresentativeService representativeService;
    private BananoWalletServiceImpl service;

    @BeforeEach
    void setUp()
    {
        db = mock(IDBConnector.class);
        rpc = mock(RPC.class);
        representativeService = mock(RepresentativeService.class);
        service = new BananoWalletServiceImpl(db, rpc, representativeService);
    }

    private PlayerRecord activeRecord()
    {
        return new PlayerRecord(PLAYER_ID.toString(), "PlayerName", WALLET, false);
    }

    @Test
    void getAddress_returnsWallet_whenRecordExists()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        assertEquals(WALLET, service.getAddress(PLAYER_ID));
    }

    @Test
    void getAddress_returnsNull_whenNoRecord()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(null);
        assertNull(service.getAddress(PLAYER_ID));
    }

    @Test
    void getAddress_returnsNull_whenStoredWalletIsCreationFailureSentinel()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(
                new PlayerRecord(PLAYER_ID.toString(), "PlayerName", RPC.ACCOUNT_CREATION_FAILED, false));
        assertNull(service.getAddress(PLAYER_ID));
    }

    @Test
    void getAddress_returnsNull_whenStoredWalletInvalid()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(
                new PlayerRecord(PLAYER_ID.toString(), "PlayerName", "ban_notreal", false));
        assertNull(service.getAddress(PLAYER_ID));
    }

    @Test
    void getAddress_returnsNull_forNullPlayerId()
    {
        assertNull(service.getAddress(null));
        verifyNoInteractions(db);
    }

    @Test
    void send_fails_whenStoredWalletIsCreationFailureSentinel() throws TransactionError
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(
                new PlayerRecord(PLAYER_ID.toString(), "PlayerName", RPC.ACCOUNT_CREATION_FAILED, false));

        BananoWalletService.SendResult result = service.send(PLAYER_ID, DEST, 1.5);

        assertFalse(result.success());
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void send_fails_forNonFiniteOrNonPositiveAmount() throws TransactionError
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());

        assertFalse(service.send(PLAYER_ID, DEST, Double.NaN).success());
        assertFalse(service.send(PLAYER_ID, DEST, Double.POSITIVE_INFINITY).success());
        assertFalse(service.send(PLAYER_ID, DEST, 0).success());
        assertFalse(service.send(PLAYER_ID, DEST, -1).success());
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void send_doesNotLeakRuntimeExceptions() throws TransactionError
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        when(rpc.sendTransaction(WALLET, DEST, 1.5)).thenThrow(new IllegalStateException("boom"));

        BananoWalletService.SendResult result = service.send(PLAYER_ID, DEST, 1.5);

        assertFalse(result.success());
        assertNotNull(result.userError());
    }

    @Test
    void getBalance_queriesRpcWithPlayerWallet()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        when(rpc.getBalance(WALLET)).thenReturn(4.5);

        assertEquals(4.5, service.getBalance(PLAYER_ID));
    }

    @Test
    void getBalance_returnsZero_whenNoWallet()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(null);
        assertEquals(0.0, service.getBalance(PLAYER_ID));
        verify(rpc, never()).getBalance(anyString());
    }

    @Test
    void send_success_returnsBlockHash() throws TransactionError
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        when(rpc.sendTransaction(WALLET, DEST, 1.5)).thenReturn("BLOCK");

        BananoWalletService.SendResult result = service.send(PLAYER_ID, DEST, 1.5);

        assertTrue(result.success());
        assertEquals("BLOCK", result.blockHash());
    }

    @Test
    void send_fails_whenNoWallet()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(null);

        BananoWalletService.SendResult result = service.send(PLAYER_ID, DEST, 1.5);

        assertFalse(result.success());
        assertNotNull(result.userError());
    }

    @Test
    void send_fails_whenAccountFrozen()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(
                new PlayerRecord(PLAYER_ID.toString(), "PlayerName", WALLET, true));

        BananoWalletService.SendResult result = service.send(PLAYER_ID, DEST, 1.5);

        assertFalse(result.success());
        assertNotNull(result.userError());
    }

    @Test
    void send_reportsTransactionError() throws TransactionError
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        when(rpc.sendTransaction(WALLET, DEST, 1.5)).thenThrow(new TransactionError("Insufficient balance"));

        BananoWalletService.SendResult result = service.send(PLAYER_ID, DEST, 1.5);

        assertFalse(result.success());
        assertEquals("Insufficient balance", result.userError());
    }

    @Test
    void setRepresentative_delegatesToRepresentativeService()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        when(representativeService.setRepresentative(WALLET, DEST))
                .thenReturn(RepresentativeService.RepresentativeResult.succeeded());

        BananoWalletService.RepresentativeResult result = service.setRepresentative(PLAYER_ID, DEST);

        assertTrue(result.success());
        verify(representativeService).setRepresentative(WALLET, DEST);
    }

    @Test
    void setRepresentative_fails_whenNoWallet()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(null);

        BananoWalletService.RepresentativeResult result = service.setRepresentative(PLAYER_ID, DEST);

        assertFalse(result.success());
        verifyNoInteractions(representativeService);
    }

    @Test
    void setRepresentative_fails_whenAccountFrozen()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(
                new PlayerRecord(PLAYER_ID.toString(), "PlayerName", WALLET, true));

        BananoWalletService.RepresentativeResult result = service.setRepresentative(PLAYER_ID, DEST);

        assertFalse(result.success());
        verifyNoInteractions(representativeService);
    }

    @Test
    void resetToDefaultRepresentative_delegatesToRepresentativeService()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(activeRecord());
        when(representativeService.setRandomRepresentative(WALLET))
                .thenReturn(RepresentativeService.RepresentativeResult.failed("No representatives available."));

        BananoWalletService.RepresentativeResult result = service.resetToDefaultRepresentative(PLAYER_ID);

        assertFalse(result.success());
        assertEquals("No representatives available.", result.userError());
    }

    @Test
    void resetToDefaultRepresentative_fails_whenNoWallet()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(null);

        BananoWalletService.RepresentativeResult result = service.resetToDefaultRepresentative(PLAYER_ID);

        assertFalse(result.success());
        verifyNoInteractions(representativeService);
    }

    @Test
    void resetToDefaultRepresentative_fails_whenAccountFrozen()
    {
        when(db.getPlayerRecord(PLAYER_ID)).thenReturn(
                new PlayerRecord(PLAYER_ID.toString(), "PlayerName", WALLET, true));

        BananoWalletService.RepresentativeResult result = service.resetToDefaultRepresentative(PLAYER_ID);

        assertFalse(result.success());
        verifyNoInteractions(representativeService);
    }
}
