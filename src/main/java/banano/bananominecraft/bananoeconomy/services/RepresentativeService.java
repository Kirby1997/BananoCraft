package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.validation.Validator;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Framework-free core of representative selection/assignment: pick a real representative for
 * an account and change it via the node. Shared by {@code /representative} and by callers that
 * need to reset an account's representative after a temporary change (e.g. an external service's
 * wallet-ownership verification handshake, which asks the account to change its representative
 * to a one-time verification address).
 *
 * <p>Knows nothing about Bukkit, scheduling, or messaging — so it is unit-testable with plain
 * mocks. The command layer keeps argument parsing, the async boundary, and player messaging.</p>
 */
public final class RepresentativeService
{
    private static final Logger LOGGER = Logger.getLogger(RepresentativeService.class.getName());

    private final RPC rpc;
    private final ConfigEngine configEngine;

    public RepresentativeService(RPC rpc, ConfigEngine configEngine)
    {
        this.rpc = rpc;
        this.configEngine = configEngine;
    }

    /**
     * Candidate representatives to choose from: the admin-curated list from config.yml if one is
     * configured, otherwise whatever the node currently reports as online. Curation is preferred
     * because representative choice affects Nano/Banano network decentralization — an admin may
     * want to pin known-good representatives rather than always trusting the node's live list.
     */
    public List<String> getCandidateRepresentatives()
    {
        List<String> curated = configEngine.getRepresentatives();

        if (!curated.isEmpty())
        {
            List<String> valid = curated.stream().filter(Validator::validateAddress).collect(Collectors.toList());

            if (valid.size() < curated.size())
            {
                LOGGER.log(Level.WARNING,
                        "Ignoring {0} malformed address(es) in config.yml''s representatives list.",
                        curated.size() - valid.size());
            }

            if (!valid.isEmpty())
            {
                return valid;
            }

            LOGGER.warning("config.yml's representatives list has no valid addresses — "
                    + "falling back to the node's online representatives.");
        }

        return rpc.representativesOnline();
    }

    /**
     * Assigns a specific representative to {@code account}, signing with the main player wallet.
     */
    public RepresentativeResult setRepresentative(String account, String representative)
    {
        return setRepresentative(account, representative, configEngine.getWalletId());
    }

    /**
     * Assigns a specific representative to {@code account}.
     */
    public RepresentativeResult setRepresentative(String account, String representative, String walletId)
    {
        if (!Validator.validateAddress(representative))
        {
            return RepresentativeResult.failed("Invalid representative address.");
        }

        try
        {
            rpc.setRepresentative(account, representative, walletId);

            return RepresentativeResult.succeeded();
        }
        catch (TransactionError error)
        {
            return RepresentativeResult.failed(error.getUserError());
        }
    }

    /**
     * Picks a random candidate representative and assigns it to {@code account}, signing with
     * the main player wallet.
     */
    public RepresentativeResult setRandomRepresentative(String account)
    {
        return setRandomRepresentative(account, configEngine.getWalletId());
    }

    /**
     * Picks a random candidate representative (see {@link #getCandidateRepresentatives()}) and
     * assigns it to {@code account}.
     */
    public RepresentativeResult setRandomRepresentative(String account, String walletId)
    {
        List<String> candidates = getCandidateRepresentatives();

        if (candidates.isEmpty())
        {
            return RepresentativeResult.failed("No representatives available to choose from.");
        }

        String representative = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        return setRepresentative(account, representative, walletId);
    }

    /**
     * Looks up {@code account}'s current representative.
     */
    public RepresentativeInfoResult getCurrentRepresentative(String account)
    {
        String representative = rpc.getRepresentative(account);

        if (representative == null)
        {
            return RepresentativeInfoResult.failed("Could not retrieve representative information.");
        }

        return RepresentativeInfoResult.succeeded(representative);
    }

    /** Outcome of {@link #setRepresentative} / {@link #setRandomRepresentative}. */
    public record RepresentativeResult(boolean success, String userError)
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

    /** Outcome of {@link #getCurrentRepresentative}. */
    public record RepresentativeInfoResult(boolean success, String representative, String userError)
    {
        public static RepresentativeInfoResult succeeded(String representative)
        {
            return new RepresentativeInfoResult(true, representative, null);
        }

        public static RepresentativeInfoResult failed(String userError)
        {
            return new RepresentativeInfoResult(false, null, userError);
        }
    }
}
