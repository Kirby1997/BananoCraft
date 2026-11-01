package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the tip transfer core. No MockBukkit, no scheduler, no chat
 * components — just the money logic against mocked {@link RPC} and {@link IDBConnector}.
 */
class TipServiceTest
{
    private static final String SENDER    = "ban_sender";
    private static final String RECIPIENT = "ban_recipient";

    private IDBConnector db;
    private RPC rpc;
    private TipService service;
    private UUID recipientUuid;

    @BeforeEach
    void setUp()
    {
        db = mock(IDBConnector.class);
        rpc = mock(RPC.class);
        service = new TipService(db, rpc);
        recipientUuid = UUID.randomUUID();
    }

    @Test
    void onlineRecipient_sendsTransaction_andDoesNotPersist() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 1.5)).thenReturn("BLOCK");

        TipService.TransferResult result = service.transfer(
                SENDER, RECIPIENT, recipientUuid, 1.5, "thanks", "Alice", true);

        assertEquals(TipService.TransferResult.Status.SENT_ONLINE, result.status());
        assertEquals("BLOCK", result.blockHash());
        verify(rpc).sendTransaction(SENDER, RECIPIENT, 1.5);
        verify(rpc).receiveBlock(RECIPIENT, "BLOCK");
        verify(db, never()).saveOfflinePayment(any());
    }

    @Test
    void onlineRecipient_stillSucceeds_whenAutoReceiveFails() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 1.5)).thenReturn("BLOCK");
        when(rpc.receiveBlock(RECIPIENT, "BLOCK")).thenThrow(new TransactionError("Block not found"));

        TipService.TransferResult result = service.transfer(
                SENDER, RECIPIENT, recipientUuid, 1.5, "thanks", "Alice", true);

        // The tip itself already succeeded, so a failed auto-receive is not reported as a failure.
        assertEquals(TipService.TransferResult.Status.SENT_ONLINE, result.status());
        assertEquals("BLOCK", result.blockHash());
    }

    @Test
    void offlineRecipient_persistsOfflinePayment() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 2.0)).thenReturn("BLOCK2");

        TipService.TransferResult result = service.transfer(
                SENDER, RECIPIENT, recipientUuid, 2.0, "note", "Alice", false);

        assertEquals(TipService.TransferResult.Status.SENT_OFFLINE, result.status());
        assertEquals("BLOCK2", result.blockHash());
        verify(rpc).receiveBlock(RECIPIENT, "BLOCK2");

        ArgumentCaptor<OfflinePaymentRecord> captor = ArgumentCaptor.forClass(OfflinePaymentRecord.class);
        verify(db).saveOfflinePayment(captor.capture());
        OfflinePaymentRecord saved = captor.getValue();
        assertEquals(recipientUuid, saved.targetPlayerUUID());
        assertEquals("Alice", saved.fromPlayerName());
        assertEquals(2.0, saved.paymentAmount());
        assertEquals("BLOCK2", saved.blockHash());
        assertEquals("note", saved.message());
    }

    @Test
    void transactionError_isReportedAsFailed_andNothingPersisted() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 5.0))
                .thenThrow(new TransactionError("Insufficient balance"));

        TipService.TransferResult result = service.transfer(
                SENDER, RECIPIENT, recipientUuid, 5.0, "", "Alice", true);

        assertEquals(TipService.TransferResult.Status.FAILED, result.status());
        assertEquals("Insufficient balance", result.userError());
        assertNull(result.blockHash());
        verify(db, never()).saveOfflinePayment(any());
        verify(rpc, never()).receiveBlock(anyString(), anyString());
    }

    @Test
    void unknownOutcome_offlineRecipient_isReportedAsStatusUnknown_andStillGetsAMarkedRecord() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 2.0)).thenThrow(new UnknownOutcomeError());

        TipService.TransferResult result = service.transfer(
                SENDER, RECIPIENT, recipientUuid, 2.0, "gg", "Alice", false);

        assertEquals(TipService.TransferResult.Status.FAILED, result.status());
        assertEquals(UnknownOutcomeError.USER_MESSAGE, result.userError());
        verify(rpc, never()).receiveBlock(anyString(), anyString());

        ArgumentCaptor<OfflinePaymentRecord> captor = ArgumentCaptor.forClass(OfflinePaymentRecord.class);
        verify(db).saveOfflinePayment(captor.capture());
        OfflinePaymentRecord saved = captor.getValue();
        assertEquals(recipientUuid, saved.targetPlayerUUID());
        assertEquals("Alice", saved.fromPlayerName());
        assertEquals(2.0, saved.paymentAmount());
        assertTrue(saved.message().startsWith(TipService.UNKNOWN_RECIPIENT_NOTE), saved.message());
        assertTrue(saved.message().contains("outcome unknown"), saved.message());
        assertTrue(saved.message().endsWith("gg"), saved.message());
    }

    @Test
    void unknownOutcome_offlineRecipient_withoutMessage_stillMarked() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 2.0)).thenThrow(new UnknownOutcomeError());

        service.transfer(SENDER, RECIPIENT, recipientUuid, 2.0, "", "Alice", false);

        ArgumentCaptor<OfflinePaymentRecord> captor = ArgumentCaptor.forClass(OfflinePaymentRecord.class);
        verify(db).saveOfflinePayment(captor.capture());
        assertEquals(TipService.UNKNOWN_RECIPIENT_NOTE, captor.getValue().message());
    }

    @Test
    void unknownOutcome_onlineRecipient_isStatusUnknown_noRecordSaved() throws TransactionError
    {
        when(rpc.sendTransaction(SENDER, RECIPIENT, 2.0)).thenThrow(new UnknownOutcomeError());

        TipService.TransferResult result = service.transfer(
                SENDER, RECIPIENT, recipientUuid, 2.0, "gg", "Alice", true);

        assertEquals(TipService.TransferResult.Status.FAILED, result.status());
        assertEquals(UnknownOutcomeError.USER_MESSAGE, result.userError());
        verify(db, never()).saveOfflinePayment(any());
        verify(rpc, never()).receiveBlock(anyString(), anyString());
    }
}
