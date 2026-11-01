package banano.bananominecraft.bananoeconomy.classes;

/**
 * Result of moving funds on behalf of a synchronous caller (Vault, the bank layer).
 *
 * <p>{@link #UNKNOWN} means the block was published but its fate could not be established (light
 * wallet mode only). It must <em>not</em> be reported to the caller as a failure: a shop that
 * sees a failure would retry or refund and the funds could move twice. The wallet engine always
 * republishes / resolves that same block before building anything else on the account, so the
 * operation is reported as a success and the situation is logged at {@code SEVERE} so an operator can
 * reconcile it with {@code /history}.</p>
 */
public enum TransferOutcome
{
    /** The transfer was accepted by the network. */
    OK,

    /** The transfer did not happen (nothing moved). */
    FAILED,

    /** The transfer may or may not have happened; reported to callers as success. */
    UNKNOWN;

    /** Note appended to log lines and Vault responses for an {@link #UNKNOWN} outcome. */
    public static final String UNKNOWN_NOTE = "outcome unknown – reconcile with /history";

    /** {@code true} for {@link #OK} and {@link #UNKNOWN}: what a boolean caller must be told. */
    public boolean isSuccess()
    {
        return this != FAILED;
    }
}
