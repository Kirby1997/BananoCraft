package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonDBConnectorTest
{
    private ServerMock server;
    private PluginMock plugin;
    private JsonDBConnector db;
    private PlayerMock player;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        db = new JsonDBConnector(plugin);
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown()
    {
        db.close();
        MockBukkit.unmock();
    }

    // --- player records ---

    @Test
    void createPlayerRecord_isRetrievable()
    {
        PlayerRecord created = db.createPlayerRecord(player, "ban_wallet");

        assertNotNull(created);
        assertEquals("ban_wallet", created.getWallet());
        assertTrue(db.hasPlayerRecord(player));
        assertEquals("ban_wallet", db.getPlayerRecord(player).getWallet());
    }

    @Test
    void createPlayerRecord_isIdempotent()
    {
        PlayerRecord first  = db.createPlayerRecord(player, "ban_wallet");
        PlayerRecord second = db.createPlayerRecord(player, "ban_different");

        // The second call must not overwrite the first wallet assignment.
        assertEquals(first.getWallet(), second.getWallet());
        assertEquals("ban_wallet", db.getPlayerRecord(player).getWallet());
    }

    @Test
    void frozenListing_reflectsRecordState()
    {
        PlayerRecord record = db.createPlayerRecord(player, "ban_wallet");

        assertTrue(db.getUnfrozenPlayers().contains(record));
        assertFalse(db.getFrozenPlayers().contains(record));

        record.setFrozen(true);

        assertTrue(db.getFrozenPlayers().contains(record));
        assertFalse(db.getUnfrozenPlayers().contains(record));
    }

    @Test
    void playerRecord_persistsAcrossConnectorInstances()
    {
        db.createPlayerRecord(player, "ban_persisted");

        // A fresh connector over the same data folder must load the record from disk.
        JsonDBConnector reopened = new JsonDBConnector(plugin);
        PlayerRecord loaded = reopened.getPlayerRecord(player);

        assertNotNull(loaded);
        assertEquals("ban_persisted", loaded.getWallet());
        reopened.close();
    }

    // --- offline payments ---

    @Test
    void offlinePayments_saveGetTotalDelete()
    {
        OfflinePaymentRecord p1 = new OfflinePaymentRecord(
                player.getUniqueId(), "Bob", 1.5, "HASH1", LocalDateTime.now(), "");
        OfflinePaymentRecord p2 = new OfflinePaymentRecord(
                player.getUniqueId(), "Carol", 2.5, "HASH2", LocalDateTime.now(), "note");

        assertTrue(db.saveOfflinePayment(p1));
        assertTrue(db.saveOfflinePayment(p2));

        List<OfflinePaymentRecord> records = db.getOfflinePaymentRecords(player);
        assertEquals(2, records.size());
        assertEquals(4.0, db.getOfflinePaymentsTotal(player));

        db.deleteOfflinePaymentRecords(player);
        assertTrue(db.getOfflinePaymentRecords(player).isEmpty());
        assertEquals(0.0, db.getOfflinePaymentsTotal(player));
    }

    @Test
    void saveOfflinePayment_rejectsNull()
    {
        assertFalse(db.saveOfflinePayment(null));
    }

    @Test
    void offlinePayments_isolatedPerPlayer()
    {
        PlayerMock other = server.addPlayer("Bob");
        db.saveOfflinePayment(new OfflinePaymentRecord(
                player.getUniqueId(), "Carol", 9.0, "HASH", LocalDateTime.now(), ""));

        assertEquals(9.0, db.getOfflinePaymentsTotal(player));
        assertEquals(0.0, db.getOfflinePaymentsTotal(other));
    }

    @Test
    void offlinePaymentsTotal_nullSafe()
    {
        assertEquals(0.0, db.getOfflinePaymentsTotal(null));
        assertTrue(db.getOfflinePaymentRecords(null).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Bank records
    // -------------------------------------------------------------------------

    private static final String BANK_NAME  = "treasury";
    private static final String BANK_ADDR  = "ban_3bankaddress1111111111111111111111111111111111111111111111111";
    private static final String OWNER_UUID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_UUID = "00000000-0000-0000-0000-000000000002";

    private BankRecord makeBank()
    {
        return new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 1_000_000L);
    }

    @Test
    void createBankRecord_isRetrievable()
    {
        assertTrue(db.createBankRecord(makeBank()));

        assertTrue(db.bankNameExists(BANK_NAME));

        BankRecord loaded = db.getBankRecord(BANK_NAME);
        assertNotNull(loaded);
        assertEquals(BANK_NAME,  loaded.getBankName());
        assertEquals(BANK_ADDR,  loaded.getAddress());
        assertEquals(OWNER_UUID, loaded.getOwnerUuid());
    }

    @Test
    void createBankRecord_isIdempotent()
    {
        assertTrue(db.createBankRecord(makeBank()));
        // Second call with the same name must fail — no overwrite.
        assertFalse(db.createBankRecord(new BankRecord(BANK_NAME, "ban_other", OTHER_UUID, 2_000_000L)));

        assertEquals(BANK_ADDR, db.getBankRecord(BANK_NAME).getAddress());
    }

    @Test
    void bankNameExists_falseWhenAbsent()
    {
        assertFalse(db.bankNameExists(BANK_NAME));
    }

    @Test
    void getBankRecord_returnsNull_whenAbsent()
    {
        assertNull(db.getBankRecord(BANK_NAME));
    }

    @Test
    void getAllBankNames_returnsAll()
    {
        db.createBankRecord(makeBank());
        db.createBankRecord(new BankRecord("guild", "ban_3guild111111111111111111111111111111111111111111111111111111", OTHER_UUID, 0));

        List<String> names = db.getAllBankNames();
        assertEquals(2, names.size());
        assertTrue(names.contains(BANK_NAME));
        assertTrue(names.contains("guild"));
    }

    @Test
    void getAllBankNames_emptyWhenNoBanks()
    {
        assertTrue(db.getAllBankNames().isEmpty());
    }

    @Test
    void deleteBankRecord_removesRecord()
    {
        db.createBankRecord(makeBank());

        assertTrue(db.deleteBankRecord(BANK_NAME));

        assertFalse(db.bankNameExists(BANK_NAME));
        assertNull(db.getBankRecord(BANK_NAME));
    }

    @Test
    void deleteBankRecord_returnsFalse_whenNotFound()
    {
        assertFalse(db.deleteBankRecord("nonexistent"));
    }

    @Test
    void deleteBankRecord_alsoRemovesMembers()
    {
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);
        db.addBankMember(BANK_NAME, OTHER_UUID);

        db.deleteBankRecord(BANK_NAME);

        assertFalse(db.isBankMember(BANK_NAME, OWNER_UUID));
        assertFalse(db.isBankMember(BANK_NAME, OTHER_UUID));
    }

    // -------------------------------------------------------------------------
    // Bank members
    // -------------------------------------------------------------------------

    @Test
    void addBankMember_isRetrievable()
    {
        db.createBankRecord(makeBank());
        assertTrue(db.addBankMember(BANK_NAME, OWNER_UUID));

        assertTrue(db.isBankMember(BANK_NAME, OWNER_UUID));
    }

    @Test
    void addBankMember_isIdempotent()
    {
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);
        db.addBankMember(BANK_NAME, OWNER_UUID); // duplicate — must not throw or double-add

        assertTrue(db.isBankMember(BANK_NAME, OWNER_UUID));
    }

    @Test
    void isBankMember_falseWhenNotAdded()
    {
        db.createBankRecord(makeBank());
        assertFalse(db.isBankMember(BANK_NAME, OWNER_UUID));
    }

    @Test
    void removeBankMember_removesFromSet()
    {
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);
        db.addBankMember(BANK_NAME, OTHER_UUID);

        assertTrue(db.removeBankMember(BANK_NAME, OWNER_UUID));

        assertFalse(db.isBankMember(BANK_NAME, OWNER_UUID));
        assertTrue(db.isBankMember(BANK_NAME, OTHER_UUID)); // unaffected
    }

    @Test
    void removeBankMember_returnsFalse_whenNotMember()
    {
        db.createBankRecord(makeBank());
        assertFalse(db.removeBankMember(BANK_NAME, OWNER_UUID));
    }

    @Test
    void members_isolatedPerBank()
    {
        db.createBankRecord(makeBank());
        db.createBankRecord(new BankRecord("guild", "ban_3guild111111111111111111111111111111111111111111111111111111", OTHER_UUID, 0));

        db.addBankMember(BANK_NAME, OWNER_UUID);

        assertTrue(db.isBankMember(BANK_NAME, OWNER_UUID));
        assertFalse(db.isBankMember("guild", OWNER_UUID)); // not a member of the other bank
    }

    // -------------------------------------------------------------------------
    // isBankOwner
    // -------------------------------------------------------------------------

    @Test
    void isBankOwner_trueForOwnerUuid()
    {
        db.createBankRecord(makeBank());
        assertTrue(db.isBankOwner(BANK_NAME, OWNER_UUID));
    }

    @Test
    void isBankOwner_falseForNonOwner()
    {
        db.createBankRecord(makeBank());
        assertFalse(db.isBankOwner(BANK_NAME, OTHER_UUID));
    }

    @Test
    void isBankOwner_falseWhenBankAbsent()
    {
        assertFalse(db.isBankOwner(BANK_NAME, OWNER_UUID));
    }

    // -------------------------------------------------------------------------
    // JSON persistence across connector instances
    // -------------------------------------------------------------------------

    @Test
    void bankRecord_persistsAcrossConnectorInstances()
    {
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);

        // Fresh connector must load banks.json and bank_members.json from disk.
        JsonDBConnector reopened = new JsonDBConnector(plugin);

        assertTrue(reopened.bankNameExists(BANK_NAME));
        assertEquals(BANK_ADDR, reopened.getBankRecord(BANK_NAME).getAddress());
        assertTrue(reopened.isBankMember(BANK_NAME, OWNER_UUID));

        reopened.close();
    }

    @Test
    void bankDeletion_persistsAcrossConnectorInstances()
    {
        db.createBankRecord(makeBank());
        db.deleteBankRecord(BANK_NAME);

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertFalse(reopened.bankNameExists(BANK_NAME));
        reopened.close();
    }

    @Test
    void isBankMember_trueForMemberAddedBeforeOtherMember()
    {
        // Regression: partial-cache bug where adding member B causes the cache set
        // to become non-null, so a subsequent isBankMember check for member A
        // (already in the set from loadBankMembers) incorrectly returned false.
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);
        db.addBankMember(BANK_NAME, OTHER_UUID);

        // Both members were added — neither should ever be reported absent.
        assertTrue(db.isBankMember(BANK_NAME, OWNER_UUID));
        assertTrue(db.isBankMember(BANK_NAME, OTHER_UUID));
    }

    @Test
    void isBankMember_trueAfterReopen_whenLoadedFromDisk()
    {
        // Members persisted in a previous session must still be recognised by a
        // fresh connector instance (tests the load-from-disk path).
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);
        db.addBankMember(BANK_NAME, OTHER_UUID);

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertTrue(reopened.isBankMember(BANK_NAME, OWNER_UUID));
        assertTrue(reopened.isBankMember(BANK_NAME, OTHER_UUID));
        reopened.close();
    }

    // -------------------------------------------------------------------------
    // Missing / empty / corrupt player files
    // -------------------------------------------------------------------------

    private File dataDir()
    {
        return new File(plugin.getDataFolder(), "data");
    }

    private File playerFile(PlayerMock p)
    {
        return new File(dataDir(), p.getUniqueId() + ".json");
    }

    @Test
    void getPlayerRecord_missingFile_returnsNullWithoutCreatingFile()
    {
        assertNull(db.getPlayerRecord(player));
        assertNull(db.getOfflinePlayerRecord(player));

        assertFalse(playerFile(player).exists(), "Loading a missing record must not create an empty file");
        assertFalse(db.hasPlayerRecord(player));
    }

    @Test
    void emptyPlayerFile_isTreatedAsNoRecord() throws IOException
    {
        assertTrue(playerFile(player).createNewFile());

        assertFalse(db.hasPlayerRecord(player));
        assertNull(db.getPlayerRecord(player));

        // ...so a record can still be created over it.
        PlayerRecord created = db.createPlayerRecord(player, "ban_after_empty");
        assertNotNull(created);
        assertEquals("ban_after_empty", created.getWallet());
    }

    @Test
    void corruptPlayerFile_isTreatedAsNoRecord() throws IOException
    {
        Files.writeString(playerFile(player).toPath(), "{ not valid json", StandardCharsets.UTF_8);

        assertFalse(db.hasPlayerRecord(player));
        assertNull(db.getPlayerRecord(player));
    }

    // -------------------------------------------------------------------------
    // Atomic writes
    // -------------------------------------------------------------------------

    @Test
    void writes_leaveNoTempFilesBehind()
    {
        db.createPlayerRecord(player, "ban_wallet");
        db.createBankRecord(makeBank());
        db.addBankMember(BANK_NAME, OWNER_UUID);
        db.saveOfflinePayment(new OfflinePaymentRecord(
                player.getUniqueId(), "Bob", 1.0, "HASH", LocalDateTime.now(), ""));

        String[] leftovers = dataDir().list((dir, name) -> name.endsWith(".tmp"));

        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
        assertTrue(playerFile(player).length() > 0);
    }

    // -------------------------------------------------------------------------
    // Offline payments are persisted immediately
    // -------------------------------------------------------------------------

    @Test
    void offlinePayment_persistedImmediately_withoutClose()
    {
        db.saveOfflinePayment(new OfflinePaymentRecord(
                player.getUniqueId(), "Bob", 3.0, "HASH", LocalDateTime.now(), "hi"));

        // No close()/flush: simulates a crash straight after the tip.
        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals(3.0, reopened.getOfflinePaymentsTotal(player));
        reopened.close();
    }

    @Test
    void offlinePaymentDeletion_persistedImmediately_withoutClose()
    {
        db.saveOfflinePayment(new OfflinePaymentRecord(
                player.getUniqueId(), "Bob", 3.0, "HASH", LocalDateTime.now(), ""));
        db.deleteOfflinePaymentRecords(player);

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals(0.0, reopened.getOfflinePaymentsTotal(player));
        reopened.close();
    }

    // -------------------------------------------------------------------------
    // Claimed wallet index
    // -------------------------------------------------------------------------

    @Test
    void claimedWallets_updatedOnInsert()
    {
        PlayerMock other = server.addPlayer("Bob");
        db.createPlayerRecord(player, "ban_alice");

        assertTrue(db.isAlreadyAssignedToOtherPlayer("ban_alice", other));
        assertFalse(db.isAlreadyAssignedToOtherPlayer("ban_alice", player));
    }

    @Test
    void claimedWallets_loadedSynchronouslyOnStartup_ignoringNonPlayerFiles()
    {
        PlayerMock other = server.addPlayer("Bob");
        db.createPlayerRecord(player, "ban_alice");
        db.createBankRecord(makeBank()); // banks.json must not be parsed as a player file

        // Index must be complete as soon as the constructor returns — no scheduler tick.
        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertTrue(reopened.isAlreadyAssignedToOtherPlayer("ban_alice", other));
        reopened.close();
    }

    // -------------------------------------------------------------------------
    // updatePlayerWallet
    // -------------------------------------------------------------------------

    @Test
    void updatePlayerWallet_updatesCacheDiskAndIndex()
    {
        PlayerMock other = server.addPlayer("Bob");
        PlayerRecord record = db.createPlayerRecord(player, "ban_old");

        assertTrue(db.updatePlayerWallet(player, "ban_new"));

        // Cached instance is updated in place.
        assertEquals("ban_new", record.getWallet());
        assertEquals("ban_new", db.getPlayerRecord(player).getWallet());

        // Wallet index follows the change.
        assertTrue(db.isAlreadyAssignedToOtherPlayer("ban_new", other));
        assertFalse(db.isAlreadyAssignedToOtherPlayer("ban_old", other));

        // Persisted to disk.
        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals("ban_new", reopened.getPlayerRecord(player).getWallet());
        assertTrue(reopened.isAlreadyAssignedToOtherPlayer("ban_new", other));
        assertFalse(reopened.isAlreadyAssignedToOtherPlayer("ban_old", other));
        reopened.close();
    }

    @Test
    void updatePlayerWallet_returnsFalse_whenNoRecord()
    {
        assertFalse(db.updatePlayerWallet(player, "ban_new"));
        assertFalse(playerFile(player).exists());
    }

    // -------------------------------------------------------------------------
    // walletIndex (seed-derivation index) — players
    // -------------------------------------------------------------------------

    private void writeLegacyPlayerFile(PlayerMock p, String wallet) throws IOException
    {
        // Exactly what a pre-walletIndex version wrote: no walletIndex key at all.
        Files.writeString(playerFile(p).toPath(),
                "{\"playerUUID\":\"" + p.getUniqueId() + "\",\"playerName\":\"" + p.getName()
                        + "\",\"wallet\":\"" + wallet + "\",\"isFrozen\":false}",
                StandardCharsets.UTF_8);
    }

    @Test
    void walletIndex_roundTripsThroughDisk()
    {
        PlayerRecord created = db.createPlayerRecord(player, "ban_wallet", 5);

        assertEquals(5, created.getWalletIndex());
        assertEquals(5, db.getPlayerWalletIndex("ban_wallet"));
        assertEquals(5, db.getMaxPlayerWalletIndex());

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals(5, reopened.getPlayerRecord(player).getWalletIndex());
        assertEquals(5, reopened.getPlayerWalletIndex("ban_wallet"));
        assertEquals(5, reopened.getMaxPlayerWalletIndex());
        reopened.close();
    }

    @Test
    void createPlayerRecord_withoutIndex_hasNullIndex_andZeroMax()
    {
        PlayerRecord created = db.createPlayerRecord(player, "ban_wallet");

        assertNull(created.getWalletIndex());
        assertNull(db.getPlayerWalletIndex("ban_wallet"));
        assertEquals(0, db.getMaxPlayerWalletIndex());
    }

    @Test
    void legacyFileWithoutWalletIndex_loadsWithNullIndex() throws IOException
    {
        writeLegacyPlayerFile(player, "ban_legacy");

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        PlayerRecord record = reopened.getPlayerRecord(player);

        assertNotNull(record);
        assertEquals("ban_legacy", record.getWallet());
        assertNull(record.getWalletIndex());
        assertEquals(0, reopened.getMaxPlayerWalletIndex());
        reopened.close();
    }

    @Test
    void getPlayerRecordsMissingWalletIndex_listsOnlyRecordsWithoutIndex() throws IOException
    {
        PlayerMock legacy = server.addPlayer("Legacy");
        writeLegacyPlayerFile(legacy, "ban_legacy");
        db.createPlayerRecord(player, "ban_new", 3);

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        List<PlayerRecord> missing = reopened.getPlayerRecordsMissingWalletIndex();

        assertEquals(1, missing.size());
        assertEquals("ban_legacy", missing.get(0).getWallet());
        assertEquals(legacy.getUniqueId().toString(), missing.get(0).getPlayerUUID());
        reopened.close();
    }

    @Test
    void setPlayerWalletIndex_persistsForOfflineRecord() throws IOException
    {
        writeLegacyPlayerFile(player, "ban_legacy");
        JsonDBConnector reopened = new JsonDBConnector(plugin);

        assertTrue(reopened.setPlayerWalletIndex(player.getUniqueId().toString(), 12));

        assertEquals(12, reopened.getPlayerWalletIndex("ban_legacy"));
        assertEquals(12, reopened.getMaxPlayerWalletIndex());
        assertTrue(reopened.getPlayerRecordsMissingWalletIndex().isEmpty());
        reopened.close();

        JsonDBConnector again = new JsonDBConnector(plugin);
        assertEquals(12, again.getPlayerRecord(player).getWalletIndex());
        // The rest of the record is untouched.
        assertEquals("ban_legacy", again.getPlayerRecord(player).getWallet());
        assertEquals("Alice", again.getPlayerRecord(player).getPlayerName());
        again.close();
    }

    @Test
    void setPlayerWalletIndex_updatesTheCachedRecordOfAnOnlinePlayer()
    {
        PlayerRecord cached = db.createPlayerRecord(player, "ban_wallet");

        assertTrue(db.setPlayerWalletIndex(player.getUniqueId().toString(), 9));

        assertEquals(9, cached.getWalletIndex());
        assertEquals(9, db.getPlayerRecord(player).getWalletIndex());
    }

    @Test
    void setPlayerWalletIndex_returnsFalse_forUnknownPlayerOrBadUuid()
    {
        assertFalse(db.setPlayerWalletIndex(java.util.UUID.randomUUID().toString(), 3));
        assertFalse(db.setPlayerWalletIndex("not-a-uuid", 3));
        assertNull(db.getPlayerWalletIndex("ban_nothing"));
    }

    @Test
    void getMaxPlayerWalletIndex_isHighestAcrossAllPlayers_includingOffline()
    {
        PlayerMock bob = server.addPlayer("Bob");
        db.createPlayerRecord(player, "ban_a", 4);
        db.createPlayerRecord(bob, "ban_b", 17);

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals(17, reopened.getMaxPlayerWalletIndex());
        reopened.close();
    }

    @Test
    void updatePlayerWallet_withIndex_storesNewIndexAndDropsTheOldWalletFromTheIndexMap()
    {
        db.createPlayerRecord(player, "ban_old", 2);

        assertTrue(db.updatePlayerWallet(player, "ban_new", 8));

        assertEquals(8, db.getPlayerRecord(player).getWalletIndex());
        assertEquals(8, db.getPlayerWalletIndex("ban_new"));
        assertNull(db.getPlayerWalletIndex("ban_old"));
        assertEquals(8, db.getMaxPlayerWalletIndex());

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals(8, reopened.getPlayerRecord(player).getWalletIndex());
        assertEquals(8, reopened.getPlayerWalletIndex("ban_new"));
        reopened.close();
    }

    // -------------------------------------------------------------------------
    // walletIndex — banks
    // -------------------------------------------------------------------------

    @Test
    void bankWalletIndex_roundTripsThroughDisk()
    {
        assertTrue(db.createBankRecord(new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 1L, 3)));

        assertEquals(3, db.getBankRecord(BANK_NAME).getWalletIndex());
        assertEquals(3, db.getBankWalletIndex(BANK_ADDR));
        assertEquals(3, db.getMaxBankWalletIndex());

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertEquals(3, reopened.getBankRecord(BANK_NAME).getWalletIndex());
        assertEquals(3, reopened.getMaxBankWalletIndex());
        reopened.close();
    }

    @Test
    void legacyBanksFileWithoutWalletIndex_loadsWithNullIndex_thenBackfillPersists() throws IOException
    {
        Files.writeString(new File(dataDir(), "banks.json").toPath(),
                "[{\"bankName\":\"" + BANK_NAME + "\",\"address\":\"" + BANK_ADDR + "\",\"ownerUuid\":\""
                        + OWNER_UUID + "\",\"createdAt\":1000000}]",
                StandardCharsets.UTF_8);

        JsonDBConnector reopened = new JsonDBConnector(plugin);
        assertNull(reopened.getBankRecord(BANK_NAME).getWalletIndex());
        assertNull(reopened.getBankWalletIndex(BANK_ADDR));
        assertEquals(0, reopened.getMaxBankWalletIndex());

        assertTrue(reopened.setBankWalletIndex(BANK_NAME, 6));
        assertEquals(6, reopened.getBankRecord(BANK_NAME).getWalletIndex());
        assertFalse(reopened.setBankWalletIndex("no-such-bank", 1));
        reopened.close();

        JsonDBConnector again = new JsonDBConnector(plugin);
        assertEquals(6, again.getBankRecord(BANK_NAME).getWalletIndex());
        assertEquals(6, again.getMaxBankWalletIndex());
        again.close();
    }
}
