package banano.bananominecraft.bananoeconomy.wallet.crypto;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Blake2bTest
{
    /** Message used by the fixed vectors below: byte i = (7i + 3) mod 256. */
    private static byte[] pattern(int n)
    {
        byte[] m = new byte[n];
        for (int i = 0; i < n; i++)
        {
            m[i] = (byte) (i * 7 + 3);
        }
        return m;
    }

    @Test
    void rfc7693AppendixA_abc()
    {
        assertEquals("BA80A53F981C4D0D6A2797B69F12F6E94C212F14685AC4B74B12BB6FDBFFA2D1"
                        + "7D87C5392AAB792DC252D5DE4533CC9518D38AA8DBF1925AB92386EDD4009923",
                Hex.encode(Blake2b.digest(64, "abc".getBytes())));
    }

    @Test
    void emptyMessage_512()
    {
        assertEquals("786A02F742015903C6C6FD852552D272912F4740E15847618A86E217F71F5419"
                        + "D25E1031AFEE585313896444934EB04B903A685B1448B755D56F701AFE9BE2CE",
                Hex.encode(Blake2b.digest(64)));
    }

    @Test
    void fixedVectors_variousLengthsAndDigestSizes()
    {
        // Produced with Python hashlib.blake2b (CPython's reference BLAKE2 implementation)
        Object[][] vectors = {
                {3, 32, "9FACD640130C81E7F318A8D5C660BC1B0F5D03861FFBD4CC1358E0E904593C28"},
                {3, 5, "63F8DCDDE3"},
                {3, 8, "66DCA9EC9FCD9A2C"},
                {1, 1, "02"},
                {128, 64, "2D9E329F42AFA3601D646692B81C13E87FCAFF5BF15972E9813D7373CB6D181F"
                        + "9599F4D513D4AF4FD6EBD37497ACEB29ABA5EE23ED764D8510B552BD088814FB"},
                {129, 32, "A34A4E1E03C541DFBF3099C4B6C143C022CED65C28BD7E8A10E0A098461AECF0"},
                {255, 48, "35D2A811855F40526EECA836DF8678F0998026B4C7E8695D9FC5E2F6C848E07D"
                        + "3E324E00F3CB094336D7B0FDDC2AFF10"},
                {256, 64, "91019C558584980249CA43ECEED27E19F1C3C24161B93EED1EEE2A6A774F60BF"
                        + "8A81B43750870BEE1698FEAC9C5336AE4D5C842E7EAD159BF3916387E8DED9AE"},
                {1000, 32, "D62B6C768CE1AFC8367E0498AB2F8E3F7C178C35B1429F14C4604B545D200F52"},
        };
        for (Object[] v : vectors)
        {
            assertEquals(v[2], Hex.encode(Blake2b.digest((Integer) v[1], pattern((Integer) v[0]))),
                    "len=" + v[0] + " out=" + v[1]);
        }
    }

    @Test
    void matchesBouncyCastle_randomInputs()
    {
        Random rnd = new Random(7693);
        for (int i = 0; i < 2000; i++)
        {
            int outLen = 1 + rnd.nextInt(64);
            byte[] msg = new byte[rnd.nextInt(600)];
            rnd.nextBytes(msg);
            Blake2bDigest bc = new Blake2bDigest(null, outLen, null, null);
            bc.update(msg, 0, msg.length);
            byte[] expected = new byte[outLen];
            bc.doFinal(expected, 0);
            assertArrayEquals(expected, Blake2b.digest(outLen, msg), "len=" + msg.length + " out=" + outLen);
        }
    }

    @Test
    void multipleParts_equalConcatenation()
    {
        Random rnd = new Random(1);
        for (int i = 0; i < 300; i++)
        {
            byte[] a = new byte[rnd.nextInt(300)];
            byte[] b = new byte[rnd.nextInt(300)];
            byte[] c = new byte[rnd.nextInt(300)];
            rnd.nextBytes(a);
            rnd.nextBytes(b);
            rnd.nextBytes(c);
            byte[] all = new byte[a.length + b.length + c.length];
            System.arraycopy(a, 0, all, 0, a.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            System.arraycopy(c, 0, all, a.length + b.length, c.length);
            assertArrayEquals(Blake2b.digest(32, all), Blake2b.digest(32, a, b, c));
        }
    }

    @Test
    void invalidArguments_rejected()
    {
        assertThrows(IllegalArgumentException.class, () -> Blake2b.digest(0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> Blake2b.digest(65, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> Blake2b.digest(32, (byte[]) null));
    }
}
