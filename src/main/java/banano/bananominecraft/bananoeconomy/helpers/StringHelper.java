package banano.bananominecraft.bananoeconomy.helpers;

public class StringHelper
{
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
}
