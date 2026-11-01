package banano.bananominecraft.bananoeconomy.wallet.crypto;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The curve arithmetic is shared by both hash variants, so it is checked here in its standard
 * SHA-512 form against RFC 8032 vectors, the JDK and BouncyCastle. The BLAKE2b variant is
 * checked against known Nano vectors in {@link NanoKeysTest} and {@link StateBlockTest}.
 */
class Ed25519Test
{
    private static final Ed25519 SHA512 = new Ed25519(parts ->
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            for (byte[] p : parts)
            {
                md.update(p);
            }
            return md.digest();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    });

    private static final BigInteger L =
            BigInteger.ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));

    @Test
    void rfc8032Section7_1_vectors()
    {
        String[][] vectors = {
                {"9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
                        "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
                        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                                + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"},
                {"4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
                        "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
                        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da"
                                + "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"},
                {"c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
                        "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025", "af82",
                        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac"
                                + "18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"},
        };
        for (String[] v : vectors)
        {
            byte[] sk = Hex.decode(v[0]);
            byte[] msg = Hex.decode(v[2]);
            assertEquals(v[1].toUpperCase(), Hex.encode(SHA512.publicKey(sk)));
            assertEquals(v[3].toUpperCase(), Hex.encode(SHA512.sign(sk, msg)));
            assertTrue(SHA512.verify(Hex.decode(v[1]), msg, Hex.decode(v[3])));
        }
    }

    @Test
    void matchesJdkAndBouncyCastle_randomKeysAndMessages() throws Exception
    {
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        Random rnd = new Random(8032);
        for (int i = 0; i < 200; i++)
        {
            byte[] sk = new byte[32];
            rnd.nextBytes(sk);
            byte[] msg = new byte[rnd.nextInt(200)];
            rnd.nextBytes(msg);

            byte[] pk = SHA512.publicKey(sk);
            byte[] sig = SHA512.sign(sk, msg);

            byte[] bcPk = new byte[32];
            org.bouncycastle.math.ec.rfc8032.Ed25519.generatePublicKey(sk, 0, bcPk, 0);
            assertArrayEquals(bcPk, pk, "public key " + i);

            PrivateKey jdkSk = kf.generatePrivate(new EdECPrivateKeySpec(NamedParameterSpec.ED25519, sk));
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(jdkSk);
            signer.update(msg);
            assertArrayEquals(signer.sign(), sig, "signature " + i);

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(jdkPublicKey(kf, pk));
            verifier.update(msg);
            assertTrue(verifier.verify(sig), "JDK verifies " + i);
            assertTrue(SHA512.verify(pk, msg, sig), "we verify " + i);
        }
    }

    @Test
    void verify_rejectsNonCanonicalS()
    {
        byte[] sk = new byte[32];
        sk[0] = 1;
        byte[] msg = "msg".getBytes();
        byte[] sig = SHA512.sign(sk, msg);
        byte[] pk = SHA512.publicKey(sk);
        assertTrue(SHA512.verify(pk, msg, sig));

        // S + L is the same scalar mod L but not canonical (RFC 8032 5.1.7 requires S < L)
        byte[] s = new byte[32];
        System.arraycopy(sig, 32, s, 0, 32);
        BigInteger sPlusL = littleEndian(s).add(L);
        byte[] malleated = sig.clone();
        System.arraycopy(toLittleEndian32(sPlusL), 0, malleated, 32, 32);
        assertFalse(SHA512.verify(pk, msg, malleated));
    }

    @Test
    void isCanonicalScalar_boundaries()
    {
        assertTrue(Ed25519.isCanonicalScalar(toLittleEndian32(BigInteger.ZERO)));
        assertTrue(Ed25519.isCanonicalScalar(toLittleEndian32(L.subtract(BigInteger.ONE))));
        assertFalse(Ed25519.isCanonicalScalar(toLittleEndian32(L)));
        assertFalse(Ed25519.isCanonicalScalar(toLittleEndian32(L.add(BigInteger.ONE))));
    }

    @Test
    void verify_malformedInputs_returnFalse()
    {
        byte[] sk = new byte[32];
        byte[] pk = SHA512.publicKey(sk);
        byte[] sig = SHA512.sign(sk, new byte[0]);
        assertFalse(SHA512.verify(pk, new byte[1], sig));
        assertFalse(SHA512.verify(null, new byte[0], sig));
        assertFalse(SHA512.verify(new byte[31], new byte[0], sig));
        assertFalse(SHA512.verify(pk, new byte[0], new byte[63]));
        assertFalse(SHA512.verify(pk, null, sig));
        // A y-coordinate for which x^2 = (y^2 - 1) / (d y^2 + 1) has no root is not a curve point
        assertFalse(SHA512.verify(toLittleEndian32(yNotOnCurve()), new byte[0], sig));
    }

    @Test
    void publicKey_wrongLength_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> SHA512.publicKey(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> SHA512.sign(null, new byte[0]));
    }

    /** Smallest y >= 2 whose x^2 is a quadratic non-residue mod p (Euler's criterion). */
    private static BigInteger yNotOnCurve()
    {
        BigInteger p = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
        BigInteger d = BigInteger.valueOf(-121665).mod(p).multiply(BigInteger.valueOf(121666).modInverse(p)).mod(p);
        for (BigInteger y = BigInteger.TWO; ; y = y.add(BigInteger.ONE))
        {
            BigInteger y2 = y.multiply(y).mod(p);
            BigInteger x2 = y2.subtract(BigInteger.ONE).multiply(d.multiply(y2).add(BigInteger.ONE).modInverse(p)).mod(p);
            if (x2.modPow(p.subtract(BigInteger.ONE).shiftRight(1), p).equals(p.subtract(BigInteger.ONE)))
            {
                return y;
            }
        }
    }

    private static PublicKey jdkPublicKey(KeyFactory kf, byte[] encoded) throws Exception
    {
        byte[] y = encoded.clone();
        boolean xOdd = (y[31] & 0x80) != 0;
        y[31] &= 0x7f;
        return kf.generatePublic(new EdECPublicKeySpec(NamedParameterSpec.ED25519,
                new EdECPoint(xOdd, littleEndian(y))));
    }

    private static BigInteger littleEndian(byte[] b)
    {
        byte[] be = new byte[b.length];
        for (int i = 0; i < b.length; i++)
        {
            be[i] = b[b.length - 1 - i];
        }
        return new BigInteger(1, be);
    }

    private static byte[] toLittleEndian32(BigInteger v)
    {
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++)
        {
            out[i] = v.shiftRight(8 * i).byteValue();
        }
        return out;
    }
}
