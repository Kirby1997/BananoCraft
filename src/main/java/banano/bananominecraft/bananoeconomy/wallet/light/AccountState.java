package banano.bananominecraft.bananoeconomy.wallet.light;

import java.math.BigInteger;

/**
 * Immutable snapshot of an account's chain head as reported by {@code account_info}.
 *
 * <p>For an account that has never been opened (the node answers {@code "Account not found"}),
 * {@link #opened()} is false, {@link #frontier()} is 32 zero bytes, {@link #balance()} is zero and
 * {@link #representative()} is {@code null}.</p>
 */
public final class AccountState
{
    private static final byte[] ZERO = new byte[32];

    private final boolean opened;
    private final byte[] frontier;
    private final BigInteger balance;
    private final String representative;

    AccountState(boolean opened, byte[] frontier, BigInteger balance, String representative)
    {
        this.opened = opened;
        this.frontier = frontier == null ? ZERO.clone() : frontier.clone();
        this.balance = balance == null ? BigInteger.ZERO : balance;
        this.representative = representative;
    }

    static AccountState unopened()
    {
        return new AccountState(false, null, BigInteger.ZERO, null);
    }

    /**
     * @return whether the account has an open block on the ledger
     */
    public boolean opened()
    {
        return opened;
    }

    /**
     * @return a copy of the 32-byte hash of the account's latest block (all zero when unopened)
     */
    public byte[] frontier()
    {
        return frontier.clone();
    }

    /**
     * @return the account balance in raw (zero when unopened)
     */
    public BigInteger balance()
    {
        return balance;
    }

    /**
     * @return the account's representative address, or {@code null} when unopened or not reported
     */
    public String representative()
    {
        return representative;
    }

    @Override
    public String toString()
    {
        return "AccountState{opened=" + opened + ", balance=" + balance + ", representative=" + representative + "}";
    }
}
