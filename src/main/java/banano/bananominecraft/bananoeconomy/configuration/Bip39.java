package banano.bananominecraft.bananoeconomy.configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts 32-byte entropy to/from a 24-word BIP39 mnemonic phrase.
 *
 * <p>Algorithm (256-bit entropy):
 * <ol>
 *   <li>Compute SHA-256 of the entropy; take the first 8 bits as a checksum.</li>
 *   <li>Concatenate 256 entropy bits + 8 checksum bits = 264 bits.</li>
 *   <li>Split into 24 groups of 11 bits; map each to a word from the 2048-word list.</li>
 * </ol>
 *
 * <p>The word list is loaded from the bundled resource {@code /bip39_english.txt}.</p>
 */
final class Bip39
{
    private static final int ENTROPY_BYTES = 32;
    private static final int WORD_COUNT    = 24;

    private final List<String> wordList;

    Bip39()
    {
        wordList = loadWordList();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Converts 32 bytes of raw entropy to a space-separated 24-word mnemonic.
     */
    String toMnemonic(byte[] entropy)
    {
        if (entropy.length != ENTROPY_BYTES)
        {
            throw new IllegalArgumentException("Entropy must be " + ENTROPY_BYTES + " bytes");
        }

        byte[] hash = sha256(entropy);

        // 33-byte working buffer: 32 entropy + 1 checksum byte (only 8 bits used)
        byte[] buf = new byte[ENTROPY_BYTES + 1];
        System.arraycopy(entropy, 0, buf, 0, ENTROPY_BYTES);
        buf[ENTROPY_BYTES] = hash[0];

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WORD_COUNT; i++)
        {
            if (i > 0)
            {
                sb.append(' ');
            }

            sb.append(wordList.get(extract11Bits(buf, i * 11)));
        }

        return sb.toString();
    }

    /**
     * Converts a space-separated 24-word mnemonic back to 32 bytes of entropy.
     * Validates the BIP39 checksum; throws {@link IllegalArgumentException} on mismatch.
     */
    byte[] toEntropy(String mnemonic)
    {
        if (mnemonic == null)
        {
            throw new IllegalArgumentException("Mnemonic must not be null");
        }

        String[] parts = mnemonic.trim().split("\\s+");
        if (parts.length != WORD_COUNT)
        {
            throw new IllegalArgumentException("Mnemonic must be " + WORD_COUNT + " words");
        }

        byte[] buf = new byte[ENTROPY_BYTES + 1];
        for (int i = 0; i < WORD_COUNT; i++)
        {
            int index = wordList.indexOf(parts[i]);

            if (index < 0)
            {
                throw new IllegalArgumentException("Unknown BIP39 word: '" + parts[i] + "'");
            }

            store11Bits(buf, i * 11, index);
        }

        byte[] entropy = new byte[ENTROPY_BYTES];
        System.arraycopy(buf, 0, entropy, 0, ENTROPY_BYTES);

        byte expected = buf[ENTROPY_BYTES];
        byte actual   = sha256(entropy)[0];
        if (expected != actual)
        {
            throw new IllegalArgumentException("Invalid mnemonic: checksum mismatch");
        }

        return entropy;
    }

    // -------------------------------------------------------------------------
    // Bit manipulation helpers
    // -------------------------------------------------------------------------

    private static int extract11Bits(byte[] data, int bitOffset)
    {
        int value = 0;

        for (int i = 0; i < 11; i++)
        {
            int byteIdx = (bitOffset + i) / 8;
            int bitIdx  = 7 - ((bitOffset + i) % 8);

            if ((data[byteIdx] & (1 << bitIdx)) != 0)
            {
                value |= (1 << (10 - i));
            }
        }

        return value;
    }

    private static void store11Bits(byte[] data, int bitOffset, int value)
    {
        for (int i = 0; i < 11; i++)
        {
            int byteIdx = (bitOffset + i) / 8;
            int bitIdx  = 7 - ((bitOffset + i) % 8);

            if ((value & (1 << (10 - i))) != 0)
            {
                data[byteIdx] |=  (1 << bitIdx);
            }
            else
            {
                data[byteIdx] &= ~(1 << bitIdx);
            }
        }
    }

    private static byte[] sha256(byte[] data)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(data);
        }
        catch (Exception e)
        {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    // -------------------------------------------------------------------------
    // Word list loading
    // -------------------------------------------------------------------------

    private static List<String> loadWordList()
    {
        List<String> list = new ArrayList<>(2048);

        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(
                        Bip39.class.getResourceAsStream("/bip39_english.txt"),
                        "bip39_english.txt not found on classpath"))))
        {
            String line;

            while ((line = r.readLine()) != null)
            {
                line = line.trim();

                if (!line.isEmpty())
                {
                    list.add(line);
                }
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to load BIP39 word list", e);
        }

        if (list.size() != 2048)
        {
            throw new IllegalStateException(
                    "BIP39 word list has " + list.size() + " entries, expected 2048");
        }

        return list;
    }
}
