package banano.bananominecraft.bananoeconomy.wallet.crypto;

/**
 * Hexadecimal encoding as used by the Nano/Banano RPC (upper-case on output, either case on input).
 */
public final class Hex
{
    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private Hex()
    {
    }

    /**
     * Encode bytes as upper-case hex.
     *
     * @param b the bytes to encode
     * @return upper-case hex string, two characters per byte
     */
    public static String encode(byte[] b)
    {
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++)
        {
            out[2 * i] = DIGITS[(b[i] >>> 4) & 0xf];
            out[2 * i + 1] = DIGITS[b[i] & 0xf];
        }
        return new String(out);
    }

    /**
     * Decode a hex string (upper- or lower-case, no prefix or separators).
     *
     * @param hex the hex string
     * @return the decoded bytes
     * @throws IllegalArgumentException if the length is odd or a character is not a hex digit
     */
    public static byte[] decode(String hex)
    {
        if (hex == null || (hex.length() & 1) != 0)
        {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++)
        {
            out[i] = (byte) ((digit(hex.charAt(2 * i)) << 4) | digit(hex.charAt(2 * i + 1)));
        }
        return out;
    }

    private static int digit(char c)
    {
        if (c >= '0' && c <= '9')
        {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f')
        {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F')
        {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("Invalid hex character: " + c);
    }
}
