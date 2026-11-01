package banano.bananominecraft.bananoeconomy.wallet.crypto;

/**
 * BLAKE2b hash function (RFC 7693), unkeyed, with a variable digest length of 1..64 bytes.
 *
 * <p>This is the hash used throughout the Nano/Banano protocol: key derivation (256-bit),
 * block hashes (256-bit), address checksums (40-bit), proof-of-work (64-bit) and, at 512 bits,
 * inside ed25519-blake2b signatures.</p>
 */
public final class Blake2b
{
    /** Maximum digest length in bytes. */
    public static final int MAX_OUT_BYTES = 64;

    private static final int BLOCK_BYTES = 128;

    static final long[] IV = {
            0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L,
            0x510e527fade682d1L, 0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    };

    static final byte[][] SIGMA = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
            {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
            {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
            {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
            {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
            {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
            {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
            {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
            {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3}
    };

    private final long[] h = new long[8];
    private final long[] m = new long[16];
    private final long[] v = new long[16];
    private final byte[] buffer = new byte[BLOCK_BYTES];
    private final int outLen;
    private int bufferLen;
    /** Byte counter t (RFC 7693 allows 2^128; inputs here are far below 2^64). */
    private long counter;

    private Blake2b(int outLen)
    {
        this.outLen = outLen;
        System.arraycopy(IV, 0, h, 0, 8);
        // Parameter block word 0: digest length, key length 0, fanout 1, depth 1
        h[0] ^= 0x01010000L ^ outLen;
    }

    /**
     * Hash the concatenation of {@code parts}.
     *
     * @param outLenBytes digest length in bytes, 1..64
     * @param parts       message parts, hashed in order as one message (none means the empty message)
     * @return the {@code outLenBytes}-byte digest
     * @throws IllegalArgumentException if {@code outLenBytes} is out of range or a part is null
     */
    public static byte[] digest(int outLenBytes, byte[]... parts)
    {
        if (outLenBytes < 1 || outLenBytes > MAX_OUT_BYTES)
        {
            throw new IllegalArgumentException("BLAKE2b output length must be 1..64 bytes: " + outLenBytes);
        }
        Blake2b state = new Blake2b(outLenBytes);
        for (byte[] part : parts)
        {
            if (part == null)
            {
                throw new IllegalArgumentException("BLAKE2b input part is null");
            }
            state.update(part);
        }
        return state.finish();
    }

    private void update(byte[] in)
    {
        int off = 0;
        int len = in.length;
        while (len > 0)
        {
            // The final block must be compressed with the last-block flag, so a full buffer
            // is only flushed once more input is known to follow.
            if (bufferLen == BLOCK_BYTES)
            {
                counter += BLOCK_BYTES;
                compress(buffer, false);
                bufferLen = 0;
            }
            int n = Math.min(BLOCK_BYTES - bufferLen, len);
            System.arraycopy(in, off, buffer, bufferLen, n);
            bufferLen += n;
            off += n;
            len -= n;
        }
    }

    private byte[] finish()
    {
        counter += bufferLen;
        for (int i = bufferLen; i < BLOCK_BYTES; i++)
        {
            buffer[i] = 0;
        }
        compress(buffer, true);
        byte[] out = new byte[outLen];
        for (int i = 0; i < outLen; i++)
        {
            out[i] = (byte) (h[i >>> 3] >>> (8 * (i & 7)));
        }
        return out;
    }

    private void compress(byte[] block, boolean last)
    {
        for (int i = 0; i < 16; i++)
        {
            m[i] = readLongLE(block, i * 8);
        }
        System.arraycopy(h, 0, v, 0, 8);
        System.arraycopy(IV, 0, v, 8, 8);
        v[12] ^= counter;
        // v[13] ^= high 64 bits of the counter, always 0 here
        if (last)
        {
            v[14] = ~v[14];
        }
        for (int r = 0; r < 12; r++)
        {
            byte[] s = SIGMA[r];
            g(0, 4, 8, 12, m[s[0]], m[s[1]]);
            g(1, 5, 9, 13, m[s[2]], m[s[3]]);
            g(2, 6, 10, 14, m[s[4]], m[s[5]]);
            g(3, 7, 11, 15, m[s[6]], m[s[7]]);
            g(0, 5, 10, 15, m[s[8]], m[s[9]]);
            g(1, 6, 11, 12, m[s[10]], m[s[11]]);
            g(2, 7, 8, 13, m[s[12]], m[s[13]]);
            g(3, 4, 9, 14, m[s[14]], m[s[15]]);
        }
        for (int i = 0; i < 8; i++)
        {
            h[i] ^= v[i] ^ v[i + 8];
        }
    }

    private void g(int a, int b, int c, int d, long x, long y)
    {
        v[a] = v[a] + v[b] + x;
        v[d] = Long.rotateRight(v[d] ^ v[a], 32);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 24);
        v[a] = v[a] + v[b] + y;
        v[d] = Long.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    static long readLongLE(byte[] b, int off)
    {
        return (b[off] & 0xffL)
                | (b[off + 1] & 0xffL) << 8
                | (b[off + 2] & 0xffL) << 16
                | (b[off + 3] & 0xffL) << 24
                | (b[off + 4] & 0xffL) << 32
                | (b[off + 5] & 0xffL) << 40
                | (b[off + 6] & 0xffL) << 48
                | (b[off + 7] & 0xffL) << 56;
    }
}
