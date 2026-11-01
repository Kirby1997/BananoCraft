package banano.bananominecraft.bananoeconomy.exceptions;

/**
 * A {@link TransactionError} for a block whose fate is unknown: it was published but the network's
 * answer was lost, and re-checking the account did not settle whether it was accepted. Callers must
 * not treat this as a plain failure (the funds may have moved); the user should check their
 * history before retrying.
 */
public class UnknownOutcomeError extends TransactionError
{
    /** User-facing message shown instead of a generic failure. */
    public static final String USER_MESSAGE = "Transaction status unknown - check /history before retrying.";

    public UnknownOutcomeError()
    {
        super(USER_MESSAGE);
    }
}
