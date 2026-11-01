package banano.bananominecraft.bananoeconomy.wallet.crypto;

import java.util.Arrays;

/**
 * Nano/Banano key derivation, addresses and ed25519-blake2b signatures.
 *
 * <ul>
 *   <li>private key = BLAKE2b-256(seed || uint32_be(index))</li>
 *   <li>public key = Ed25519 public key with BLAKE2b-512 in place of SHA-512</li>
 *   <li>address = prefix + base32(4 zero bits || pubkey) (52 chars)
 *       + base32(reverse(BLAKE2b-40(pubkey))) (8 chars)</li>
 * </ul>
 */
public final class NanoKeys
{
    /** Address prefixes accepted by {@link #publicKeyFromAddress(String)}. */
    private static final String[] KNOWN_PREFIXES = {"ban_", "nano_", "xrb_"};
    private static final char[] ALPHABET = "13456789abcdefghijkmnopqrstuwxyz".toCharArray();
    private static final int[] DECODE = new int[128];
    private static final int KEY_CHARS = 52;
    private static final int CHECKSUM_CHARS = 8;

    static
    {
        Arrays.fill(DECODE, -1);
        for (int i = 0; i < ALPHABET.length; i++)
        {
            DECODE[ALPHABET[i]] = i;
        }
    }

    private NanoKeys()
    {
    }

    /**
     * Derive the private key for account {@code index} of a wallet seed.
     *
     * @param seed32 the 32-byte wallet seed
     * @param index  the account index (treated as unsigned 32-bit; callers use 0..2^31-1)
     * @return the 32-byte private key
     * @throws IllegalArgumentException if the seed is not 32 bytes
     */
    public static byte[] privateKey(byte[] seed32, int index)
    {
        requireLength(seed32, 32, "seed");
        byte[] idx = {(byte) (index >>> 24), (byte) (index >>> 16), (byte) (index >>> 8), (byte) index};
        return Blake2b.digest(32, seed32, idx);
    }

    /**
     * Derive the public key of a private key (ed25519-blake2b).
     *
     * @param privateKey32 the 32-byte private key
     * @return the 32-byte public key
     * @throws IllegalArgumentException if the key is not 32 bytes
     */
    public static byte[] publicKey(byte[] privateKey32)
    {
        return Ed25519.BLAKE2B.publicKey(privateKey32);
    }

    /**
     * Encode a public key as an account address.
     *
     * @param publicKey32 the 32-byte public key
     * @param prefix      the address prefix including the underscore, e.g. {@code "ban_"}
     * @return the address, e.g. {@code ban_1...}
     * @throws IllegalArgumentException if the key is not 32 bytes or the prefix is null/empty
     */
    public static String address(byte[] publicKey32, String prefix)
    {
        requireLength(publicKey32, 32, "public key");
        if (prefix == null || prefix.isEmpty())
        {
            throw new IllegalArgumentException("Address prefix must not be empty");
        }
        byte[] checksum = reverse(Blake2b.digest(5, publicKey32));
        StringBuilder sb = new StringBuilder(prefix.length() + KEY_CHARS + CHECKSUM_CHARS);
        sb.append(prefix);
        // 4 zero bits + 256 key bits = 260 bits = 52 characters
        appendBase32(sb, publicKey32, 4, KEY_CHARS);
        appendBase32(sb, checksum, 0, CHECKSUM_CHARS);
        return sb.toString();
    }

    /**
     * Decode and validate an address with any known prefix ({@code ban_}, {@code nano_},
     * {@code xrb_}). Addresses are case-sensitive (lower-case only), as produced by nodes.
     *
     * <p>Callers that must only accept Banano addresses should check the prefix too, e.g.
     * with {@link #isValidAddress(String, String)}.</p>
     *
     * @param address the account address
     * @return the 32-byte public key
     * @throws IllegalArgumentException if the prefix, length, character set, padding bits or
     *                                  checksum are invalid
     */
    public static byte[] publicKeyFromAddress(String address)
    {
        if (address == null)
        {
            throw new IllegalArgumentException("Address is null");
        }
        for (String prefix : KNOWN_PREFIXES)
        {
            if (address.startsWith(prefix))
            {
                return decode(address, prefix.length());
            }
        }
        throw new IllegalArgumentException("Unknown address prefix: " + address);
    }

