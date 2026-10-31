package banano.bananominecraft.bananoeconomy.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigEngineTest
{
    @TempDir File tempDir;

    private Plugin plugin;
    private FileConfiguration config;

    @BeforeEach
    void setUp()
    {
        plugin = mock(Plugin.class);
        config = mock(FileConfiguration.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(tempDir);

        // Default behaviour: every getString/getBoolean/getInt returns the supplied default.
        when(config.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config.contains("masterWallet")).thenReturn(true);
        when(config.getString("masterWallet", "")).thenReturn("");
    }

    @Test
    void loadsDefaults_whenConfigEmpty()
    {
        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("", engine.getNodeAddress());
        assertEquals("100000000000000000000000000000", engine.getMultiplier());
        assertEquals("https://creeper.banano.cc/explorer/account/", engine.getExplorerAccount());
        assertEquals("https://creeper.banano.cc/explorer/block/", engine.getExplorerBlock());
        assertFalse(engine.getEnableOfflinePayment());
        assertEquals(10, engine.getMaximumTransactionHistoryCount());
        assertEquals("wss://ws.banano.trade", engine.getWebsocketUrl());
        assertTrue(engine.getRepresentatives().isEmpty());
    }

    @Test
    void readsConfiguredValues()
    {
        when(config.getString("IP", "")).thenReturn("http://node:7072");
        when(config.getString("walletID", "")).thenReturn("WID");
        when(config.getBoolean("allowofflinepayment", false)).thenReturn(true);
        when(config.getInt("maximumtransactionhistorycount", 10)).thenReturn(25);
        when(config.getString("websocketUrl", "wss://ws.banano.trade")).thenReturn("wss://custom");
        when(config.getStringList("representatives")).thenReturn(List.of("ban_rep1", "ban_rep2"));

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("http://node:7072", engine.getNodeAddress());
        assertEquals("WID", engine.getWalletId());
        assertTrue(engine.getEnableOfflinePayment());
        assertEquals(25, engine.getMaximumTransactionHistoryCount());
        assertEquals("wss://custom", engine.getWebsocketUrl());
        assertEquals(List.of("ban_rep1", "ban_rep2"), engine.getRepresentatives());
    }

    @Test
    void warnsWhenMasterWalletMissing()
    {
        when(config.contains("masterWallet")).thenReturn(false);

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("", engine.getMasterWallet());
    }

    @Test
    void setMaximumTransactionHistoryCount_acceptsInRange()
    {
        ConfigEngine engine = new ConfigEngine(plugin);

        engine.setMaximumTransactionHistoryCount(50);
        assertEquals(50, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(ConfigEngine.MIN_HISTORY_TRANSACTIONS);
        assertEquals(ConfigEngine.MIN_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(ConfigEngine.MAX_HISTORY_TRANSACTIONS);
        assertEquals(ConfigEngine.MAX_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());
    }

    @Test
    void setMaximumTransactionHistoryCount_resetsToDefault_whenOutOfRange()
    {
        ConfigEngine engine = new ConfigEngine(plugin);

        engine.setMaximumTransactionHistoryCount(ConfigEngine.MAX_HISTORY_TRANSACTIONS + 1);
        assertEquals(ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(0);
        assertEquals(ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());

        engine.setMaximumTransactionHistoryCount(-5);
        assertEquals(ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS, engine.getMaximumTransactionHistoryCount());
    }

    @Test
    void save_writesMutableFields_andPersists()
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        engine.setNodeAddress("http://new-node");
        engine.setWalletId("NEWWID");
        engine.setMasterWallet("ban_master");
        engine.setEnableOfflinePayment(true);
        engine.setWebsocketUrl("wss://new");

        assertTrue(engine.save());

        verify(config).set("IP", "http://new-node");
        verify(config).set("walletID", "NEWWID");
        verify(config).set("masterWallet", "ban_master");
        verify(config).set("allowofflinepayment", true);
        verify(config).set("websocketUrl", "wss://new");
        verify(plugin, atLeastOnce()).saveConfig();
        // Re-reading after a write would let a torn file drop the encrypted seeds from memory.
        verify(plugin, never()).reloadConfig();
    }

    @Test
    void reload_reReadsConfig()
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        when(config.getString("IP", "")).thenReturn("http://reloaded");

        engine.reload();

        verify(plugin).reloadConfig();
        assertEquals("http://reloaded", engine.getNodeAddress());
    }

    @Test
    void initialiseConfig_isNullSafe()
    {
        when(plugin.getConfig()).thenReturn(null);
        assertDoesNotThrow(() -> new ConfigEngine(plugin));
    }

    // -------------------------------------------------------------------------
    // Bank seed / isBankSeedConfigured
    // -------------------------------------------------------------------------

    @Test
    void IsBankSeedConfiguredFalseWhenAbsent()
    {
        // config mock returns "" for bankWalletSeed (default answer)
        ConfigEngine engine = new ConfigEngine(plugin);
        assertFalse(engine.isBankSeedConfigured());
    }

    @Test
    void IsBankSeedConfiguredFalseWhenPlaceholder()
    {
        when(config.getString("bankWalletSeed", "")).thenReturn("INSERT BANK WALLET SEED HERE");
        ConfigEngine engine = new ConfigEngine(plugin);
        assertFalse(engine.isBankSeedConfigured());
    }

    @Test
    void IsBankSeedConfiguredTrueWhenRealSeed()
    {
        when(config.getString("bankWalletSeed", "")).thenReturn("AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233");
        ConfigEngine engine = new ConfigEngine(plugin);
        assertTrue(engine.isBankSeedConfigured());
    }

    @Test
    void BankSeedIsEncryptedInConfigOnFirstLoad()
    {
        String plainSeed = "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233";
        when(config.getString("bankWalletSeed", "")).thenReturn(plainSeed);

        new ConfigEngine(plugin);

        // The plaintext seed must have been replaced with an ENC: value.
        verify(config).set(eq("bankWalletSeed"), argThat(v ->
                v instanceof String && ((String) v).startsWith(SecretManager.ENC_PREFIX)));
        verify(plugin, atLeastOnce()).saveConfig();
    }

    @Test
    void WalletSeedIsEncryptedInConfigOnFirstLoad()
    {
        String plainSeed = "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233";
        when(config.getString("walletSeed", "")).thenReturn(plainSeed);

        new ConfigEngine(plugin);

        verify(config).set(eq("walletSeed"), argThat(v ->
                v instanceof String && ((String) v).startsWith(SecretManager.ENC_PREFIX)));
        verify(plugin, atLeastOnce()).saveConfig();
    }

    @Test
    void SetBankWalletIdPersistedBySave()
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        engine.setBankWalletId("BANK-WALLET-ID");

        engine.save();

        verify(config).set("bankWalletID", "BANK-WALLET-ID");
    }

    @Test
    void BankWalletIdNotWrittenBySaveWhenEmpty()
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        // bankWalletId is "" by default

        engine.save();

        verify(config, never()).set(eq("bankWalletID"), any());
    }

    @Test
    void BankSeedDecryptionFailureLogsSevereAndDisablesBanking() throws Exception
    {
        // Encrypt the seed with the current key, then delete it so the next
        // SecretManager (inside ConfigEngine) generates a new, incompatible key.
        SecretManager initialSm = new SecretManager(new File(tempDir, "secret.key"),
                Logger.getLogger("setup"));
        String encrypted = initialSm.encrypt(
                "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233");
        assertTrue(new File(tempDir, "secret.key").delete(), "key file must be deletable");

        when(config.getString("bankWalletSeed", "")).thenReturn(encrypted);

        Logger logger = Logger.getLogger("test");
        List<LogRecord> captured = new ArrayList<>();
        Handler captor = new Handler()
        {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(captor);
        ConfigEngine engine;
        try
        {
            engine = new ConfigEngine(plugin);
        }
        finally
        {
            logger.removeHandler(captor);
        }

        assertFalse(engine.isBankSeedConfigured(), "banking must be disabled after decryption failure");
        assertTrue(captured.stream().anyMatch(r ->
                r.getLevel() == Level.SEVERE && r.getMessage().contains("bankWalletSeed")),
                "a SEVERE log about bankWalletSeed decryption failure must be emitted");
    }

    // -------------------------------------------------------------------------
    // walletSeed auto-generation
    // -------------------------------------------------------------------------

    @Test
    void WalletSeedIsAutoGeneratedWhenAbsent()
    {
        // Default stub returns "" for walletSeed — triggers auto-generation.
        ConfigEngine engine = new ConfigEngine(plugin);

        String seed = engine.getWalletSeed();
        assertNotNull(seed);
        assertEquals(64, seed.length(), "Generated seed must be a 64-char hex string");
        assertTrue(seed.matches("[0-9A-F]{64}"), "Generated seed must be uppercase hex");
    }

    @Test
    void WalletSeedIsNotRegenerated_whenSecretKeyAlreadyExists() throws Exception
    {
        // A missing walletSeed on a server that already has secret.key means config.yml lost it
        // (truncated / edited) - generating a new seed would silently switch to a new wallet.
        java.nio.file.Files.write(new File(tempDir, "secret.key").toPath(), new byte[32]);

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("", engine.getWalletSeed());
        verify(config, never()).set(eq("walletSeed"), any());
    }

    @Test
    void WalletSeedIsAutoGeneratedWhenPlaceholder()
    {
        when(config.getString("walletSeed", "")).thenReturn("INSERT WALLET SEED HERE");
        ConfigEngine engine = new ConfigEngine(plugin);

        String seed = engine.getWalletSeed();
        assertEquals(64, seed.length());
        assertTrue(seed.matches("[0-9A-F]{64}"));
    }

    @Test
    void WalletSeedAutoGeneratedTwoDifferentEnginesProduceDifferentSeeds()
    {
        // Each engine gets its own temp folder so secret.key doesn't clash.
        File dir2 = new File(tempDir, "second");
        dir2.mkdirs();
        Plugin plugin2 = mock(Plugin.class);
        FileConfiguration config2 = mock(FileConfiguration.class);
        when(plugin2.getLogger()).thenReturn(Logger.getLogger("test2"));
        when(plugin2.getConfig()).thenReturn(config2);
        when(plugin2.getDataFolder()).thenReturn(dir2);
        when(config2.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(config2.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(config2.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config2.contains("masterWallet")).thenReturn(true);
        when(config2.getString("masterWallet", "")).thenReturn("");

        String seed1 = new ConfigEngine(plugin).getWalletSeed();
        String seed2 = new ConfigEngine(plugin2).getWalletSeed();

        assertNotEquals(seed1, seed2, "Each auto-generated seed must be unique");
    }

    @Test
    void WalletSeedAutoGeneratedIsEncryptedInConfig()
    {
        new ConfigEngine(plugin);

        // The auto-generated seed must be persisted as ENC:… in config.
        verify(config).set(eq("walletSeed"), argThat(v ->
                v instanceof String && ((String) v).startsWith(SecretManager.ENC_PREFIX)));
        verify(plugin, atLeastOnce()).saveConfig();
    }

    @Test
    void WalletSeedAutoGeneratedWritesSeedBackupFile()
    {
        new ConfigEngine(plugin);

        File backup = new File(tempDir, "seed-backup.txt");
        assertTrue(backup.exists(), "seed-backup.txt must be created on first-run seed generation");
        assertTrue(backup.length() > 0, "seed-backup.txt must not be empty");
    }

    @Test
    void WalletSeedAutoGeneratedBackupFileContainsMnemonicAndHex() throws Exception
    {
        ConfigEngine engine = new ConfigEngine(plugin);
        String expectedHex = engine.getWalletSeed();

        File backup   = new File(tempDir, "seed-backup.txt");
        String content = Files.readString(backup.toPath());

        // Hex seed must appear verbatim in the backup file.
        assertTrue(content.contains(expectedHex),
                "Backup file must contain the hex seed");

        // Reconstruct the expected mnemonic and verify each numbered word appears in the file.
        String expectedMnemonic = new Bip39().toMnemonic(java.util.HexFormat.of().parseHex(expectedHex));
        String[] words = expectedMnemonic.split(" ");
        for (int i = 0; i < words.length; i++)
        {
            assertTrue(content.contains((i + 1) + ". " + words[i]),
                    "Backup file must contain mnemonic word " + (i + 1) + ": " + words[i]);
        }
    }

    @Test
    void WalletSeedExistingBackupFileLogsSevereNagOnStartup() throws Exception
    {
        // Create a backup file manually — simulates a server restart where the admin hasn't deleted it yet.
        new File(tempDir, "seed-backup.txt").createNewFile();

        // Provide an already-encrypted seed so no re-generation fires on this startup.
        SecretManager sm = new SecretManager(new File(tempDir, "secret.key"), Logger.getLogger("setup"));
        String encrypted = sm.encrypt("AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233");
        when(config.getString("walletSeed", "")).thenReturn(encrypted);

        Logger logger = Logger.getLogger("test");
        List<LogRecord> captured = new ArrayList<>();
        Handler captor = new Handler()
        {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(captor);
        try
        {
            new ConfigEngine(plugin);
        }
        finally
        {
            logger.removeHandler(captor);
        }

        assertTrue(captured.stream().anyMatch(r ->
                r.getLevel() == Level.SEVERE
                && r.getMessage().contains("seed-backup.txt")),
                "A SEVERE nag about the backup file must be logged while the file exists");
    }

    @Test
    void WalletSeedNoBackupNagWhenFileAbsent()
    {
        // Stub walletSeed to a real encrypted value (no auto-generation, no backup file created).
        SecretManager sm = new SecretManager(new File(tempDir, "secret.key"), Logger.getLogger("setup"));
        String encrypted = sm.encrypt("AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233");
        when(config.getString("walletSeed", "")).thenReturn(encrypted);

        Logger logger = Logger.getLogger("test");
        List<LogRecord> captured = new ArrayList<>();
        Handler captor = new Handler()
        {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(captor);
        try
        {
            new ConfigEngine(plugin);
        }
        finally
        {
            logger.removeHandler(captor);
        }

        assertFalse(captured.stream().anyMatch(r ->
                r.getLevel() == Level.SEVERE
                && r.getMessage().contains("seed-backup.txt")),
                "No backup-file nag must fire when the file does not exist");
    }

    @Test
    void WalletSeedDecryptionFailureLogsSevere() throws Exception
    {
        SecretManager initialSm = new SecretManager(new File(tempDir, "secret.key"),
                Logger.getLogger("setup"));
        String encrypted = initialSm.encrypt(
                "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233");
        assertTrue(new File(tempDir, "secret.key").delete(), "key file must be deletable");

        when(config.getString("walletSeed", "")).thenReturn(encrypted);

        Logger logger = Logger.getLogger("test");
        List<LogRecord> captured = new ArrayList<>();
        Handler captor = new Handler()
        {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(captor);
        try
        {
            new ConfigEngine(plugin);
        }
        finally
        {
            logger.removeHandler(captor);
        }

        assertTrue(captured.stream().anyMatch(r ->
                r.getLevel() == Level.SEVERE && r.getMessage().contains("walletSeed")),
                "a SEVERE log about walletSeed decryption failure must be emitted");
    }

    // -------------------------------------------------------------------------
    // Seed safety
    // -------------------------------------------------------------------------

    private List<LogRecord> captureLogs(Runnable action)
    {
        Logger logger = Logger.getLogger("test");
        List<LogRecord> captured = new ArrayList<>();
        Handler captor = new Handler()
        {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(captor);
        try
        {
            action.run();
        }
        finally
        {
            logger.removeHandler(captor);
        }
        return captured;
    }

    @Test
    void WalletSeedNotGenerated_whenWalletIdAlreadySet()
    {
        when(config.getString("walletID", "")).thenReturn("EXISTING-WALLET-ID");

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals("", engine.getWalletSeed());
        verify(config, never()).set(eq("walletSeed"), any());
        assertFalse(new File(tempDir, "seed-backup.txt").exists(),
                "No backup must be written for a seed that was never generated");
    }

    @Test
    void WalletSeedGenerated_whenWalletIdIsPlaceholder()
    {
        when(config.getString("walletID", "")).thenReturn("INSERT WALLET ID FROM NODE HERE");

        ConfigEngine engine = new ConfigEngine(plugin);

        assertEquals(64, engine.getWalletSeed().length());
    }

    @Test
    void WalletSeedBackupFileIsOwnerOnly() throws Exception
    {
        assumeTrue(tempDir.toPath().getFileSystem().supportedFileAttributeViews().contains("posix"));

        new ConfigEngine(plugin);

        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(new File(tempDir, "seed-backup.txt").toPath()));
    }

    @Test
    void WalletSeedExistingBackupFileIsNotOverwritten() throws Exception
    {
        File existing = new File(tempDir, "seed-backup.txt");
        Files.writeString(existing.toPath(), "OLD BACKUP");

        new ConfigEngine(plugin);

        assertEquals("OLD BACKUP", Files.readString(existing.toPath()));
        File[] others = tempDir.listFiles((dir, name) -> name.startsWith("seed-backup-") && name.endsWith(".txt"));
        assertNotNull(others);
        assertEquals(1, others.length, "The new seed must be backed up to a separate file");
    }

    @Test
    void WalletSeedIsNeverLogged()
    {
        ConfigEngine[] holder = new ConfigEngine[1];
        List<LogRecord> logs = captureLogs(() -> holder[0] = new ConfigEngine(plugin));

        String seed = holder[0].getWalletSeed();
        assertTrue(logs.stream().noneMatch(r -> r.getMessage() != null && r.getMessage().contains(seed)),
                "The seed must never appear in the server log");
    }

    @Test
    void PlaintextSeedNotSaved_whenKeyUnusable() throws Exception
    {
        // A wrong-length key makes SecretManager not ready.
        Files.write(new File(tempDir, "secret.key").toPath(), new byte[] {1, 2, 3});
        String plainSeed = "AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233AABBCCDD00112233";
        when(config.getString("walletSeed", "")).thenReturn(plainSeed);

        List<LogRecord> logs = captureLogs(() -> new ConfigEngine(plugin));

        verify(config, never()).set(eq("walletSeed"), any());
        assertTrue(logs.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getMessage().contains("walletSeed")));
    }

    @Test
    void GeneratedSeedNotSaved_whenKeyUnusable() throws Exception
    {
        Files.write(new File(tempDir, "secret.key").toPath(), new byte[] {1, 2, 3});

        ConfigEngine engine = new ConfigEngine(plugin);

        verify(config, never()).set(eq("walletSeed"), any());
        assertEquals("", engine.getWalletSeed());
        assertFalse(new File(tempDir, "seed-backup.txt").exists());
    }

    @Test
    void NodeAddressCredentialsAreRedactedInLog()
    {
        when(config.getString("IP", "")).thenReturn("http://rpcuser:hunter2@node:7072");

        List<LogRecord> logs = captureLogs(() -> new ConfigEngine(plugin));

        assertTrue(logs.stream().noneMatch(r -> r.getMessage() != null && r.getMessage().contains("hunter2")));
        assertTrue(logs.stream().anyMatch(r -> r.getMessage() != null
                && r.getMessage().contains("http://***@node:7072")));
    }

    @Test
    void redactUserInfo_leavesPlainUrlsUntouched()
    {
        assertEquals("http://node:7072", ConfigEngine.redactUserInfo("http://node:7072"));
        assertEquals("https://***@host/path", ConfigEngine.redactUserInfo("https://u:p@host/path"));
        assertEquals("", ConfigEngine.redactUserInfo(""));
        assertNull(ConfigEngine.redactUserInfo(null));
    }
}
