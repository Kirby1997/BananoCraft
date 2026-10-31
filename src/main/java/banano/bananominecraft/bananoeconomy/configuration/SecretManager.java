package banano.bananominecraft.bananoeconomy.configuration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
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

    /**
     * Encrypts {@code plaintext} into an {@code ENC:} value.
     *
     * @return the ciphertext, or {@code null} if no key is loaded or encryption
     *         failed. Never returns the plaintext, so callers cannot accidentally
     *         persist an unencrypted secret.
     */
    public String encrypt(String plaintext)
    {
        if (key == null || plaintext == null)
        {
            return null;
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
            return null;
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

                // Never overwrite a key we cannot use: it may be the only way to
                // recover encrypted seeds (e.g. after a botched copy). Move it aside.
                File bad = new File(keyFile.getParentFile(),
                        keyFile.getName() + ".bad-" + System.currentTimeMillis());
                boolean moved = keyFile.renameTo(bad);
                logger.severe("secret.key has an unexpected length (" + keyBytes.length + " bytes, expected "
                        + (KEY_BITS / 8) + "). "
                        + (moved ? "It was renamed to " + bad.getName() + "." : "It could not be renamed.")
                        + " Config values will NOT be encrypted or decrypted this run. Restore the correct"
                        + " secret.key, or re-enter the seeds in plaintext and restart to generate a new key.");

                return null;
            }

            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(KEY_BITS, new SecureRandom());
            SecretKey newKey = kg.generateKey();

            File parent = keyFile.getParentFile();
            if (parent != null)
            {
                parent.mkdirs();
            }

            Path keyPath = keyFile.toPath();
            createOwnerOnlyFile(keyPath);

            try
            {
                Files.write(keyPath, newKey.getEncoded());
            }
            catch (IOException e)
            {
                // Don't leave a truncated key behind for the next startup to trip over.
                Files.deleteIfExists(keyPath);
                throw e;
            }

            logger.info("Generated new secret.key for config encryption.");

            return newKey;
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "Failed to load or generate secret.key — seeds will not be encrypted.", e);

            return null;
        }
    }

    /**
     * Create a new, empty file readable and writable only by its owner. On POSIX
     * file systems the permissions are applied atomically at creation, so the file
     * is never briefly world-readable; elsewhere (e.g. Windows) a best-effort
     * owner-only fallback is applied straight after creation.
     *
     * @throws java.nio.file.FileAlreadyExistsException if the file already exists
     */
    static void createOwnerOnlyFile(Path path) throws IOException
    {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
        {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
            return;
        }

        Files.createFile(path);

        File file = path.toFile();
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        file.setExecutable(false, false);
    }
}
