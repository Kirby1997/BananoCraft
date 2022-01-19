package banano.bananominecraft.bananoeconomy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validator {
    public static boolean validateAddress(String account_address){
        Pattern pattern = Pattern.compile("^ban_[13]{1}[13456789abcdefghijkmnopqrstuwxyz]{59}$");
        Matcher matcher = pattern.matcher(account_address);
        return matcher.find();
    }
}
