package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.classes.TransferOutcome;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.io.RPC;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Framework-free core of the tip flow.
 *
 * <p>This class deliberately knows nothing about Bukkit, scheduling, locales, or chat
 * components. It performs the money transfer and decides whether the recipient needs an
 * offline-payment record, then reports the outcome. The command layer
 * ({@code TipCommand}) stays responsible for argument parsing, the async boundary, and
 * rendering messages — so this logic is unit-testable with plain mocks, no MockBukkit.</p>
 */
public final class TipService
{
    private static final Logger LOGGER = Logger.getLogger(TipService.class.getName());

    /** Prefix of the offline-payment note when the sender's transaction outcome is unknown. */
    static final String UNKNOWN_RECIPIENT_NOTE =
            "[" + TransferOutcome.UNKNOWN_NOTE + " - this payment may not have arrived, check /history]";

    private final IDBConnector db;
    private final RPC rpc;

    public TipService(IDBConnector db, RPC rpc)
    {
        this.db = db;
        this.rpc = rpc;
    }

    /**
     * Send {@code amount} from {@code senderWallet} to {@code recipientWallet}. If the
     * recipient is offline, persist an {@link OfflinePaymentRecord} so they are notified
     * on next login.
     *
     * @param recipientOnline whether the recipient is currently online (resolved by the
     *                        caller, which owns the Bukkit lookup)
     * @return a {@link TransferResult} describing the outcome; never {@code null}
     */
    public TransferResult transfer(String senderWallet,
                                   String recipientWallet,
                                   UUID recipientUuid,
                                   double amount,
                                   String message,
                                   String fromDisplayName,
                                   boolean recipientOnline)
    {
        final String blockHash;

        try
        {
            blockHash = rpc.sendTransaction(senderWallet, recipientWallet, amount);
        }
        catch (final UnknownOutcomeError error)
        {
            // The tip may or may not have been sent. The sender is told the status is unknown (not a
            // plain failure), and an offline recipient still gets a record so they are informed too.
            LOGGER.log(Level.SEVERE, "UNKNOWN OUTCOME for tip from {0} to {1} (wallet {2}), amount {3} BAN: {4}",
                    new Object[]{ fromDisplayName, recipientUuid, recipientWallet, amount,
                            TransferOutcome.UNKNOWN_NOTE });

            if (!recipientOnline)
            {
                db.saveOfflinePayment(new OfflinePaymentRecord(
                        recipientUuid, fromDisplayName, amount, "", LocalDateTime.now(),
                        UNKNOWN_RECIPIENT_NOTE + (message == null || message.isEmpty() ? "" : " " + message)));
            }

            return TransferResult.failed(error.getUserError());
        }
        catch (final TransactionError error)
        {
            return TransferResult.failed(error.getUserError());
        }

        // Best-effort: the tip has already been sent regardless of whether this succeeds.
        // If it fails, the block is left pending and will still be picked up later by the
        // node's own auto-receive (node mode), receive-on-join (light mode, which has no node
        // auto-receive), or a manual /receive.
        try
        {
            rpc.receiveBlock(recipientWallet, blockHash);
        }
        catch (final TransactionError error)
        {
            LOGGER.log(Level.WARNING, "Failed to auto-receive tip block {0} for {1}: {2}",
                    new Object[]{ blockHash, recipientWallet, error.getUserError() });
        }

        if (!recipientOnline)
        {
            db.saveOfflinePayment(new OfflinePaymentRecord(
                    recipientUuid, fromDisplayName, amount, blockHash, LocalDateTime.now(), message));

            return TransferResult.sentOffline(blockHash);
        }

        return TransferResult.sentOnline(blockHash);
    }

    /**
     * Outcome of {@link #transfer}. {@code blockHash} is set on success; {@code userError}
     * is set on failure.
     */
    public record TransferResult(Status status, String blockHash, String userError)
    {
        public enum Status { SENT_ONLINE, SENT_OFFLINE, FAILED }

        public static TransferResult sentOnline(String blockHash)
        {
            return new TransferResult(Status.SENT_ONLINE, blockHash, null);
        }

        public static TransferResult sentOffline(String blockHash)
        {
            return new TransferResult(Status.SENT_OFFLINE, blockHash, null);
        }

        public static TransferResult failed(String userError)
        {
            return new TransferResult(Status.FAILED, null, userError);
        }
    }
}
