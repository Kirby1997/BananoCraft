package banano.bananominecraft.bananoeconomy.integration;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.JsonDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.io.VaultConnector;
import banano.bananominecraft.bananoeconomy.services.BankService;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.UUID;

import static net.milkbowl.vault.economy.EconomyResponse.ResponseType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Full-stack integration tests.
 *
 * <p>These tests wire a real {@link JsonDBConnector} (backed by MockBukkit's
 * temp data folder), a real {@link BankService}, and a real {@link VaultConnector}
 * together.  Only {@link RPC} and {@link ConfigEngine} are mocked — the Banano
 * node and Bukkit configuration can never be present in a unit-test environment.</p>
 *
 * <p>Goals:
 * <ul>
 *   <li>Verify the full create → deposit → withdraw lifecycle end-to-end.</li>
 *   <li>Confirm that the balance cache interacts correctly with real DB reads.</li>
 *   <li>Catch integration bugs that pure unit tests with a mocked DB layer cannot.</li>
 * </ul></p>
 */
class BankFlowIntegrationTest
{
    private static final String BANK_NAME      = "treasury";
    private static final String BANK_ADDR      = "ban_3bankaddr111111111111111111111111111111111111111111111111111111";
    private static final String BANK_ADDR_2    = "ban_3bankaddr222222222222222222222222222222222222222222222222222222";
    private static final String MASTER_ADDR    = "ban_1masteraddr1111111111111111111111111111111111111111111111111111";
    private static final String BANK_WALLET_ID = "BANK-WALLET-ID";
    private static final String OWNER_UUID_STR = "00000000-0000-0000-0000-000000000001";
    private static final UUID   OWNER_UUID     = UUID.fromString(OWNER_UUID_STR);

    private ServerMock     server;
    private PluginMock     plugin;
    private RPC            rpc;
    private ConfigEngine   configEngine;
    private JsonDBConnector db;
    private BankService    bankService;
    private VaultConnector connector;
    private OfflinePlayer  owner;

    @BeforeEach
    void setUp()
    {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();

        rpc          = mock(RPC.class);
        configEngine = mock(ConfigEngine.class);

        when(configEngine.isBankSeedConfigured()).thenReturn(true);
        when(configEngine.getBankWalletId()).thenReturn(BANK_WALLET_ID);
        when(rpc.getMasterWallet()).thenReturn(MASTER_ADDR);
        when(rpc.accountCreate(-1, BANK_WALLET_ID)).thenReturn(BANK_ADDR);

        db          = new JsonDBConnector(plugin);
        bankService = new BankService(plugin, configEngine, rpc, db);
        connector   = new VaultConnector(plugin, mock(EconomyFuncs.class), rpc, bankService);

        owner = mock(OfflinePlayer.class);
        when(owner.getUniqueId()).thenReturn(OWNER_UUID);
    }

