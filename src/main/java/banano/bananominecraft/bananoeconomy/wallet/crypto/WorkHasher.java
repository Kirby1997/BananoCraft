package banano.bananominecraft.bananoeconomy.wallet.crypto;

/**
 * Allocation-free proof-of-work hash for one root: BLAKE2b-64 over the 40-byte message
 * uint64_le(work) || root, specialised to a single final block (RFC 7693 compression with
 * t = 40 and the last-block flag).
 *
 * <p>Not thread-safe: it reuses its working arrays, so each search thread needs its own
 * instance. Tests check it against {@link Blake2b} and {@link Work#value(byte[], long)}, and
 * {@link CpuWorkGenerator} re-verifies every result with {@link Work#isValid} before using it.</p>
 */
final class WorkHasher
{
    /** h[0] after the parameter block: 8-byte digest, no key, fanout 1, depth 1. */
    private static final long H0 = Blake2b.IV[0] ^ 0x01010008L;
    private static final long MESSAGE_BYTES = 40;

    /** Message words: m[0] = work, m[1..4] = root, m[5..15] = 0. */
    private final long[] m = new long[16];
    private final long[] v = new long[16];

    WorkHasher(byte[] root32)
    {
        NanoKeys.requireLength(root32, 32, "work root");
        for (int i = 0; i < 4; i++)
        {
            m[i + 1] = Blake2b.readLongLE(root32, 8 * i);
        }
    }

    /** The work value of {@code work} for this root (see {@link Work#value(byte[], long)}). */
    long value(long work)
    {
        long[] v = this.v;
        long[] m = this.m;
        m[0] = work;
        v[0] = H0;
        System.arraycopy(Blake2b.IV, 1, v, 1, 7);
        System.arraycopy(Blake2b.IV, 0, v, 8, 8);
        v[12] ^= MESSAGE_BYTES;
        v[14] = ~v[14];
        for (int r = 0; r < 12; r++)
        {
            byte[] s = Blake2b.SIGMA[r];
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]]);
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]]);
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]]);
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]]);
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]]);
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]]);
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]]);
        }
        // Only the first output word is needed: h0' = h0 ^ v0 ^ v8, read little-endian
        return H0 ^ v[0] ^ v[8];
    }

    private static void g(long[] v, int a, int b, int c, int d, long x, long y)
    {
        long va = v[a];
        long vb = v[b];
        long vc = v[c];
        long vd = v[d];
        va += vb + x;
        vd = Long.rotateRight(vd ^ va, 32);
        vc += vd;
        vb = Long.rotateRight(vb ^ vc, 24);
        va += vb + y;
        vd = Long.rotateRight(vd ^ va, 16);
        vc += vd;
        vb = Long.rotateRight(vb ^ vc, 63);
        v[a] = va;
        v[b] = vb;
        v[c] = vc;
        v[d] = vd;
    }
}
