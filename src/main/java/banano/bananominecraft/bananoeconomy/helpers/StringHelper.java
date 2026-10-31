package banano.bananominecraft.bananoeconomy.helpers;

import java.util.regex.Pattern;

public class StringHelper
{
    /** Matches the "user:pass@" part of a URL's authority (scheme kept in group 1). */
    private static final Pattern URL_USER_INFO = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*://)[^/?#@]*@");

    private StringHelper()
    {
    }

    public static String padLeft(String string, int length)
    {
        if (string.length() <= length)
        {
            return " ".repeat(length - string.length()) + string;
        }

        return string.substring(0, length);
    }

    public static String padRight(String string, int length)
    {
        if (string.length() <= length)
        {
            return string + " ".repeat( length - string.length());
        }

        return string.substring(0, length);
    }

    public static String left(String string, int length)
    {
        if (string.length() > length)
        {
            return string.substring(0, length);
        }

        return string;
    }

    public static String right(String string, int length)
    {
        if (string.length() > length)
        {
            return string.substring(string.length() - length);
        }

        return string;
    }

    /**
     * Removes any user-info (e.g. {@code user:pass@}) from a URL so credentials embedded in
     * a node address are never shown to players. Returns the input unchanged if it has none.
     */
    public static String stripUrlUserInfo(String url)
    {
        if (url == null)
        {
            return null;
        }

        return URL_USER_INFO.matcher(url).replaceFirst("$1");
    }
}
