package banano.bananominecraft.bananoeconomy.wallet.light;

/**
 * Failure of a light-wallet operation. The {@link Kind} tells the caller whether funds may have moved.
 */
public class LightWalletException extends Exception
{
    /**
     * Classification of a failure.
     */
    public enum Kind
    {
        /** The node definitively refused the block (fork, stale frontier, bad work, unreceivable, ...). Nothing moved. */
        REJECTED,
        /**
         * The block was signed and possibly delivered, but whether it is on the ledger could not be determined.
         * Funds MAY have moved: callers must not treat this as a failure (e.g. must not refund). The wallet keeps
         * the block and republishes it before building anything else on that account.
         */
        UNKNOWN_OUTCOME,
        /** The node could not be reached or gave an unusable answer before any block was published. Nothing moved. */
        UNAVAILABLE,
        /** The account balance is lower than the requested amount (or the account is unopened). Nothing moved. */
        INSUFFICIENT_BALANCE,
        /** The request itself is invalid (bad address, amount, index, hash or source block). Nothing moved. */
        INVALID
    }

    private static final long serialVersionUID = 1L;

    private final Kind kind;

    /**
     * @param kind    the failure classification
     * @param message human-readable detail
     */
    public LightWalletException(Kind kind, String message)
    {
        super(message);
        this.kind = kind;
    }

    /**
     * @param kind    the failure classification
     * @param message human-readable detail
     * @param cause   the underlying cause
     */
    public LightWalletException(Kind kind, String message, Throwable cause)
    {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * @return the failure classification
     */
    public Kind kind()
    {
        return kind;
    }

    @Override
    public String getMessage()
    {
        return kind + ": " + super.getMessage();
    }
}
