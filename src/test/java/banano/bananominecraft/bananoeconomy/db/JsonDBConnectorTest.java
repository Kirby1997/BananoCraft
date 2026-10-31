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
}
