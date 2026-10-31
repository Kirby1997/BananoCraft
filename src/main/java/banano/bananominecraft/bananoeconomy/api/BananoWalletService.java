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

    /** Sends {@code amountBan} from the player's wallet to an arbitrary external address. */
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

    /** Outcome of {@link #send}. */
    record SendResult(boolean success, String blockHash, String userError)
    {
        public static SendResult succeeded(String blockHash)
        {
            return new SendResult(true, blockHash, null);
        }

        public static SendResult failed(String userError)
        {
            return new SendResult(false, null, userError);
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
