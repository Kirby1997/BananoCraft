package banano.bananominecraft.bananoeconomy.wallet.crypto;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NanoKeysTest
{
    /**
     * seed hex, index, private key, public key, ban_ address. Row 0 is the widely published Nano
     * vector (all-zero seed, index 0); the rest were produced by the jNano 2.20.1 differential run
     * (300 random seeds, 0 mismatches).
     */
    private static final String[][] KEY_VECTORS = {
            {"0000000000000000000000000000000000000000000000000000000000000000", "0",
                    "9F0E444C69F77A49BD0BE89DB92C38FE713E0963165CCA12FAF5712D7657120F",
                    "C008B814A7D269A1FA3C6528B19201A24D797912DB9996FF02A1FF356E45552B",
                    "ban_3i1aq1cchnmbn9x5rsbap8b15akfh7wj7pwskuzi7ahz8oq6cobd99d4r3b7"},
            {"E3AF26B5E40389397A21ED8836D521260A56E37E6B813B262C360387488A0C0A", "0",
                    "B7C4C0AD505E623000040A8472B57E0C9CF91504B768B52D1D72C6E7BF39BBDB",
                    "B183170D15ACE3C2A665B6BDC5FC8099F9E93F0D9F6063DBC599C993B91F954A",
                    "ban_3ee54w8jdd95rcm8dfoxrqya38hsx6ziu9u1ehfwd8gbkgwjz7cci5mga1cr"},
            {"DFE9E1134B14807F9BABD1FF7815398BE510FB25624937246616D4F8CD6F1A1B", "1",
                    "218AF654FB7DF198CB35CDA6EBF5A998002C0D99A74BB774A02D27773D38B089",
                    "34ECA263BFFA07621DD73B3C2230EEBACDB50FADDE3533F3C88FFF1FDC92BE31",
                    "ban_1f9enbjuzyi9eagxggsw6argxgpfpn9tuqjo8hswj5zz5zgb7hjjpqgaf31b"},
            {"16404E9A38FA8F599518DBC6BC2345CF9D56E6276CA7C50583839BBC299F72BE", "2147483647",
                    "A26E4CD991A15BA67B9C7013E7C64A031B4BF0A0453872E765821F6B3307DCEC",
                    "90FB7C4793EEFB6F4688D7EF1AB3A1DF17C25E8678D433F9D7D2A40785F88B02",
                    "ban_369uhj5s9uqufx5ajozh5cst5qrqrbhaey8n8hwxhno61y4zj4r4rzdx7ki4"},
            {"198CB93952A5054FA543D62BB15DE344503D7C14D37CD3E771243EE16A3F09F8", "63916860",
                    "AF7F0C60C093F207011520B58C9624B2F33CA7F4880B124C1CF1DEB62D41DFC2",
                    "56B50577931BCF56B739DFF17A3C0C151C54EE2AAA71F938F00EE2CE03B9E8EA",
                    "ban_1ooo1ous88yhctummqzjhay1r7awcmq4ocmjz6wh15q4sr3umt9cwq4p1i6d"},
            {"98C1085AF4A6FE1473016A4E9B79475604E7C920575EB4008E03211812DC8A1D", "1709846729",
                    "8BDA46A1669D963301E188052642B8873DBE85A0214AA2EE6193E0759936EF03",
                    "F628887AF93F0EB1EE7A04C940EE302190CBD3398C54C3144CE4259B76FE0DD0",
                    "ban_3xjaj3xhkhrgp9q9n38ba5q51aeishbmm54nrec6ss37mfuhw5gieerw6m7c"},
            {"04EF3BE9A5948F10AB7B91863DC3167E4E0328C95F9143D34D76F8AA59F67B81", "2078320933",
                    "59E5DEB004B1E6344DB3F6EF642B0183758B1FF1585CB97605A762252F3CE3BC",
                    "AA474901F5C4FF151F04A898202EC023636D3F5EBF51E4DD519ED8B9BF39B4F3",
                    "ban_3ck9b61zdj9z4nhibc6r61qe1au5fnzoxhtjwmgo59prq8zmmf9moeytdmn7"},
    };

    private static final String VALID = KEY_VECTORS[0][4];

    @Test
    void knownVectors_privateKeyPublicKeyAddress()
    {
        for (String[] v : KEY_VECTORS)
        {
            byte[] sk = NanoKeys.privateKey(Hex.decode(v[0]), Integer.parseInt(v[1]));
            assertEquals(v[2], Hex.encode(sk));
            byte[] pk = NanoKeys.publicKey(sk);
            assertEquals(v[3], Hex.encode(pk));
            assertEquals(v[4], NanoKeys.address(pk, "ban_"));
            assertArrayEquals(pk, NanoKeys.publicKeyFromAddress(v[4]));
            assertTrue(NanoKeys.isValidAddress(v[4], "ban_"));
        }
    }

    @Test
    void nanoPrefix_sameKeyPublishedVector()
    {
        byte[] pk = Hex.decode(KEY_VECTORS[0][3]);
        String nano = "nano_3i1aq1cchnmbn9x5rsbap8b15akfh7wj7pwskuzi7ahz8oq6cobd99d4r3b7";
        assertEquals(nano, NanoKeys.address(pk, "nano_"));
        assertArrayEquals(pk, NanoKeys.publicKeyFromAddress(nano));
        assertArrayEquals(pk, NanoKeys.publicKeyFromAddress("xrb_" + nano.substring(5)));
        assertTrue(NanoKeys.isValidAddress(nano, "nano_"));
        assertFalse(NanoKeys.isValidAddress(nano, "ban_"));
        assertFalse(NanoKeys.isValidAddress(VALID, "nano_"));
    }

    @Test
    void address_roundTrip_randomKeys()
    {
        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++)
        {
            byte[] pk = new byte[32];
            rnd.nextBytes(pk);
            String a = NanoKeys.address(pk, "ban_");
            assertEquals(64, a.length());
            assertTrue(a.charAt(4) == '1' || a.charAt(4) == '3');
            assertArrayEquals(pk, NanoKeys.publicKeyFromAddress(a));
        }
    }

    @Test
    void publicKeyFromAddress_rejectsMalformed()
    {
        String body = VALID.substring(4);
        String[] bad = {
                null,
                "",
                "ban_",
                VALID.substring(0, 63),
                VALID + "1",
                "foo_" + body,
                "ban" + body,
                VALID.toUpperCase(),
                // last checksum character altered
                VALID.substring(0, 63) + (VALID.charAt(63) == '7' ? '8' : '7'),
                // key character altered (checksum no longer matches)
                VALID.substring(0, 20) + (VALID.charAt(20) == 'a' ? 'b' : 'a') + VALID.substring(21),
                // characters outside the alphabet: 0, 2, l, v
                VALID.substring(0, 10) + '0' + VALID.substring(11),
                VALID.substring(0, 10) + '2' + VALID.substring(11),
                VALID.substring(0, 10) + 'l' + VALID.substring(11),
                VALID.substring(0, 10) + 'v' + VALID.substring(11),
                VALID.substring(0, 10) + 'é' + VALID.substring(11),
                // first character encodes a non-zero padding bit
                "ban_5" + VALID.substring(5),
        };
        for (String a : bad)
        {
            assertThrows(IllegalArgumentException.class, () -> NanoKeys.publicKeyFromAddress(a), String.valueOf(a));
            assertFalse(NanoKeys.isValidAddress(a, "ban_"), String.valueOf(a));
        }
        assertFalse(NanoKeys.isValidAddress(VALID, null));
        assertFalse(NanoKeys.isValidAddress(VALID, ""));
    }

    @Test
    void signAndVerify_roundTripAndTamperDetection()
    {
        byte[] sk = Hex.decode(KEY_VECTORS[1][2]);
        byte[] pk = Hex.decode(KEY_VECTORS[1][3]);
        byte[] msg = Hex.decode("86B0ACB9D132C059B652C01B4176DCFA9A81987004D8C5D65586ABC27A5F462B");
        byte[] sig = NanoKeys.sign(sk, msg);
        assertEquals(64, sig.length);
        assertArrayEquals(sig, NanoKeys.sign(sk, msg), "deterministic");
        assertTrue(NanoKeys.verify(pk, msg, sig));
        for (int i = 0; i < 64; i += 7)
        {
            byte[] bad = sig.clone();
            bad[i] ^= 1;
            assertFalse(NanoKeys.verify(pk, msg, bad));
        }
        byte[] otherMsg = msg.clone();
        otherMsg[0] ^= 1;
        assertFalse(NanoKeys.verify(pk, otherMsg, sig));
        assertFalse(NanoKeys.verify(Hex.decode(KEY_VECTORS[2][3]), msg, sig));
    }

    @Test
    void invalidArguments_rejected()
    {
        assertThrows(IllegalArgumentException.class, () -> NanoKeys.privateKey(new byte[31], 0));
        assertThrows(IllegalArgumentException.class, () -> NanoKeys.privateKey(null, 0));
        assertThrows(IllegalArgumentException.class, () -> NanoKeys.publicKey(new byte[33]));
        assertThrows(IllegalArgumentException.class, () -> NanoKeys.address(new byte[31], "ban_"));
        assertThrows(IllegalArgumentException.class, () -> NanoKeys.address(new byte[32], ""));
        assertThrows(IllegalArgumentException.class, () -> NanoKeys.sign(new byte[32], null));
        assertFalse(NanoKeys.verify(new byte[32], new byte[0], null));
    }
}
