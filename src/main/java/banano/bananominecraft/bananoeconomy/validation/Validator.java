package banano.bananominecraft.bananoeconomy.validation;

import java.util.regex.Pattern;

public class Validator
{
    /**
     * Compiled once at class load time — reused for every address validation so
     * that {@link Pattern#compile} is not called on every transaction.
     */
    private static final Pattern BAN_ADDRESS_PATTERN =
            Pattern.compile("^ban_[13][13456789abcdefghijkmnopqrstuwxyz]{59}$");

    private Validator() {}

    public static boolean validateAddress(String accountAddress)
    {
        if (accountAddress == null)
        {
            return false;
        }

        return BAN_ADDRESS_PATTERN.matcher(accountAddress).find();
    }
}
