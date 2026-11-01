package banano.bananominecraft.bananoeconomy.api;

import java.util.UUID;

/**
 * Small, stable cross-plugin surface for other plugins to interact with a player's BananoCraft
 * wallet: read their address/balance, send BAN to an arbitrary destination, and manage their
 * account's representative.
 *
 * <p>Register interest via Bukkit's {@code ServicesManager}, e.g.:</p>
 * <pre>{@code
 * RegisteredServiceProvider<BananoWalletService> provider =
 *         Bukkit.getServicesManager().getRegistration(BananoWalletService.class);
 * }</pre>
 *
 * <p>This interface intentionally does not expose BananoCraft's internal service types
 * ({@code RepresentativeService}, {@code WithdrawService}, etc.) — its result records are its
 * own, so BananoCraft's internals can change freely without breaking consumers.</p>
 */
public interface BananoWalletService
{
    /** The player's BananoCraft-controlled Banano address, or {@code null} if they have no wallet yet. */
    String getAddress(UUID playerId);

    /** The player's current on-chain balance, or {@code 0.0} if they have no wallet yet. */
    double getBalance(UUID playerId);

    /**
     * Sends {@code amountBan} from the player's wallet to an arbitrary external address.
     *
     * <p><b>Unknown outcome (light wallet mode).</b> If the block was published but the network's
     * answer was lost and re-checking the account could not settle whether it was accepted, the result
     * has {@link SendResult#success()} {@code == true}, {@link SendResult#unknown()} {@code == true},
     * {@link SendResult#blockHash()} {@code == null} and {@link SendResult#userError()} holding an
     * explanatory note. It is deliberately not reported as a failure: a caller that retried or refunded
     * on failure could move the funds twice. BananoCraft always republishes that same block before it
     * builds anything else on the account, and logs the situation at SEVERE. A caller that needs
     * certainty can check {@link SendResult#unknown()} and reconcile against the player's history.</p>
     */
    SendResult send(UUID playerId, String destinationAddress, double amountBan);

    /** Sets the player's account representative to a specific address. */
    RepresentativeResult setRepresentative(UUID playerId, String representativeAddress);

    /**
     * Resets the player's account representative to one chosen by BananoCraft's own policy (an
     * admin-curated list if configured, otherwise a random currently-online representative).
     *
     * <p>Intended for callers that temporarily changed a player's representative for their own
     * purposes (e.g. verifying wallet ownership via a representative-change challenge) and need
     * to put the account back on a real representative afterward.</p>
     */
    RepresentativeResult resetToDefaultRepresentative(UUID playerId);

    /**
     * Outcome of {@link #send}.
     *
     * @param success   {@code true} if the send happened <em>or</em> its outcome is {@code unknown}
     * @param blockHash the send block's hash; {@code null} on failure and when {@code unknown}
     * @param userError the failure reason; when {@code unknown} an explanatory note instead
     * @param unknown   {@code true} if the block was published but whether it was accepted could not be
     *                  established (light wallet mode only); then {@code success} is also {@code true}
     */
    record SendResult(boolean success, String blockHash, String userError, boolean unknown)
    {
        /** Kept for binary compatibility with consumers compiled against the three-field record. */
        public SendResult(boolean success, String blockHash, String userError)
        {
            this(success, blockHash, userError, false);
        }

        public static SendResult succeeded(String blockHash)
        {
            return new SendResult(true, blockHash, null, false);
        }

        public static SendResult failed(String userError)
        {
            return new SendResult(false, null, userError, false);
        }

        /** Published, but whether it was accepted is unknown; reported as a success with a note. */
        public static SendResult unknownOutcome(String note)
        {
            return new SendResult(true, null, note, true);
        }
    }

    /** Outcome of {@link #setRepresentative} / {@link #resetToDefaultRepresentative}. */
    record RepresentativeResult(boolean success, String userError)
    {
        public static RepresentativeResult succeeded()
        {
            return new RepresentativeResult(true, null);
        }

        public static RepresentativeResult failed(String userError)
        {
            return new RepresentativeResult(false, userError);
        }
    }
}
