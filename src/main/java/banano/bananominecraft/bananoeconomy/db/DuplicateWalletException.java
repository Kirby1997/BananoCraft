package banano.bananominecraft.bananoeconomy.db;

/**
 * Thrown by a storage backend when inserting or updating a record violates the UNIQUE constraint on
 * the wallet address or on the wallet derivation index (another record, possibly written by another
 * server sharing the database, already owns it). Callers treat it as "this index/address is taken" and
 * try the next index; nothing was written.
 */
public class DuplicateWalletException extends RuntimeException
{
    public DuplicateWalletException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
