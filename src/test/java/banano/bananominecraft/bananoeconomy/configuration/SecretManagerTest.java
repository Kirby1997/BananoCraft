package banano.bananominecraft.bananoeconomy.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SecretManagerTest
{
    private static final Logger LOG      = Logger.getLogger("test");
    private static final String SEED     = "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233";
    private static final String PH_SEED  = "INSERT WALLET SEED HERE";
    private static final String PH_BANK  = "INSERT BANK WALLET SEED HERE";

    @TempDir File tempDir;

    private SecretManager manager()
    {
        return new SecretManager(new File(tempDir, "secret.key"), LOG);
    }

    // -------------------------------------------------------------------------
    // isEncrypted
    // -------------------------------------------------------------------------

    @Test
    void IsEncryptedTrueWhenPrefixed()
    {
        assertTrue(manager().isEncrypted(SecretManager.ENC_PREFIX + "somebase64"));
    }

    @Test
    void IsEncryptedFalseForPlaintext()
    {
        assertFalse(manager().isEncrypted(SEED));
    }

    @Test
    void IsEncryptedFalseForNull()
    {
        assertFalse(manager().isEncrypted(null));
    }

    // -------------------------------------------------------------------------
    // needsEncryption
    // -------------------------------------------------------------------------

    @Test
    void NeedsEncryptionTrueForPlaintext()
    {
        assertTrue(manager().needsEncryption(SEED, PH_SEED));
    }

    @Test
    void NeedsEncryptionFalseForEmpty()
    {
        assertFalse(manager().needsEncryption("", PH_SEED));
    }

    @Test
    void NeedsEncryptionFalseForNull()
    {
        assertFalse(manager().needsEncryption(null, PH_SEED));
    }

    @Test
    void NeedsEncryptionFalseForPlaceholder()
    {
        assertFalse(manager().needsEncryption(PH_SEED, PH_SEED));
    }

    @Test
    void NeedsEncryptionFalseForDifferentPlaceholder()
    {
        assertFalse(manager().needsEncryption(PH_BANK, PH_BANK));
    }

    @Test
    void NeedsEncryptionFalseWhenAlreadyEncrypted()
    {
        SecretManager sm = manager();
        String encrypted = sm.encrypt(SEED);
        assertFalse(sm.needsEncryption(encrypted, PH_SEED));
    }

    // -------------------------------------------------------------------------
    // encrypt / decrypt round-trip
    // -------------------------------------------------------------------------

    @Test
    void EncryptDecryptRoundTrip()
    {
        SecretManager sm = manager();
        String encrypted = sm.encrypt(SEED);

        assertTrue(sm.isEncrypted(encrypted), "Encrypted value must start with ENC:");
        assertNotEquals(SEED, encrypted, "Ciphertext must differ from plaintext");
        assertEquals(SEED, sm.decrypt(encrypted));
    }

    @Test
    void EncryptProducesDifferentCiphertextEachTime()
    {
        SecretManager sm = manager();
        String first  = sm.encrypt(SEED);
        String second = sm.encrypt(SEED);
        // Different IVs mean different ciphertext even for the same plaintext.
        assertNotEquals(first, second);
    }

    @Test
    void SameKeyFileSharedAcrossInstances()
    {
        // Instance A encrypts; instance B (loading the same key) decrypts.
        File keyFile = new File(tempDir, "secret.key");
        SecretManager a = new SecretManager(keyFile, LOG);
        String encrypted = a.encrypt(SEED);

        SecretManager b = new SecretManager(keyFile, LOG);
        assertEquals(SEED, b.decrypt(encrypted));
    }

    @Test
    void DifferentKeyFileCannotDecrypt()
    {
        SecretManager a = new SecretManager(new File(tempDir, "key_a.key"), LOG);
        SecretManager b = new SecretManager(new File(tempDir, "key_b.key"), LOG);

        String encrypted = a.encrypt(SEED);
        // b has a different key — decryption should fail and return null.
        assertNull(b.decrypt(encrypted));
    }

    // -------------------------------------------------------------------------
    // isReady
    // -------------------------------------------------------------------------

    @Test
    void IsReadyTrueAfterSuccessfulKeyGeneration()
    {
        assertTrue(manager().isReady());
    }

    @Test
    void DecryptReturnsNullWhenKeyIsNullAndValueIsEncrypted()
    {
        // Trigger key == null by passing a directory path as the key file —
        // the write attempt fails so loadOrGenerateKey returns null.
        File dirAsKeyFile = new File(tempDir, "not-a-file");
        dirAsKeyFile.mkdirs();
        SecretManager noKey = new SecretManager(dirAsKeyFile, LOG);

        assertFalse(noKey.isReady(), "SecretManager must report not ready when key failed to load");

        // Previously, decrypt() returned the raw ENC: string here (not null), which
        // caused ConfigEngine to treat it as valid plaintext and "enable" banking with
        // a garbage seed.  It must return null so the caller triggers a SEVERE log.
        String encrypted = manager().encrypt(SEED);
        assertNull(noKey.decrypt(encrypted));
    }
}
