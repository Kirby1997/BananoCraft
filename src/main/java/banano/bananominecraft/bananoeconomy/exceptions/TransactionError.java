package banano.bananominecraft.bananoeconomy.exceptions;

public class TransactionError extends Exception
{
    /** Error message to be displayed to the user. */
    private final String userError;

    public TransactionError(final String userError)
    {
        super(userError);
        this.userError = userError;
    }

    public String getUserError()
    {
        return userError;
    }
}