    /**
     * Whether {@code address} is a well-formed address with exactly the given prefix and a
     * valid checksum.
     *
     * @param address the address to check (may be null)
     * @param prefix  the required prefix, e.g. {@code "ban_"}
     * @return true if valid
     */
    public static boolean isValidAddress(String address, String prefix)
    {
        if (address == null || prefix == null || prefix.isEmpty() || !address.startsWith(prefix))
        {
            return false;
        }
        try
        {
            decode(address, prefix.length());
            return true;
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    /**
     * Sign a message (for blocks: the 32-byte block hash) with ed25519-blake2b.
     *
     * @param privateKey32 the 32-byte private key
     * @param message      the message bytes
     * @return the 64-byte signature
     * @throws IllegalArgumentException if the key is not 32 bytes or the message is null
     */
    public static byte[] sign(byte[] privateKey32, byte[] message)
    {
        if (message == null)
        {
            throw new IllegalArgumentException("Message is null");
        }
        return Ed25519.BLAKE2B.sign(privateKey32, message);
    }

    /**
     * Verify an ed25519-blake2b signature. Malformed inputs return false rather than throwing.
     *
     * @param publicKey32 the 32-byte public key
     * @param message     the signed message
     * @param signature64 the 64-byte signature
     * @return true if the signature is valid (and canonical) for this key and message
     */
    public static boolean verify(byte[] publicKey32, byte[] message, byte[] signature64)
    {
        return Ed25519.BLAKE2B.verify(publicKey32, message, signature64);
    }

    // -------------------------------------------------------------------------
    // Base32 (Nano alphabet), big-endian bit order
    // -------------------------------------------------------------------------

    /** Append {@code chars} 5-bit groups of ({@code padBits} zero bits || data). */
    private static void appendBase32(StringBuilder sb, byte[] data, int padBits, int chars)
    {
        for (int c = 0; c < chars; c++)
        {
            int value = 0;
            for (int k = 0; k < 5; k++)
            {
                int bit = c * 5 + k - padBits;
                value <<= 1;
                if (bit >= 0)
                {
                    value |= (data[bit >>> 3] >>> (7 - (bit & 7))) & 1;
                }
            }
            sb.append(ALPHABET[value]);
        }
    }

    /** Decode {@code chars} characters into ({@code padBits} zero bits || out); false if padding is non-zero. */
    private static boolean decodeBase32(String s, int off, int chars, int padBits, byte[] out)
    {
        for (int c = 0; c < chars; c++)
        {
            char ch = s.charAt(off + c);
            int value = ch < 128 ? DECODE[ch] : -1;
            if (value < 0)
            {
                throw new IllegalArgumentException("Invalid address character: " + ch);
            }
            for (int k = 0; k < 5; k++)
            {
                int bitValue = (value >>> (4 - k)) & 1;
                int bit = c * 5 + k - padBits;
                if (bit < 0)
                {
                    if (bitValue != 0)
                    {
                        return false;
                    }
                }
                else if (bitValue != 0)
                {
                    out[bit >>> 3] |= (byte) (1 << (7 - (bit & 7)));
                }
            }
        }
        return true;
    }

    private static byte[] decode(String address, int prefixLen)
    {
        if (address.length() != prefixLen + KEY_CHARS + CHECKSUM_CHARS)
        {
            throw new IllegalArgumentException("Invalid address length: " + address);
        }
        byte[] key = new byte[32];
        if (!decodeBase32(address, prefixLen, KEY_CHARS, 4, key))
        {
            throw new IllegalArgumentException("Invalid address (non-zero padding bits): " + address);
        }
        byte[] checksum = new byte[5];
        decodeBase32(address, prefixLen + KEY_CHARS, CHECKSUM_CHARS, 0, checksum);
        if (!Arrays.equals(checksum, reverse(Blake2b.digest(5, key))))
        {
            throw new IllegalArgumentException("Invalid address checksum: " + address);
        }
        return key;
    }

    private static byte[] reverse(byte[] b)
    {
        byte[] r = new byte[b.length];
        for (int i = 0; i < b.length; i++)
        {
            r[i] = b[b.length - 1 - i];
        }
        return r;
    }

    static void requireLength(byte[] b, int len, String what)
    {
        if (b == null || b.length != len)
        {
            throw new IllegalArgumentException(what + " must be " + len + " bytes");
        }
    }
}
