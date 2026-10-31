package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;

/**
 * Framework-free core of the withdraw flow: send funds from a wallet to a raw Banano
 * address. Shared by {@code WithdrawCommand} (player wallet) and {@code AdminCommand}'s
 * server-wallet withdraw (master wallet).
 *
 * <p>Knows nothing about Bukkit, scheduling, or messaging — so it is unit-testable with
 * plain mocks. The command layer keeps argument parsing, the async boundary, and the
 * explorer-link rendering.</p>
 */
public final class WithdrawService
{
    private static final String ARG_ALL = "all";

    private final RPC rpc;

    public WithdrawService(RPC rpc)
    {
        this.rpc = rpc;
    }

    /**
     * Resolve an amount argument: {@code "all"} maps to the current balance of
     * {@code sourceWallet}; otherwise the string is parsed as a double.
     *
     * @throws NumberFormatException if {@code amountArg} is neither "all" nor a number
     */
    public double resolveAmount(String sourceWallet, String amountArg)
    {
        if (amountArg.equalsIgnoreCase(ARG_ALL))
        {
            return rpc.getBalance(sourceWallet);
        }
        return Double.parseDouble(amountArg);
    }

    /**
     * Send {@code amount} from {@code sourceWallet} to {@code destAddress}.
     *
     * @return a {@link WithdrawResult}; {@code success} carries the block hash, otherwise
     *         {@code userError} carries the node's failure reason
     */
    public WithdrawResult withdraw(String sourceWallet, String destAddress, double amount)
    {
        try
        {
            return WithdrawResult.success(rpc.sendTransaction(sourceWallet, destAddress, amount));
        }
        catch (final TransactionError error)
        {
            return WithdrawResult.failed(error.getUserError());
        }
    }

    /** Outcome of {@link #withdraw}. */
    public record WithdrawResult(boolean success, String blockHash, String userError)
    {
        public static WithdrawResult success(String blockHash)
        {
            return new WithdrawResult(true, blockHash, null);
        }

        public static WithdrawResult failed(String userError)
        {
            return new WithdrawResult(false, null, userError);
        }
    }
}
