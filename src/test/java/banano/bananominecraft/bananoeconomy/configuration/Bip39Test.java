package banano.bananominecraft.bananoeconomy.configuration;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class Bip39Test
{
    private final Bip39 bip39 = new Bip39();

    // -------------------------------------------------------------------------
    // Word list sanity
    // -------------------------------------------------------------------------

    @Test
    void WordListLoads2048Words()
    {
        // If the list has the wrong count, Bip39() throws in the constructor.
        // Reaching this line means it loaded correctly.
        assertNotNull(bip39);
    }

    // -------------------------------------------------------------------------
    // toMnemonic
    // -------------------------------------------------------------------------

    @Test
    void ToMnemonicProduces24Words()
    {
        byte[] entropy = new byte[32];
        String mnemonic = bip39.toMnemonic(entropy);
        assertEquals(24, mnemonic.split(" ").length);
    }

    @Test
    void ToMnemonicRejectsWrongEntropyLength()
    {
        assertThrows(IllegalArgumentException.class, () -> bip39.toMnemonic(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> bip39.toMnemonic(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> bip39.toMnemonic(new byte[33]));
    }

    @Test
    void ToMnemonicAllZeroEntropyProducesKnownFirstWord()
    {
        // BIP39 test vector: all-zero 256-bit entropy → first word is "abandon"
        byte[] entropy = new byte[32];
        String mnemonic = bip39.toMnemonic(entropy);
        assertEquals("abandon", mnemonic.split(" ")[0]);
    }

    // -------------------------------------------------------------------------
    // Round-trip
    // -------------------------------------------------------------------------

    @Test
    void RoundTripAllZeroEntropy()
    {
        byte[] entropy  = new byte[32];
        String mnemonic = bip39.toMnemonic(entropy);
        byte[] restored = bip39.toEntropy(mnemonic);
        assertArrayEquals(entropy, restored);
    }

    @Test
    void RoundTripRandomEntropy100Times()
    {
        SecureRandom rng = new SecureRandom();
        for (int i = 0; i < 100; i++)
        {
            byte[] entropy = new byte[32];
            rng.nextBytes(entropy);
            byte[] restored = bip39.toEntropy(bip39.toMnemonic(entropy));
            assertArrayEquals(entropy, restored, "round-trip failed at iteration " + i);
        }
    }

    @Test
    void RoundTripKnownHexSeed()
    {
        // A well-known BIP39 test seed — deterministic
        String hex      = "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233";
        byte[] entropy  = HexFormat.of().parseHex(hex);
        String mnemonic = bip39.toMnemonic(entropy);
        byte[] restored = bip39.toEntropy(mnemonic);
        assertEquals(hex, HexFormat.of().withUpperCase().formatHex(restored));
    }

    // -------------------------------------------------------------------------
    // toEntropy — error cases
    // -------------------------------------------------------------------------

    @Test
    void ToEntropyRejectsNull()
    {
        assertThrows(IllegalArgumentException.class, () -> bip39.toEntropy(null));
    }

    @Test
    void ToEntropyRejectsWrongWordCount()
    {
        assertThrows(IllegalArgumentException.class, () -> bip39.toEntropy("abandon"));
        assertThrows(IllegalArgumentException.class,
                () -> bip39.toEntropy("abandon ".repeat(23).trim()));
    }

    @Test
    void ToEntropyRejectsUnknownWord()
    {
        // Build a valid mnemonic, replace one word with garbage
        byte[] entropy  = new byte[32];
        String mnemonic = bip39.toMnemonic(entropy);
        String broken   = mnemonic.replaceFirst("abandon", "notaword");
        assertThrows(IllegalArgumentException.class, () -> bip39.toEntropy(broken));
    }

    @Test
    void ToEntropyRejectsBadChecksum()
    {
        // Take a valid mnemonic, swap the last word with another valid word to break checksum
        byte[] entropy  = new byte[32];
        String[] words  = bip39.toMnemonic(entropy).split(" ");
        // Replace last word with one that produces a checksum mismatch
        words[23]       = words[23].equals("art") ? "able" : "art";
        String broken   = String.join(" ", words);
        assertThrows(IllegalArgumentException.class, () -> bip39.toEntropy(broken));
    }
}
