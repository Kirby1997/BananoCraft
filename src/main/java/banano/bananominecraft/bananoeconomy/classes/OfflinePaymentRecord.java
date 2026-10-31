package banano.bananominecraft.bananoeconomy.classes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable record of a tip that was sent while the recipient was offline.
 *
 * <p>All fields are {@code final}.  Gson can still deserialise instances of
 * this class because it uses {@code sun.misc.Unsafe} to bypass the constructor
 * and set fields directly — the same mechanism already used for
 * {@link PlayerRecord}'s final fields.</p>
 */
public record OfflinePaymentRecord(UUID targetPlayerUUID,
                                   String fromPlayerName,
                                   double paymentAmount,
                                   String blockHash,
                                   LocalDateTime transactionDate,
                                   String message)
{
}
