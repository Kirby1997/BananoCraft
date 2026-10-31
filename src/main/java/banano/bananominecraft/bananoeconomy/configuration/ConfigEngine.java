package banano.bananominecraft.bananoeconomy.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Single source of truth for all runtime-mutable plugin configuration values.
 *
 * <p>All classes that previously read {@code plugin.getConfig()} directly should
 * read from this class instead, so that in-memory state and persisted state are
 * always consistent.</p>
 *
 * <p>Wallet seeds are encrypted with AES-256-GCM on first load: if a plaintext
 * seed is found in {@code config.yml} it is replaced in-place with an
 * {@code ENC:…} ciphertext.  The key lives in {@code secret.key} in the plugin
 * data folder.  See {@link SecretManager} for details.</p>
 *
 * <p>Call {@link #save()} after mutating any field to persist the change to disk
 * and keep the Bukkit config in sync.</p>
 */
public class ConfigEngine
{
    public static final int MIN_HISTORY_TRANSACTIONS     = 1;
    public static final int MAX_HISTORY_TRANSACTIONS     = 100;
    public static final int DEFAULT_HISTORY_TRANSACTIONS = 10;

    private static final String SEED_PLACEHOLDER      = "INSERT WALLET SEED HERE";
    private static final String BANK_SEED_PLACEHOLDER = "INSERT BANK WALLET SEED HERE";
    private static final String SEED_BACKUP_FILENAME  = "seed-backup.txt";

    private final Plugin        plugin;
    private final SecretManager secretManager;

    // --- node / RPC ---
    private String nodeAddress  = "";
    private String multiplier   = "100000000000000000000000000000";
    private String walletId     = "";
    private String walletSeed   = "";
    private String masterWallet = "";

    // --- bank wallet ---
    private String bankWalletSeed = "";
    private String bankWalletId   = "";

    // --- block explorer ---
    private String explorerAccount = "https://creeper.banano.cc/explorer/account/";
    private String explorerBlock   = "https://creeper.banano.cc/explorer/block/";

    // --- feature flags ---
    private boolean enableOfflinePayment        = false;
    private int     maximumTransactionHistoryCount = 10;

    // --- websocket ---
    private String websocketUrl = "wss://ws.banano.trade";

    // --- representatives ---
    /** Admin-curated representative addresses; empty means "let callers fall back to the node's
     *  own representatives_online list" rather than always trusting whatever the node reports. */
    private List<String> representatives = Collections.emptyList();

    public ConfigEngine(Plugin plugin)
    {
        this.plugin        = plugin;
        this.secretManager = new SecretManager(
                new File(plugin.getDataFolder(), "secret.key"),
                plugin.getLogger());
        initialiseConfig(plugin.getConfig());
    }

    public void reload()
    {
        this.plugin.reloadConfig();
        initialiseConfig(this.plugin.getConfig());
    }

    private void initialiseConfig(FileConfiguration configuration)
    {
        if (configuration == null)
        {
            return;
        }

        this.nodeAddress = configuration.getString("IP", "");
        plugin.getLogger().info("Node address: " + this.nodeAddress);

        this.multiplier = configuration.getString("multiplier", "100000000000000000000000000000");
        this.walletId   = configuration.getString("walletID", "");

        // --- player wallet seed ---
        boolean seedJustGenerated = false;
        String rawSeed = configuration.getString("walletSeed", "");
        if (rawSeed.isEmpty() || rawSeed.equals(SEED_PLACEHOLDER))
        {
            // No seed provided — generate one now and write a backup file the admin must secure.
            rawSeed = generateSeed();
            configuration.set("walletSeed", secretManager.encrypt(rawSeed));
            plugin.saveConfig();
            this.walletSeed = rawSeed;
            writeSeedBackupFile(rawSeed);
            seedJustGenerated = true;
        }
        else if (secretManager.needsEncryption(rawSeed, SEED_PLACEHOLDER))
        {
            plugin.getLogger().info("Encrypting walletSeed in config.yml...");
            configuration.set("walletSeed", secretManager.encrypt(rawSeed));
            plugin.saveConfig();
            this.walletSeed = rawSeed;
        }
        else if (secretManager.isEncrypted(rawSeed))
        {
            String decrypted = secretManager.decrypt(rawSeed);
            if (decrypted == null)
            {
                plugin.getLogger().severe(
                        "walletSeed decryption failed — secret.key may be missing or corrupt. " +
                        "Economy will not function until the key is restored.");
            }
            this.walletSeed = decrypted != null ? decrypted : "";
        }
        else
        {
            this.walletSeed = rawSeed;
        }

        // --- bank wallet seed (optional; encrypts on first load if present and plaintext) ---
        String rawBankSeed = configuration.getString("bankWalletSeed", "");
        if (secretManager.needsEncryption(rawBankSeed, BANK_SEED_PLACEHOLDER))
        {
            plugin.getLogger().info("Encrypting bankWalletSeed in config.yml...");
            configuration.set("bankWalletSeed", secretManager.encrypt(rawBankSeed));
            plugin.saveConfig();
            this.bankWalletSeed = rawBankSeed;
        }
        else if (secretManager.isEncrypted(rawBankSeed))
        {
            String decrypted = secretManager.decrypt(rawBankSeed);
            if (decrypted == null)
            {
                plugin.getLogger().severe(
                        "bankWalletSeed decryption failed — secret.key may be missing or corrupt. " +
                        "Bank accounts will be disabled until the key is restored.");
            }
            this.bankWalletSeed = decrypted != null ? decrypted : "";
        }
        else
        {
            this.bankWalletSeed = rawBankSeed;
        }

        this.bankWalletId = configuration.getString("bankWalletID", "");
        if (!this.bankWalletId.isEmpty())
        {
            plugin.getLogger().info("Bank wallet ID loaded.");
        }

        this.explorerAccount = configuration.getString("exploreaccount",
                "https://creeper.banano.cc/explorer/account/");
        plugin.getLogger().info("Account explorer URL: " + this.explorerAccount);

        this.explorerBlock = configuration.getString("exploreblock",
                "https://creeper.banano.cc/explorer/block/");
        plugin.getLogger().info("Block explorer URL: " + this.explorerBlock);

        if (configuration.contains("masterWallet"))
        {
            this.masterWallet = configuration.getString("masterWallet", "");
            plugin.getLogger().info("Master wallet address loaded.");
        }
        else
        {
            plugin.getLogger().warning("Master wallet address not specified in config.");
        }

        this.enableOfflinePayment = configuration.getBoolean("allowofflinepayment", false);
        plugin.getLogger().info("Offline payments: " + (this.enableOfflinePayment ? "ENABLED" : "DISABLED"));

        this.maximumTransactionHistoryCount = configuration.getInt("maximumtransactionhistorycount", 10);
        plugin.getLogger().info("Maximum Transaction History Count: " + this.maximumTransactionHistoryCount);

        this.websocketUrl = configuration.getString("websocketUrl", "wss://ws.banano.trade");
        plugin.getLogger().info("WebSocket URL: " + this.websocketUrl);

        this.representatives = configuration.getStringList("representatives");
        if (!this.representatives.isEmpty())
        {
            plugin.getLogger().info("Loaded " + this.representatives.size() + " curated representative(s).");
        }

        // Nag the admin until they delete the one-time seed backup file.
        // Skip on the startup that just wrote the file — the generation SEVERE is sufficient.
        if (!seedJustGenerated && new File(plugin.getDataFolder(), SEED_BACKUP_FILENAME).exists())
        {
            plugin.getLogger().severe(
                    "'" + SEED_BACKUP_FILENAME + "' still exists in the plugin folder. " +
                    "Back up the seed phrase(s) inside it, then DELETE the file.");
        }
    }

    // -------------------------------------------------------------------------
    // Seed generation and backup
    // -------------------------------------------------------------------------

    private static String generateSeed()
    {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private void writeSeedBackupFile(String walletSeedHex)
    {
        File backupFile = new File(plugin.getDataFolder(), SEED_BACKUP_FILENAME);
        try
        {
            plugin.getDataFolder().mkdirs();
            String mnemonic = new Bip39().toMnemonic(HexFormat.of().parseHex(walletSeedHex));

            try (PrintWriter w = new PrintWriter(backupFile, "UTF-8"))
            {
                w.println("=================================================================");
                w.println(" BananoCraft — Wallet Seed Backup");
                w.println(" Created: " + Instant.now());
                w.println("=================================================================");
                w.println();
                w.println(" *** ACTION REQUIRED ***");
                w.println(" 1. Write down the mnemonic phrase below on paper, or store it");
                w.println("    in a password manager that you control.");
                w.println(" 2. Keep the backup OFFLINE and separate from this server.");
                w.println(" 3. DELETE this file once you have saved the phrase.");
                w.println();
                w.println(" This file will trigger a SEVERE warning on every server startup");
                w.println(" until it is deleted.");
                w.println();
                w.println("-----------------------------------------------------------------");
                w.println(" PLAYER WALLET (walletSeed)");
                w.println("-----------------------------------------------------------------");
                w.println();
                w.println(" 24-word mnemonic (write these down):");
                w.println();

                // Print 6 words per line for readability
                String[] words = mnemonic.split(" ");
                for (int i = 0; i < words.length; i += 6)
                {
                    StringBuilder line = new StringBuilder("  ");
                    for (int j = i; j < Math.min(i + 6, words.length); j++)
                    {
                        if (j > i) line.append(' ');
                        line.append(String.format("%2d. %-10s", j + 1, words[j]));
                    }
                    w.println(line);
                }

                w.println();
                w.println(" Hex seed (same value — paste into config.yml to restore):");
                w.println("  " + walletSeedHex);
                w.println();
                w.println("-----------------------------------------------------------------");
                w.println(" HOW TO RECOVER IF secret.key IS LOST");
                w.println("-----------------------------------------------------------------");
                w.println();
                w.println(" 1. Delete the corrupted / missing secret.key from this folder.");
                w.println(" 2. Paste the hex seed above into config.yml as:");
                w.println("      walletSeed: " + walletSeedHex);
                w.println(" 3. Start the server — it will re-encrypt the seed and");
                w.println("    generate a new secret.key automatically.");
                w.println();
                w.println(" HOW TO MIGRATE TO A NEW NODE:");
                w.println(" 1. On the new node run:  wallet_create  with the hex seed.");
                w.println(" 2. Update walletID in config.yml with the returned wallet ID.");
                w.println(" NOTE: The master wallet address (ban_xxx) is derived deterministically");
                w.println("       from the seed, so it stays the same — no config change needed.");
                w.println();
                w.println("=================================================================");
            }

            plugin.getLogger().severe(
                    "Seed backup written to: " + backupFile.getAbsolutePath());
        }
        catch (Exception e)
        {
            plugin.getLogger().severe(
                    "FAILED to write seed backup file — copy this seed manually: " + walletSeedHex);
        }
    }

    /**
     * Persist all runtime-mutable fields to disk.
     *
     * <p>Seed fields are intentionally never overwritten here — they are only
     * written (in encrypted form) by {@link #initialiseConfig} on first load.</p>
     *
     * @return {@code true} on success
     */
    public boolean save()
    {
        FileConfiguration config = this.plugin.getConfig();

        config.set("IP",                  this.nodeAddress);
        config.set("walletID",            this.walletId);
        config.set("masterWallet",        this.masterWallet);
        config.set("exploreaccount",      this.explorerAccount);
        config.set("exploreblock",        this.explorerBlock);
        config.set("allowofflinepayment", this.enableOfflinePayment);
        config.set("maximumtransactionhistorycount", this.maximumTransactionHistoryCount);
        config.set("websocketUrl",        this.websocketUrl);

        if (!this.bankWalletId.isEmpty())
        {
            config.set("bankWalletID", this.bankWalletId);
        }

        this.plugin.saveConfig();
        this.plugin.reloadConfig();

        return true;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getNodeAddress()                { return this.nodeAddress; }
    public void   setNodeAddress(String v)        { this.nodeAddress = v; }

    /** Raw Banano multiplier string (e.g. {@code "100000000000000000000000000000"}). */
    public String getMultiplier()                 { return this.multiplier; }

    public String getWalletId()                   { return this.walletId; }
    public void   setWalletId(String v)           { this.walletId = v; }

    public String getWalletSeed()                 { return this.walletSeed; }

    public String getMasterWallet()               { return this.masterWallet; }
    public void   setMasterWallet(String v)       { this.masterWallet = v; }

    // --- bank wallet ---

    public String  getBankWalletSeed()            { return this.bankWalletSeed; }

    public String  getBankWalletId()              { return this.bankWalletId; }
    public void    setBankWalletId(String v)      { this.bankWalletId = v; }

    /** Returns {@code true} when a non-placeholder bank wallet seed has been configured. */
    public boolean isBankSeedConfigured()
    {
        return this.bankWalletSeed != null
                && !this.bankWalletSeed.isEmpty()
                && !this.bankWalletSeed.equalsIgnoreCase(BANK_SEED_PLACEHOLDER);
    }

    // --- block explorer ---

    public String getExplorerAccount()            { return this.explorerAccount; }
    public void   setExplorerAccount(String v)    { this.explorerAccount = v; }

    public String getExplorerBlock()              { return this.explorerBlock; }
    public void   setExplorerBlock(String v)      { this.explorerBlock = v; }

    // --- feature flags ---

    public boolean getEnableOfflinePayment()      { return this.enableOfflinePayment; }
    public void    setEnableOfflinePayment(boolean v) { this.enableOfflinePayment = v; }

    public int     getMaximumTransactionHistoryCount() { return this.maximumTransactionHistoryCount; }
    public void    setMaximumTransactionHistoryCount(int count)
    {
        if (count < MIN_HISTORY_TRANSACTIONS
             || count > MAX_HISTORY_TRANSACTIONS)
        {
            count = DEFAULT_HISTORY_TRANSACTIONS;
        }
        this.maximumTransactionHistoryCount = count;
    }

    // --- websocket ---

    public String getWebsocketUrl()               { return this.websocketUrl; }
    public void   setWebsocketUrl(String v)       { this.websocketUrl = v; }

    // --- representatives ---

    /** Admin-curated representative addresses from config.yml; empty if none configured. */
    public List<String> getRepresentatives()      { return this.representatives; }
}
