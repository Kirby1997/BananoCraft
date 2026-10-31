package banano.bananominecraft.bananoeconomy.configuration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AES-256-GCM encryption for sensitive config values (wallet seeds).
 *
 * <p>On first run a random 256-bit key is written to {@code secret.key} inside the
 * plugin data folder with owner-read-only permissions.  Subsequent runs load that
 * key and use it to encrypt / decrypt values tagged with the {@code ENC:} prefix.</p>
 *
 * <p>Protection model: an attacker who obtains only {@code config.yml} cannot read
 * the seed; they need both {@code config.yml} and {@code secret.key}.  The
 * decrypted seed exists in JVM memory while the plugin is running — that is
 * unavoidable for any in-process approach.</p>
 */
public class SecretManager
{
    public static final String ENC_PREFIX   = "ENC:";

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int    KEY_BITS    = 256;
    private static final int    GCM_IV_LEN  = 12;
    private static final int    GCM_TAG_LEN = 128;

    private final Logger    logger;
    private final SecretKey key;

    public SecretManager(File keyFile, Logger logger)
    {
        this.logger = logger;
        this.key    = loadOrGenerateKey(keyFile);
    }

    public boolean isReady()
    {
        return key != null;
    }

    public boolean isEncrypted(String value)
    {
        return value != null && value.startsWith(ENC_PREFIX);
    }

    /** Returns {@code true} when a value is present, not a placeholder, and not yet encrypted. */
    public boolean needsEncryption(String value, String placeholder)
    {
        return value != null
                && !value.isEmpty()
                && !value.equalsIgnoreCase(placeholder)
                && !isEncrypted(value);
    }

    public String encrypt(String plaintext)
    {
        if (key == null)
        {
            return plaintext;
        }
        try
        {
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,         iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "Failed to encrypt secret.", e);
            return plaintext;
        }
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt}.
     *
     * @return the plaintext, or {@code null} if decryption fails (e.g. wrong key).
     */
    public String decrypt(String encrypted)
    {
        if (key == null)
        {
            return null;          // no key loaded — caller must treat as failure
        }

        if (!isEncrypted(encrypted))
        {
            return encrypted;  // not encrypted — return as-is
        }

        try
        {
            byte[] combined   = Base64.getDecoder().decode(encrypted.substring(ENC_PREFIX.length()));
            byte[] iv         = new byte[GCM_IV_LEN];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LEN];
            System.arraycopy(combined, 0,          iv,         0, GCM_IV_LEN);
            System.arraycopy(combined, GCM_IV_LEN, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE,
                    "Failed to decrypt secret — if secret.key was changed or deleted, "
                    + "re-enter the seed in plaintext in config.yml and restart.", e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Key management
    // -------------------------------------------------------------------------

    private SecretKey loadOrGenerateKey(File keyFile)
    {
        try
        {
            if (keyFile.exists())
            {
                byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
                if (keyBytes.length == KEY_BITS / 8)
                {
                    logger.info("Loaded encryption key from secret.key.");
                    return new SecretKeySpec(keyBytes, "AES");
                }

                logger.warning("secret.key has unexpected length — regenerating. "
                        + "Previously encrypted config values must be re-entered in plaintext.");
            }

            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(KEY_BITS, new SecureRandom());
            SecretKey newKey = kg.generateKey();

            File parent = keyFile.getParentFile();
            if (parent != null)
            {
                parent.mkdirs();
            }

            Files.write(keyFile.toPath(), newKey.getEncoded());

            // Best-effort owner-only permissions (no-op on Windows).
            keyFile.setReadable(false, false);
            keyFile.setReadable(true, true);
            keyFile.setWritable(false, false);
            keyFile.setWritable(true, true);
            keyFile.setExecutable(false);

            logger.info("Generated new secret.key for config encryption.");

            return newKey;
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "Failed to load or generate secret.key — seeds will not be encrypted.", e);

            return null;
        }
    }
}
