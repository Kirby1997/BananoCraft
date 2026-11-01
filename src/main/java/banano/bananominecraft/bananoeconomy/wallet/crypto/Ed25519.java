package banano.bananominecraft.bananoeconomy.wallet.crypto;

import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * Ed25519 (RFC 8032, pure EdDSA) with a pluggable 512-bit hash. {@link #BLAKE2B} is the
 * Nano/Banano variant ("ed25519-blake2b"): identical to Ed25519 except that every SHA-512
 * call is replaced by BLAKE2b-512.
 *
 * <p>Field and group arithmetic is a port of TweetNaCl (Bernstein et al., public domain):
 * 16 x 16-bit limbs in {@code long}s, a branch-free conditional swap, and a fixed 256-step
 * double-and-add ladder, so scalar multiplication has no secret-dependent branches or memory
 * indices. Curve constants are derived from their definitions at class load rather than typed
 * in. Verification is stricter than TweetNaCl: it rejects non-canonical S (S &gt;= L), as
 * RFC 8032 section 5.1.7 requires.</p>
 *
 * <p>Package-private: the public API is {@link NanoKeys}. The hash is a parameter only so the
 * curve arithmetic can be tested against RFC 8032 vectors and the JDK using SHA-512.</p>
 */
final class Ed25519
{
    /** A 512-bit hash over the concatenation of its arguments. */
    interface Hash512
    {
        byte[] hash(byte[]... parts);
    }

    /** ed25519-blake2b, as used by Nano and Banano. */
    static final Ed25519 BLAKE2B = new Ed25519(parts -> Blake2b.digest(64, parts));

    private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    private static final long[] GF0 = new long[16];
    private static final long[] GF1 = gf(BigInteger.ONE);
    /** d = -121665 / 121666 mod p. */
    private static final long[] D;
    private static final long[] D2;
    /** sqrt(-1) = 2^((p-1)/4) mod p. */
    private static final long[] SQRT_M1;
    private static final long[] BASE_X;
    private static final long[] BASE_Y;
    /** Group order L = 2^252 + 27742317777372353535851937790883648493, little-endian bytes. */
    private static final long[] L = {
            0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10
    };

    static
    {
        BigInteger d = BigInteger.valueOf(-121665).mod(P)
                .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
        D = gf(d);
        D2 = gf(d.shiftLeft(1).mod(P));
        BigInteger sqrtM1 = BigInteger.TWO.modPow(P.subtract(BigInteger.ONE).shiftRight(2), P);
        SQRT_M1 = gf(sqrtM1);
        // Base point: y = 4/5, x recovered from the curve equation with x even (RFC 8032 5.1.3)
        BigInteger y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
        BigInteger v = d.multiply(y2).add(BigInteger.ONE).mod(P);
        BigInteger x2 = u.multiply(v.modInverse(P)).mod(P);
        BigInteger x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (!x.multiply(x).mod(P).equals(x2))
        {
            x = x.multiply(sqrtM1).mod(P);
        }
        if (x.testBit(0))
        {
            x = P.subtract(x);
        }
        if (!x.multiply(x).mod(P).equals(x2))
        {
            throw new ExceptionInInitializerError("Ed25519 base point derivation failed");
        }
        BASE_X = gf(x);
        BASE_Y = gf(y);
    }

    private final Hash512 hash;

    Ed25519(Hash512 hash)
    {
        this.hash = hash;
    }

    // -------------------------------------------------------------------------
    // Public-key operations
    // -------------------------------------------------------------------------

    /** Derive the 32-byte public key from a 32-byte private key (RFC 8032 5.1.5). */
    byte[] publicKey(byte[] privateKey)
    {
        checkLength(privateKey, 32, "private key");
        byte[] a = expandedScalar(hash.hash(privateKey));
        long[][] p = point();
        scalarBase(p, a);
        byte[] pk = new byte[32];
        pack(pk, p);
        return pk;
    }

    /** Sign {@code message} (RFC 8032 5.1.6). Returns R || S, 64 bytes. */
    byte[] sign(byte[] privateKey, byte[] message)
    {
        checkLength(privateKey, 32, "private key");
        byte[] h = hash.hash(privateKey);
        byte[] a = expandedScalar(h);
        byte[] prefix = new byte[32];
        System.arraycopy(h, 32, prefix, 0, 32);

        long[][] p = point();
        scalarBase(p, a);
        byte[] publicKey = new byte[32];
        pack(publicKey, p);

        // r = H(prefix || M) mod L; R = rB
        byte[] r = reduce(hash.hash(prefix, message));
        long[][] rp = point();
        scalarBase(rp, r);
        byte[] sig = new byte[64];
        pack(sig, rp);

        // k = H(R || A || M) mod L; S = (r + k * a) mod L
        byte[] rEnc = new byte[32];
        System.arraycopy(sig, 0, rEnc, 0, 32);
        byte[] k = reduce(hash.hash(rEnc, publicKey, message));
        long[] x = new long[64];
        for (int i = 0; i < 32; i++)
        {
            x[i] = r[i] & 0xff;
        }
        for (int i = 0; i < 32; i++)
        {
            for (int j = 0; j < 32; j++)
            {
                x[i + j] += (k[i] & 0xff) * (long) (a[j] & 0xff);
            }
        }
        byte[] s = new byte[32];
        modL(s, x);
        System.arraycopy(s, 0, sig, 32, 32);
        return sig;
    }

    /** Verify a signature (RFC 8032 5.1.7, cofactorless). Never throws for malformed input. */
    boolean verify(byte[] publicKey, byte[] message, byte[] signature)
    {
        if (publicKey == null || publicKey.length != 32 || signature == null || signature.length != 64
                || message == null)
        {
            return false;
        }
        byte[] s = new byte[32];
        System.arraycopy(signature, 32, s, 0, 32);
        if (!isCanonicalScalar(s))
        {
            return false;
        }
        long[][] negA = point();
        if (!unpackNeg(negA, publicKey))
        {
            return false;
        }
        byte[] rEnc = new byte[32];
        System.arraycopy(signature, 0, rEnc, 0, 32);
        byte[] k = reduce(hash.hash(rEnc, publicKey, message));

        // Check encode(S*B - k*A) == R
        long[][] p = point();
        scalarMult(p, negA, k);
        long[][] sb = point();
        scalarBase(sb, s);
        add(p, sb);
        byte[] t = new byte[32];
        pack(t, p);
        return MessageDigest.isEqual(t, rEnc);
    }

    // -------------------------------------------------------------------------
    // Scalars mod L
    // -------------------------------------------------------------------------

    /** Clamp the first half of H(k) into the secret scalar (RFC 8032 5.1.5 step 2). */
    private static byte[] expandedScalar(byte[] h)
    {
        byte[] a = new byte[32];
        System.arraycopy(h, 0, a, 0, 32);
        a[0] &= (byte) 248;
        a[31] &= 127;
        a[31] |= 64;
        return a;
    }

    /** True iff the little-endian 32-byte value is below L. */
    static boolean isCanonicalScalar(byte[] s)
    {
        // Compare from the most significant byte down; s is public, so branching is fine.
        for (int i = 31; i >= 0; i--)
        {
            int si = s[i] & 0xff;
            int li = (int) L[i];
            if (si < li)
            {
                return true;
            }
            if (si > li)
            {
                return false;
            }
        }
        return false;
    }

    /** Reduce a 64-byte little-endian value mod L to 32 bytes. */
    private static byte[] reduce(byte[] h64)
    {
        long[] x = new long[64];
        for (int i = 0; i < 64; i++)
        {
            x[i] = h64[i] & 0xff;
        }
        byte[] r = new byte[32];
        modL(r, x);
        return r;
    }

    private static void modL(byte[] r, long[] x)
    {
        long carry;
        int i;
        int j;
        for (i = 63; i >= 32; --i)
        {
            carry = 0;
            for (j = i - 32; j < i - 12; ++j)
            {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)];
                carry = (x[j] + 128) >> 8;
                x[j] -= carry << 8;
            }
            x[j] += carry;
            x[i] = 0;
        }
        carry = 0;
        for (j = 0; j < 32; j++)
        {
            x[j] += carry - (x[31] >> 4) * L[j];
            carry = x[j] >> 8;
            x[j] &= 255;
        }
        for (j = 0; j < 32; j++)
        {
            x[j] -= carry * L[j];
        }
        for (i = 0; i < 32; i++)
        {
            x[i + 1] += x[i] >> 8;
            r[i] = (byte) (x[i] & 255);
        }
    }

    // -------------------------------------------------------------------------
    // Group operations (extended coordinates X, Y, Z, T)
    // -------------------------------------------------------------------------

    private static long[][] point()
    {
        return new long[4][16];
    }

    /** p = p + q (also valid for p == q, i.e. doubling). */
    private static void add(long[][] p, long[][] q)
    {
        long[] a = new long[16];
        long[] b = new long[16];
        long[] c = new long[16];
        long[] d = new long[16];
        long[] t = new long[16];
        long[] e = new long[16];
        long[] f = new long[16];
        long[] g = new long[16];
        long[] h = new long[16];

        sub(a, p[1], p[0]);
        sub(t, q[1], q[0]);
        mul(a, a, t);
        add(b, p[0], p[1]);
        add(t, q[0], q[1]);
        mul(b, b, t);
        mul(c, p[3], q[3]);
        mul(c, c, D2);
        mul(d, p[2], q[2]);
        add(d, d, d);
        sub(e, b, a);
        sub(f, d, c);
        add(g, d, c);
        add(h, b, a);

        mul(p[0], e, f);
        mul(p[1], h, g);
        mul(p[2], g, f);
        mul(p[3], e, h);
    }

    private static void cswap(long[][] p, long[][] q, int b)
    {
        for (int i = 0; i < 4; i++)
        {
            sel25519(p[i], q[i], b);
        }
    }

    private static void pack(byte[] r, long[][] p)
    {
        long[] tx = new long[16];
        long[] ty = new long[16];
        long[] zi = new long[16];
        inv25519(zi, p[2]);
        mul(tx, p[0], zi);
        mul(ty, p[1], zi);
        pack25519(r, ty);
        r[31] ^= (byte) (par25519(tx) << 7);
    }

    /** p = s * q with a fixed sequence of operations for every 256-bit s. */
    private static void scalarMult(long[][] p, long[][] q, byte[] s)
    {
        set25519(p[0], GF0);
        set25519(p[1], GF1);
        set25519(p[2], GF1);
        set25519(p[3], GF0);
        for (int i = 255; i >= 0; --i)
        {
            int b = ((s[i >>> 3] & 0xff) >>> (i & 7)) & 1;
            cswap(p, q, b);
            add(q, p);
            add(p, p);
            cswap(p, q, b);
        }
    }

    private static void scalarBase(long[][] p, byte[] s)
    {
        long[][] q = point();
        set25519(q[0], BASE_X);
        set25519(q[1], BASE_Y);
        set25519(q[2], GF1);
        mul(q[3], BASE_X, BASE_Y);
        scalarMult(p, q, s);
    }

    /** Decode a point and negate it; false if it is not on the curve. Public data only. */
    private static boolean unpackNeg(long[][] r, byte[] p)
    {
        long[] t = new long[16];
        long[] chk = new long[16];
        long[] num = new long[16];
        long[] den = new long[16];
        long[] den2 = new long[16];
        long[] den4 = new long[16];
        long[] den6 = new long[16];

        set25519(r[2], GF1);
        unpack25519(r[1], p);
        mul(num, r[1], r[1]);
        mul(den, num, D);
        sub(num, num, r[2]);
        add(den, r[2], den);

        mul(den2, den, den);
        mul(den4, den2, den2);
        mul(den6, den4, den2);
        mul(t, den6, num);
        mul(t, t, den);

        pow2523(t, t);
        mul(t, t, num);
        mul(t, t, den);
        mul(t, t, den);
        mul(r[0], t, den);

        mul(chk, r[0], r[0]);
        mul(chk, chk, den);
        if (neq25519(chk, num))
        {
            mul(r[0], r[0], SQRT_M1);
        }

        mul(chk, r[0], r[0]);
        mul(chk, chk, den);
        if (neq25519(chk, num))
        {
            return false;
        }

        if (par25519(r[0]) == ((p[31] & 0xff) >>> 7))
        {
            sub(r[0], GF0, r[0]);
        }
        mul(r[3], r[0], r[1]);
        return true;
    }

    // -------------------------------------------------------------------------
    // Field arithmetic mod 2^255 - 19 (16 limbs of 16 bits)
    // -------------------------------------------------------------------------

    private static long[] gf(BigInteger v)
    {
        long[] o = new long[16];
        for (int i = 0; i < 16; i++)
        {
            o[i] = v.shiftRight(16 * i).longValue() & 0xffff;
        }
        return o;
    }

    private static void set25519(long[] r, long[] a)
    {
        System.arraycopy(a, 0, r, 0, 16);
    }

    private static void car25519(long[] o)
    {
        for (int i = 0; i < 16; i++)
        {
            o[i] += 1L << 16;
            long c = o[i] >> 16;
            if (i < 15)
            {
                o[i + 1] += c - 1;
            }
            else
            {
                o[0] += 38 * (c - 1);
            }
            o[i] -= c << 16;
        }
    }

    /** Swap p and q iff b == 1, without branching on b. */
    private static void sel25519(long[] p, long[] q, int b)
    {
        long c = ~(b - 1L);
        for (int i = 0; i < 16; i++)
        {
            long t = c & (p[i] ^ q[i]);
            p[i] ^= t;
            q[i] ^= t;
        }
    }

    private static void pack25519(byte[] o, long[] n)
    {
        long[] m = new long[16];
        long[] t = new long[16];
        System.arraycopy(n, 0, t, 0, 16);
        car25519(t);
        car25519(t);
        car25519(t);
        for (int j = 0; j < 2; j++)
        {
            m[0] = t[0] - 0xffed;
            for (int i = 1; i < 15; i++)
            {
                m[i] = t[i] - 0xffff - ((m[i - 1] >> 16) & 1);
                m[i - 1] &= 0xffff;
            }
            m[15] = t[15] - 0x7fff - ((m[14] >> 16) & 1);
            int b = (int) ((m[15] >> 16) & 1);
            m[14] &= 0xffff;
            sel25519(t, m, 1 - b);
        }
        for (int i = 0; i < 16; i++)
        {
            o[2 * i] = (byte) t[i];
            o[2 * i + 1] = (byte) (t[i] >> 8);
        }
    }

    private static boolean neq25519(long[] a, long[] b)
    {
        byte[] c = new byte[32];
        byte[] d = new byte[32];
        pack25519(c, a);
        pack25519(d, b);
        return !MessageDigest.isEqual(c, d);
    }

    private static int par25519(long[] a)
    {
        byte[] d = new byte[32];
        pack25519(d, a);
        return d[0] & 1;
    }

    private static void unpack25519(long[] o, byte[] n)
    {
        for (int i = 0; i < 16; i++)
        {
            o[i] = (n[2 * i] & 0xff) + ((long) (n[2 * i + 1] & 0xff) << 8);
        }
        o[15] &= 0x7fff;
    }

    private static void add(long[] o, long[] a, long[] b)
    {
        for (int i = 0; i < 16; i++)
        {
            o[i] = a[i] + b[i];
        }
    }

    private static void sub(long[] o, long[] a, long[] b)
    {
        for (int i = 0; i < 16; i++)
        {
            o[i] = a[i] - b[i];
        }
    }

    private static void mul(long[] o, long[] a, long[] b)
    {
        long[] t = new long[31];
        for (int i = 0; i < 16; i++)
        {
            for (int j = 0; j < 16; j++)
            {
                t[i + j] += a[i] * b[j];
            }
        }
        for (int i = 0; i < 15; i++)
        {
            t[i] += 38 * t[i + 16];
        }
        System.arraycopy(t, 0, o, 0, 16);
        car25519(o);
        car25519(o);
    }

    private static void inv25519(long[] o, long[] i)
    {
        long[] c = new long[16];
        System.arraycopy(i, 0, c, 0, 16);
        for (int a = 253; a >= 0; a--)
        {
            mul(c, c, c);
            if (a != 2 && a != 4)
            {
                mul(c, c, i);
            }
        }
        System.arraycopy(c, 0, o, 0, 16);
    }

    private static void pow2523(long[] o, long[] i)
    {
        long[] c = new long[16];
        System.arraycopy(i, 0, c, 0, 16);
        for (int a = 250; a >= 0; a--)
        {
            mul(c, c, c);
            if (a != 1)
            {
                mul(c, c, i);
            }
        }
        System.arraycopy(c, 0, o, 0, 16);
    }

    private static void checkLength(byte[] b, int len, String what)
    {
        if (b == null || b.length != len)
        {
            throw new IllegalArgumentException(what + " must be " + len + " bytes");
        }
    }
}