    @AfterEach
    void tearDown()
    {
        db.close();
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // Create bank
    // -------------------------------------------------------------------------

    @Test
    void createBank_bankAppearsInDB()
    {
        EconomyResponse r = connector.createBank(BANK_NAME, owner);

        assertEquals(SUCCESS, r.type);
        assertTrue(db.bankNameExists(BANK_NAME));
        assertEquals(BANK_ADDR, db.getBankRecord(BANK_NAME).getAddress());
    }

    @Test
    void createBank_ownerIsAutoAddedAsMember()
    {
        connector.createBank(BANK_NAME, owner);

        assertTrue(db.isBankMember(BANK_NAME, OWNER_UUID_STR));
    }

    @Test
    void createBank_duplicate_returnsFailure()
    {
        connector.createBank(BANK_NAME, owner);
        EconomyResponse r = connector.createBank(BANK_NAME, owner);

        assertEquals(FAILURE, r.type);
        // Only one bank record must exist.
        assertEquals(1, db.getAllBankNames().size());
    }

    @Test
    void getBanks_includesCreatedBank()
    {
        connector.createBank(BANK_NAME, owner);

        assertTrue(connector.getBanks().contains(BANK_NAME));
    }

    @Test
    void hasBankSupport_trueWhenSeedConfigured()
    {
        assertTrue(connector.hasBankSupport());
    }

    @Test
    void hasBankSupport_falseWhenSeedNotConfigured()
    {
        when(configEngine.isBankSeedConfigured()).thenReturn(false);
        assertFalse(connector.hasBankSupport());
    }

    // -------------------------------------------------------------------------
    // isBankOwner / isBankMember after real create
    // -------------------------------------------------------------------------

    @Test
    void isBankOwner_trueForCreator()
    {
        connector.createBank(BANK_NAME, owner);

        assertEquals(SUCCESS, connector.isBankOwner(BANK_NAME, owner).type);
    }

    @Test
    void isBankOwner_falseForNonOwner()
    {
        connector.createBank(BANK_NAME, owner);

        OfflinePlayer other = offlinePlayer("00000000-0000-0000-0000-000000000099");
        assertEquals(FAILURE, connector.isBankOwner(BANK_NAME, other).type);
    }

    @Test
    void isBankMember_trueForOwner_afterCreate()
    {
        connector.createBank(BANK_NAME, owner);

        assertEquals(SUCCESS, connector.isBankMember(BANK_NAME, owner).type);
    }

    @Test
    void isBankMember_falseForStranger()
    {
        connector.createBank(BANK_NAME, owner);

        OfflinePlayer stranger = offlinePlayer("00000000-0000-0000-0000-000000000099");
        assertEquals(FAILURE, connector.isBankMember(BANK_NAME, stranger).type);
    }

    // -------------------------------------------------------------------------
    // Full deposit → withdraw lifecycle with cache interactions
    // -------------------------------------------------------------------------

    @Test
    void deposit_then_withdraw_fullLifecycle() throws TransactionError
    {
        connector.createBank(BANK_NAME, owner);

        // Balance sequence for BANK_ADDR across all RPC calls in this test:
        //   call 1 — bankBalance() after create (cache miss)
        //   call 2 — bankBalance() inside bankDeposit response (cache miss after invalidation)
        //   call 3 — BankService.withdraw sufficiency check (always direct RPC, bypasses cache)
        //   call 4 — bankBalance() inside bankWithdraw response (cache miss after invalidation)
        //   call 5 — bankBalance() final assertion (cache hit from call 4 → no extra RPC call)
        when(rpc.getBalance(BANK_ADDR)).thenReturn(0.0, 100.0, 100.0, 60.0);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 100.0)).thenReturn("blk_deposit");
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 40.0, BANK_WALLET_ID)).thenReturn("blk_withdraw");

        // Initial balance is 0.
        EconomyResponse balance1 = connector.bankBalance(BANK_NAME);
        assertEquals(SUCCESS, balance1.type);
        assertEquals(0.0, balance1.balance);

        // Deposit 100 → master wallet → bank.
        EconomyResponse deposit = connector.bankDeposit(BANK_NAME, 100.0);
        assertEquals(SUCCESS, deposit.type);
        assertEquals(100.0, deposit.balance);

        // Withdraw 40 → bank → master wallet.
        EconomyResponse withdraw = connector.bankWithdraw(BANK_NAME, 40.0);
        assertEquals(SUCCESS, withdraw.type);
        assertEquals(40.0,  withdraw.amount);
        assertEquals(60.0,  withdraw.balance);

        // Final balance read hits cache (populated by the withdraw response fetch).
        EconomyResponse balance2 = connector.bankBalance(BANK_NAME);
        assertEquals(SUCCESS, balance2.type);
        assertEquals(60.0, balance2.balance);

        // RPC must have been called exactly 4 times (call 5 is a cache hit).
        verify(rpc, times(4)).getBalance(BANK_ADDR);
    }

    @Test
    void bankDeposit_insufficientMasterWallet_returnsFailure() throws TransactionError
    {
        connector.createBank(BANK_NAME, owner);

        when(rpc.getBalance(MASTER_ADDR)).thenReturn(10.0);

        EconomyResponse r = connector.bankDeposit(BANK_NAME, 100.0);

        assertEquals(FAILURE, r.type);
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void bankWithdraw_insufficientBankBalance_returnsFailure()
    {
        connector.createBank(BANK_NAME, owner);

        when(rpc.getBalance(BANK_ADDR)).thenReturn(10.0);

        EconomyResponse r = connector.bankWithdraw(BANK_NAME, 100.0);

        assertEquals(FAILURE, r.type);
        assertEquals("Insufficient bank funds.", r.errorMessage);
    }

    @Test
    void bankWithdraw_transactionError_returnsFailure() throws TransactionError
    {
        connector.createBank(BANK_NAME, owner);

        when(rpc.getBalance(BANK_ADDR)).thenReturn(200.0, 200.0);
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 50.0, BANK_WALLET_ID))
                .thenThrow(new TransactionError("Node timeout"));

        EconomyResponse r = connector.bankWithdraw(BANK_NAME, 50.0);

        assertEquals(FAILURE, r.type);
    }

    // -------------------------------------------------------------------------
    // Balance cache — integration-level verification
    // -------------------------------------------------------------------------

    @Test
    void bankBalance_cachePreventsDuplicateRPCCalls()
    {
        connector.createBank(BANK_NAME, owner);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(42.0);

        // Three consecutive reads on the same bank.
        connector.bankBalance(BANK_NAME);
        connector.bankBalance(BANK_NAME);
        connector.bankBalance(BANK_NAME);

        // The RPC must only have been called once (cache serves the rest).
        verify(rpc, times(1)).getBalance(BANK_ADDR);
    }

    @Test
    void deposit_invalidatesCache_and_freshValueIsReturned() throws TransactionError
    {
        connector.createBank(BANK_NAME, owner);

        when(rpc.getBalance(BANK_ADDR)).thenReturn(0.0, 50.0);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("blk");

        connector.bankBalance(BANK_NAME); // primes cache with 0.0

        EconomyResponse deposit = connector.bankDeposit(BANK_NAME, 50.0);

        // Response balance must be the fresh post-deposit value, not the cached 0.0.
        assertEquals(50.0, deposit.balance);
    }

    // -------------------------------------------------------------------------
    // Service re-instantiation — bank state survives new BankService
    // -------------------------------------------------------------------------

    @Test
    void bankState_survivesNewBankServiceInstance()
    {
        connector.createBank(BANK_NAME, owner);

        // A new BankService backed by the same DB reads the existing records.
        BankService newService = new BankService(plugin, configEngine, rpc, db);
        VaultConnector newConnector = new VaultConnector(plugin, mock(EconomyFuncs.class), rpc, newService);

        assertTrue(newConnector.hasBankSupport());
        assertTrue(newService.bankExists(BANK_NAME));
        assertEquals(SUCCESS, newConnector.isBankOwner(BANK_NAME, owner).type);
        assertEquals(SUCCESS, newConnector.isBankMember(BANK_NAME, owner).type);
    }

    // -------------------------------------------------------------------------
    // Multiple banks
    // -------------------------------------------------------------------------

    @Test
    void multipleBanks_areIndependent() throws TransactionError
    {
        when(rpc.accountCreate(-1, BANK_WALLET_ID))
                .thenReturn(BANK_ADDR)   // first createBank
                .thenReturn(BANK_ADDR_2); // second createBank

        OfflinePlayer owner2 = offlinePlayer("00000000-0000-0000-0000-000000000002");

        connector.createBank(BANK_NAME, owner);
        connector.createBank("guild", owner2);

        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0);
        when(rpc.getBalance(BANK_ADDR_2)).thenReturn(200.0);

        assertEquals(100.0, connector.bankBalance(BANK_NAME).balance);
        assertEquals(200.0, connector.bankBalance("guild").balance);

        // Ownership is correctly attributed.
        assertEquals(SUCCESS, connector.isBankOwner(BANK_NAME, owner).type);
        assertEquals(FAILURE, connector.isBankOwner(BANK_NAME, owner2).type);
        assertEquals(SUCCESS, connector.isBankOwner("guild",  owner2).type);
        assertEquals(FAILURE, connector.isBankOwner("guild",  owner).type);

        assertEquals(2, connector.getBanks().size());
    }

    // -------------------------------------------------------------------------
    // Disabled-state guard — all bank methods refuse when seed not configured
    // -------------------------------------------------------------------------

    @Test
    void allBankMethods_returnDisabled_whenSeedNotConfigured()
    {
        when(configEngine.isBankSeedConfigured()).thenReturn(false);
        BankService disabled = new BankService(plugin, configEngine, rpc, db);
        VaultConnector disabledConnector = new VaultConnector(plugin, mock(EconomyFuncs.class), rpc, disabled);

        assertEquals(FAILURE, disabledConnector.createBank(BANK_NAME, owner).type);
        assertEquals(FAILURE, disabledConnector.bankBalance(BANK_NAME).type);
        assertEquals(FAILURE, disabledConnector.bankHas(BANK_NAME, 1.0).type);
        assertEquals(FAILURE, disabledConnector.bankDeposit(BANK_NAME, 1.0).type);
        assertEquals(FAILURE, disabledConnector.bankWithdraw(BANK_NAME, 1.0).type);
        assertEquals(FAILURE, disabledConnector.isBankOwner(BANK_NAME, owner).type);
        assertEquals(FAILURE, disabledConnector.isBankMember(BANK_NAME, owner).type);
        assertTrue(disabledConnector.getBanks().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OfflinePlayer offlinePlayer(String uuid)
    {
        OfflinePlayer p = mock(OfflinePlayer.class);
        when(p.getUniqueId()).thenReturn(UUID.fromString(uuid));
        return p;
    }
}
