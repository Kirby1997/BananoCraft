package banano.bananominecraft.bananoeconomy.wallet.crypto;

/**
 * Allocation-free proof-of-work hash for one root: BLAKE2b-64 over the 40-byte message
 * uint64_le(work) || root, specialised to a single final block with message words 5..15 zero.
 *
 * <p>The round function is mechanically unrolled from RFC 7693 (12 rounds, SIGMA[r mod 10]);
 * tests check it against {@link Blake2b} and {@link Work#value(byte[], long)}, and
 * {@link CpuWorkGenerator} re-verifies every result with {@link Work#isValid} before using it.</p>
 */
final class WorkHasher
{
    /** h[0] after the parameter block: 8-byte digest, no key, fanout 1, depth 1. */
    private static final long H0 = Blake2b.IV[0] ^ 0x01010008L;
    private static final long MESSAGE_BYTES = 40;

    private final long m1;
    private final long m2;
    private final long m3;
    private final long m4;

    WorkHasher(byte[] root32)
    {
        NanoKeys.requireLength(root32, 32, "work root");
        m1 = Blake2b.readLongLE(root32, 0);
        m2 = Blake2b.readLongLE(root32, 8);
        m3 = Blake2b.readLongLE(root32, 16);
        m4 = Blake2b.readLongLE(root32, 24);
    }

    /** The work value of {@code work} for this root (see {@link Work#value(byte[], long)}). */
    long value(long work)
    {
        final long m0 = work;
        long v0 = H0;
        long v1 = Blake2b.IV[1];
        long v2 = Blake2b.IV[2];
        long v3 = Blake2b.IV[3];
        long v4 = Blake2b.IV[4];
        long v5 = Blake2b.IV[5];
        long v6 = Blake2b.IV[6];
        long v7 = Blake2b.IV[7];
        long v8 = Blake2b.IV[0];
        long v9 = Blake2b.IV[1];
        long v10 = Blake2b.IV[2];
        long v11 = Blake2b.IV[3];
        long v12 = Blake2b.IV[4] ^ MESSAGE_BYTES;
        long v13 = Blake2b.IV[5];
        long v14 = ~Blake2b.IV[6];
        long v15 = Blake2b.IV[7];
        final long m1 = this.m1;
        final long m2 = this.m2;
        final long m3 = this.m3;
        final long m4 = this.m4;

        // Generated: G(a, b, c, d, x, y) as two lines per call; absent words are zero.
        // round 0
        v0 += v4 + m0; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4 + m1; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5 + m2; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5 + m3; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6 + m4; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 1
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5 + m4; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5 + m1; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6 + m0; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6 + m2; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4 + m3; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 2
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5 + m0; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6 + m2; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6 + m3; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7 + m1; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4 + m4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 3
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5 + m3; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5 + m1; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5 + m2; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7 + m4; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7 + m0; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 4
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4 + m0; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6 + m2; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6 + m4; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5 + m1; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4 + m3; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 5
        v0 += v4 + m2; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6 + m0; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7 + m3; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5 + m4; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4 + m1; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 6
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5 + m1; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7 + m4; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5 + m0; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6 + m3; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7 + m2; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 7
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6 + m1; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7 + m3; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5 + m0; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6 + m4; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4 + m2; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 8
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6 + m3; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7 + m0; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5 + m2; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7 + m1; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7 + m4; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 9
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4 + m2; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5 + m4; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7 + m1; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7 + m3; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4 + m0; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 10
        v0 += v4 + m0; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4 + m1; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5 + m2; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5 + m3; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6 + m4; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);
        // round 11
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 32); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 24);
        v0 += v4; v12 = Long.rotateRight(v12 ^ v0, 16); v8 += v12; v4 = Long.rotateRight(v4 ^ v8, 63);
        v1 += v5 + m4; v13 = Long.rotateRight(v13 ^ v1, 32); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 24);
        v1 += v5; v13 = Long.rotateRight(v13 ^ v1, 16); v9 += v13; v5 = Long.rotateRight(v5 ^ v9, 63);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 32); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 24);
        v2 += v6; v14 = Long.rotateRight(v14 ^ v2, 16); v10 += v14; v6 = Long.rotateRight(v6 ^ v10, 63);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 32); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 24);
        v3 += v7; v15 = Long.rotateRight(v15 ^ v3, 16); v11 += v15; v7 = Long.rotateRight(v7 ^ v11, 63);
        v0 += v5 + m1; v15 = Long.rotateRight(v15 ^ v0, 32); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 24);
        v0 += v5; v15 = Long.rotateRight(v15 ^ v0, 16); v10 += v15; v5 = Long.rotateRight(v5 ^ v10, 63);
        v1 += v6 + m0; v12 = Long.rotateRight(v12 ^ v1, 32); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 24);
        v1 += v6 + m2; v12 = Long.rotateRight(v12 ^ v1, 16); v11 += v12; v6 = Long.rotateRight(v6 ^ v11, 63);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 32); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 24);
        v2 += v7; v13 = Long.rotateRight(v13 ^ v2, 16); v8 += v13; v7 = Long.rotateRight(v7 ^ v8, 63);
        v3 += v4; v14 = Long.rotateRight(v14 ^ v3, 32); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 24);
        v3 += v4 + m3; v14 = Long.rotateRight(v14 ^ v3, 16); v9 += v14; v4 = Long.rotateRight(v4 ^ v9, 63);

        // Only the first output word is needed: h0' = h0 ^ v0 ^ v8, read little-endian
        return H0 ^ v0 ^ v8;
    }
}
