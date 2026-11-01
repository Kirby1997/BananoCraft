package banano.bananominecraft.bananoeconomy.wallet.crypto;

/**
 * Nano/Banano proof-of-work: value = uint64_le(BLAKE2b-64(uint64_le(work) || root)), and a
 * nonce is valid iff its value is at least the threshold (compared unsigned).
 *
 * <p>The root is the block's previous hash, or the account public key for an open block
 * (see {@link StateBlock#workRoot()}).</p>
 */
public final class Work
{
    /** Banano work threshold, the same for every block type. */
    public static final long BANANO_THRESHOLD = 0xfffffe0000000000L;

    private Work()
    {
    }

    /**
     * Compute the work value of a nonce for a root.
     *
     * @param root32 the 32-byte work root
     * @param work   the 64-bit work nonce (as sent to the RPC in hex, i.e. big-endian digits)
     * @return the work value, to be compared unsigned
     * @throws IllegalArgumentException if the root is not 32 bytes
     */
    public static long value(byte[] root32, long work)
    {
        NanoKeys.requireLength(root32, 32, "work root");
        byte[] nonce = new byte[8];
        for (int i = 0; i < 8; i++)
        {
            nonce[i] = (byte) (work >>> (8 * i));
        }
        return Blake2b.readLongLE(Blake2b.digest(8, nonce, root32), 0);
    }

    /**
     * Whether {@code work} meets {@code threshold} for {@code root32}.
     *
     * @param root32    the 32-byte work root
     * @param work      the work nonce
     * @param threshold the minimum value (unsigned), e.g. {@link #BANANO_THRESHOLD}
     * @return true if {@code value(root32, work) >= threshold} (unsigned)
     */
    public static boolean isValid(byte[] root32, long work, long threshold)
    {
        return Long.compareUnsigned(value(root32, work), threshold) >= 0;
    }
}
