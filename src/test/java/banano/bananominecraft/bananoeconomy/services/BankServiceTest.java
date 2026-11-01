package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.TransferOutcome;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.db.DuplicateWalletException;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.wallet.BackfillGate;
import banano.bananominecraft.bananoeconomy.wallet.MainThreadGuard;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankServiceTest
{
    private static final String BANK_NAME    = "treasury";
    private static final String BANK_ADDR    = "ban_3bankaddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static final String MASTER_ADDR  = "ban_1masterxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static final String BANK_WALLET  = "BANK-WALLET-ID";
    private static final String OWNER_UUID   = "00000000-0000-0000-0000-000000000001";

    private Plugin       plugin;
    private ConfigEngine configEngine;
    private RPC          rpc;
    private IDBConnector db;
    private BankService  service;

    @BeforeEach
    void setUp()
    {
        plugin       = mock(Plugin.class);
        configEngine = mock(ConfigEngine.class);
        rpc          = mock(RPC.class);
        db           = mock(IDBConnector.class);

        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(configEngine.getBankWalletId()).thenReturn(BANK_WALLET);
        when(rpc.getMasterWallet()).thenReturn(MASTER_ADDR);

        service = new BankService(plugin, configEngine, rpc, db);
    }

    // -------------------------------------------------------------------------
    // isBankingEnabled
    // -------------------------------------------------------------------------

    @Test
    void IsBankingEnabledTrueWhenSeedConfigured()
    {
        when(configEngine.isBankSeedConfigured()).thenReturn(true);
        assertTrue(service.isBankingEnabled());
    }

    @Test
    void IsBankingEnabledFalseWhenSeedNotConfigured()
    {
        when(configEngine.isBankSeedConfigured()).thenReturn(false);
        assertFalse(service.isBankingEnabled());
    }

    // -------------------------------------------------------------------------
    // createBank — name validation
    // -------------------------------------------------------------------------

    @Test
    void CreateBankReturnsNullWhenNameIsNull()
    {
        assertNull(service.createBank(null, OWNER_UUID));
        verify(db, never()).bankNameExists(any());
    }

    @Test
    void CreateBankReturnsNullWhenNameIsEmpty()
    {
        assertNull(service.createBank("", OWNER_UUID));
    }

    @Test
    void CreateBankReturnsNullWhenNameExceedsMaxLength()
    {
        String tooLong = "a".repeat(65);
        assertNull(service.createBank(tooLong, OWNER_UUID));
    }

    @Test
    void CreateBankReturnsNullWhenNameContainsInvalidCharacters()
    {
        assertNull(service.createBank("my bank!", OWNER_UUID));
        assertNull(service.createBank("bank name", OWNER_UUID)); // space
        assertNull(service.createBank("bank/name", OWNER_UUID));
    }

    @Test
    void CreateBankAcceptsAlphanumericHyphenUnderscore()
    {
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        assertNotNull(service.createBank("treasury",    OWNER_UUID));
        assertNotNull(service.createBank("guild-vault", OWNER_UUID));
        assertNotNull(service.createBank("bank_1",      OWNER_UUID));
    }

    private static final String OTHER_BANK_ADDR = "ban_3otherbankxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    @Test
    void CreateBankSkipsAddressAlreadyOwnedByAnotherBank()
    {
        // After the bank wallet is restored from its seed, the node can re-issue an existing bank's address.
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getAllBankNames()).thenReturn(java.util.List.of("old-bank"));
        when(db.getBankRecord("old-bank")).thenReturn(new BankRecord("old-bank", BANK_ADDR, OWNER_UUID, 0L));
        when(db.getMaxBankWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(1, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(rpc.accountCreate(2, BANK_WALLET)).thenReturn(OTHER_BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        BankRecord created = service.createBank("new-bank", OWNER_UUID);

        assertNotNull(created);
        assertEquals(OTHER_BANK_ADDR, created.getAddress());
        assertEquals(2, created.getWalletIndex(), "the skipped index must not be reused or recorded");
    }

    @Test
    void CreateBankUsesExplicitIndexOneAboveHighestStoredBankIndex()
    {
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenReturn(7);
        when(rpc.accountCreate(8, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        BankRecord created = service.createBank("new-bank", OWNER_UUID);

        assertNotNull(created);
        assertEquals(8, created.getWalletIndex());
        verify(rpc, never()).accountCreate(eq(-1), anyString());
    }

    @Test
    void CreateBankFirstIndexIsOne()
    {
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(1, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        assertEquals(1, service.createBank("new-bank", OWNER_UUID).getWalletIndex());
    }

    @Test
    void CreateBankReturnsNullWhenMaxIndexUnreadable()
    {
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenThrow(new IllegalStateException("Database unavailable"));

        assertNull(service.createBank("new-bank", OWNER_UUID));
        verify(rpc, never()).accountCreate(anyInt(), anyString());
    }

    @Test
    void CreateBankConcurrentCreationsNeverShareAnIndex() throws Exception
    {
        java.util.concurrent.atomic.AtomicInteger storedMax = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<Integer> issued = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenAnswer(inv -> storedMax.get());
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenAnswer(inv ->
        {
            Thread.sleep(20); // widen the race window
            int index = inv.getArgument(0);
            return "ban_3" + String.valueOf("13456789abcdefghijkmnopqrstuwxyz".charAt(index % 32)).repeat(59);
        });
        when(db.createBankRecord(any())).thenAnswer(inv ->
        {
            BankRecord record = inv.getArgument(0);
            issued.add(record.getWalletIndex());
            storedMax.accumulateAndGet(record.getWalletIndex(), Math::max);
            return true;
        });

        int creations = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(creations);
        java.util.List<java.util.concurrent.Future<BankRecord>> results = new java.util.ArrayList<>();
        for (int i = 0; i < creations; i++)
        {
            final String bankName = "bank" + i;
            results.add(pool.submit(() -> service.createBank(bankName, OWNER_UUID)));
        }
        for (java.util.concurrent.Future<BankRecord> result : results)
        {
            assertNotNull(result.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertEquals(creations, new java.util.HashSet<>(issued).size(), "indices must be unique: " + issued);
    }

    @Test
    void CreateBankRejectsInvalidNodeAddress()
    {
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenReturn("not-an-address");

        assertNull(service.createBank("treasury", OWNER_UUID));
        verify(db, never()).createBankRecord(any());
    }

    // -------------------------------------------------------------------------
    // createBank — other failures
    // -------------------------------------------------------------------------

    @Test
    void CreateBankReturnsNullWhenNameAlreadyExists()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(true);

        assertNull(service.createBank(BANK_NAME, OWNER_UUID));
        verify(rpc, never()).accountCreate(anyInt(), anyString());
    }

    @Test
    void CreateBankReturnsNullWhenRPCFails()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(false);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenReturn(RPC.ACCOUNT_CREATION_FAILED);

        assertNull(service.createBank(BANK_NAME, OWNER_UUID));
        verify(db, never()).createBankRecord(any());
    }

    @Test
    void CreateBankReturnsNullWhenPersistFails()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(false);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(false);

        assertNull(service.createBank(BANK_NAME, OWNER_UUID));
        verify(db, never()).addBankMember(anyString(), anyString());
    }

    @Test
    void CreateBankSuccessReturnsBankRecord()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(false);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        BankRecord result = service.createBank(BANK_NAME, OWNER_UUID);

        assertNotNull(result);
        assertEquals(BANK_NAME, result.getBankName());
        assertEquals(BANK_ADDR, result.getAddress());
        assertEquals(OWNER_UUID, result.getOwnerUuid());
    }

    @Test
    void CreateBankSuccessAddsOwnerAsMember()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(false);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        service.createBank(BANK_NAME, OWNER_UUID);

        verify(db).addBankMember(BANK_NAME, OWNER_UUID);
    }

    // -------------------------------------------------------------------------
    // bankExists
    // -------------------------------------------------------------------------

    @Test
    void BankExistsDelegatesToDB()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(true);
        assertTrue(service.bankExists(BANK_NAME));

        when(db.bankNameExists("nonexistent")).thenReturn(false);
        assertFalse(service.bankExists("nonexistent"));
    }

    // -------------------------------------------------------------------------
    // getBankBalance
    // -------------------------------------------------------------------------

    @Test
    void GetBankBalanceReturnsZeroWhenBankNotFound()
    {
        when(db.getBankRecord(BANK_NAME)).thenReturn(null);
        assertEquals(0.0, service.getBankBalance(BANK_NAME));
    }

    @Test
    void GetBankBalanceQueriesRPCWithBankAddress()
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(250.0);

        assertEquals(250.0, service.getBankBalance(BANK_NAME));
    }

    // -------------------------------------------------------------------------
    // bankHas
    // -------------------------------------------------------------------------

    @Test
    void BankHasTrueWhenSufficientBalance()
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0);

        assertTrue(service.bankHas(BANK_NAME, 100.0));
        assertTrue(service.bankHas(BANK_NAME, 50.0));
    }

    @Test
    void BankHasFalseWhenInsufficientBalance()
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(30.0);

        assertFalse(service.bankHas(BANK_NAME, 31.0));
    }

    // -------------------------------------------------------------------------
    // deposit (master wallet → bank)
    // -------------------------------------------------------------------------

    @Test
    void DepositReturnsFalseWhenBankNotFound()
    {
        when(db.getBankRecord(BANK_NAME)).thenReturn(null);
        assertFalse(service.deposit(BANK_NAME, 50.0));
    }

    @Test
    void DepositReturnsFalseWhenMasterWalletInsufficient() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(10.0);

        assertFalse(service.deposit(BANK_NAME, 50.0));
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
    }

    @Test
    void DepositSendsFromMasterToBank() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("block1");

        assertTrue(service.deposit(BANK_NAME, 50.0));
        // Must use the default (master wallet ID) overload — not the bank wallet ID.
        verify(rpc).sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0);
    }

    @Test
    void DepositReturnsFalseWhenTransactionThrows() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0))
                .thenThrow(new TransactionError("Node error"));

        assertFalse(service.deposit(BANK_NAME, 50.0));
    }

    // -------------------------------------------------------------------------
    // withdraw (bank → master wallet)
    // -------------------------------------------------------------------------

    @Test
    void WithdrawReturnsFalseWhenBankNotFound()
    {
        when(db.getBankRecord(BANK_NAME)).thenReturn(null);
        assertFalse(service.withdraw(BANK_NAME, 50.0));
    }

    @Test
    void WithdrawReturnsFalseWhenBankInsufficientBalance() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(20.0);

        assertFalse(service.withdraw(BANK_NAME, 50.0));
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble(), anyString());
    }

    @Test
    void WithdrawSendsFromBankToMasterUsingBankWalletId() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0);
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 40.0, BANK_WALLET)).thenReturn("block2");

        assertTrue(service.withdraw(BANK_NAME, 40.0));
        // Must sign with the bank wallet ID, not the master wallet ID.
        verify(rpc).sendTransaction(BANK_ADDR, MASTER_ADDR, 40.0, BANK_WALLET);
    }

    @Test
    void WithdrawReturnsFalseWhenTransactionThrows() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0);
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 40.0, BANK_WALLET))
                .thenThrow(new TransactionError("Timeout"));

        assertFalse(service.withdraw(BANK_NAME, 40.0));
    }

    @Test
    void DepositReceivesBlockIntoBankUsingBankWalletId() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("block1");

        assertTrue(service.deposit(BANK_NAME, 50.0));
        verify(rpc).receiveBlock(BANK_WALLET, BANK_ADDR, "block1");
    }

    @Test
    void DepositStillSucceedsWhenReceiveFails() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("block1");
        when(rpc.receiveBlock(BANK_WALLET, BANK_ADDR, "block1")).thenThrow(new TransactionError("x"));

        assertTrue(service.deposit(BANK_NAME, 50.0));
    }

    @Test
    void WithdrawReceivesBlockIntoMasterWallet() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0);
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 40.0, BANK_WALLET)).thenReturn("block2");

        assertTrue(service.withdraw(BANK_NAME, 40.0));
        verify(rpc).receiveBlock(MASTER_ADDR, "block2");
    }

    @Test
    void DepositAndWithdrawRejectNonFiniteAmounts() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(anyString())).thenReturn(Double.POSITIVE_INFINITY);

        assertFalse(service.deposit(BANK_NAME, Double.NaN));
        assertFalse(service.deposit(BANK_NAME, Double.POSITIVE_INFINITY));
        assertFalse(service.withdraw(BANK_NAME, Double.NaN));
        assertFalse(service.withdraw(BANK_NAME, Double.POSITIVE_INFINITY));
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble());
        verify(rpc, never()).sendTransaction(anyString(), anyString(), anyDouble(), anyString());
    }

    // -------------------------------------------------------------------------
    // getBanks / isBankOwner / isBankMember
    // -------------------------------------------------------------------------

    @Test
    void GetBanksDelegatesToDB()
    {
        when(db.getAllBankNames()).thenReturn(List.of("treasury", "guild"));
        assertEquals(List.of("treasury", "guild"), service.getBanks());
    }

    @Test
    void IsBankOwnerDelegatesToDB()
    {
        when(db.isBankOwner(BANK_NAME, OWNER_UUID)).thenReturn(true);
        assertTrue(service.isBankOwner(BANK_NAME, OWNER_UUID));

        when(db.isBankOwner(BANK_NAME, "other-uuid")).thenReturn(false);
        assertFalse(service.isBankOwner(BANK_NAME, "other-uuid"));
    }

    @Test
    void IsBankMemberDelegatesToDB()
    {
        when(db.isBankMember(BANK_NAME, OWNER_UUID)).thenReturn(true);
        assertTrue(service.isBankMember(BANK_NAME, OWNER_UUID));

        when(db.isBankMember(BANK_NAME, "stranger-uuid")).thenReturn(false);
        assertFalse(service.isBankMember(BANK_NAME, "stranger-uuid"));
    }

    // -------------------------------------------------------------------------
    // Balance cache behaviour
    // -------------------------------------------------------------------------

    @Test
    void GetBankBalanceFetchesFromRPCOnCacheMiss()
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(77.0);

        assertEquals(77.0, service.getBankBalance(BANK_NAME));
        verify(rpc, times(1)).getBalance(BANK_ADDR);
    }

    @Test
    void GetBankBalanceReturnsCachedValueOnSubsequentReads()
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(99.0);

        service.getBankBalance(BANK_NAME); // populates cache
        service.getBankBalance(BANK_NAME); // should hit cache
        service.getBankBalance(BANK_NAME); // should hit cache

        // RPC must only be called once despite three reads.
        verify(rpc, times(1)).getBalance(BANK_ADDR);
    }

    @Test
    void DepositInvalidatesCacheSoNextReadFetchesFresh() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0, 150.0); // pre-cache, then post-deposit fresh value
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("blk");

        service.getBankBalance(BANK_NAME); // 100.0 cached
        service.deposit(BANK_NAME, 50.0);  // invalidates cache
        double afterDeposit = service.getBankBalance(BANK_NAME); // fetches fresh 150.0

        assertEquals(150.0, afterDeposit);
        verify(rpc, times(2)).getBalance(BANK_ADDR); // once pre-deposit, once post-deposit
    }

    @Test
    void WithdrawInvalidatesCacheSoNextReadFetchesFresh() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        // calls: (1) getBankBalance pre-warm, (2) withdraw sufficiency check, (3) getBankBalance post-withdraw
        when(rpc.getBalance(BANK_ADDR)).thenReturn(200.0, 200.0, 150.0);
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 50.0, BANK_WALLET)).thenReturn("blk");

        service.getBankBalance(BANK_NAME);  // 200.0 cached
        service.withdraw(BANK_NAME, 50.0);  // sufficiency check (200) then invalidates cache
        double afterWithdraw = service.getBankBalance(BANK_NAME); // fetches fresh 150.0

        assertEquals(150.0, afterWithdraw);
        // Three RPC calls: pre-warm, sufficiency check, post-withdraw fresh read.
        verify(rpc, times(3)).getBalance(BANK_ADDR);
    }

    @Test
    void WithdrawUpdatesCacheWithLiveBalanceOnInsufficientFunds()
    {
        // If the cached balance is stale-high and the live balance is too low to cover
        // the withdrawal, the cache must be updated with the live value so subsequent
        // reads don't return the stale amount.
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(200.0, 30.0); // pre-warm: 200, live: 30

        service.getBankBalance(BANK_NAME);          // 200.0 cached
        boolean result = service.withdraw(BANK_NAME, 100.0); // live balance 30 < 100 → fails

        assertFalse(result);
        // The next read must return the fresh 30.0, not the stale 200.0.
        // The cache was updated with 30.0 during the failed withdrawal — no extra RPC call.
        double afterFailedWithdraw = service.getBankBalance(BANK_NAME);
        assertEquals(30.0, afterFailedWithdraw);
        verify(rpc, times(2)).getBalance(BANK_ADDR); // pre-warm + sufficiency check only
    }

    // -------------------------------------------------------------------------
    // Unknown outcome (light mode): reported as success, never as failure
    // -------------------------------------------------------------------------

    /** Captures SEVERE records logged by the plugin's logger. */
    private java.util.List<java.util.logging.LogRecord> captureSevere()
    {
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        Logger logger = Logger.getLogger("bank-unknown-test");
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler()
        {
            @Override public void publish(java.util.logging.LogRecord r)
            {
                if (r.getLevel() == java.util.logging.Level.SEVERE) records.add(r);
            }
            @Override public void flush() {}
            @Override public void close() {}
        });
        when(plugin.getLogger()).thenReturn(logger);
        return records;
    }

    @Test
    void DepositUnknownOutcomeIsSuccess_logsSevere_andSkipsReceive() throws TransactionError
    {
        java.util.List<java.util.logging.LogRecord> severe = captureSevere();
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenThrow(new UnknownOutcomeError());

        assertEquals(TransferOutcome.UNKNOWN, service.depositOutcome(BANK_NAME, 50.0));
        assertTrue(service.deposit(BANK_NAME, 50.0), "boolean API must not report an unknown outcome as failure");

        verify(rpc, never()).receiveBlock(anyString(), anyString(), anyString());
        assertFalse(severe.isEmpty());
        String message = severe.get(0).getMessage();
        assertTrue(message.contains(BANK_NAME) && message.contains("50.0") && message.contains("deposit")
                && message.contains("outcome unknown \u2013 reconcile with /history"), message);
    }

    @Test
    void WithdrawUnknownOutcomeIsSuccess_logsSevere() throws TransactionError
    {
        java.util.List<java.util.logging.LogRecord> severe = captureSevere();
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(BANK_ADDR)).thenReturn(100.0);
        when(rpc.sendTransaction(BANK_ADDR, MASTER_ADDR, 40.0, BANK_WALLET)).thenThrow(new UnknownOutcomeError());

        assertEquals(TransferOutcome.UNKNOWN, service.withdrawOutcome(BANK_NAME, 40.0));
        assertTrue(service.withdraw(BANK_NAME, 40.0));
        assertFalse(severe.isEmpty());
        assertTrue(severe.get(0).getMessage().contains("withdrawal"), severe.get(0).getMessage());
    }

    @Test
    void PlainTransactionErrorStillFails_outcomeIsFailedNotUnknown() throws TransactionError
    {
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenThrow(new TransactionError("rejected"));

        assertEquals(TransferOutcome.FAILED, service.depositOutcome(BANK_NAME, 50.0));
    }

    // -------------------------------------------------------------------------
    // Light mode: the (slow) auto-receive never runs on the primary thread
    // -------------------------------------------------------------------------

    @Test
    void LightModeAutoReceiveOnPrimaryThreadIsScheduledAsync() throws TransactionError
    {
        java.util.List<Runnable> scheduled = new java.util.ArrayList<>();
        service.setThreadGuard(new MainThreadGuard(() -> true, scheduled::add));
        when(configEngine.isLightMode()).thenReturn(true);
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("block1");

        assertTrue(service.deposit(BANK_NAME, 50.0));

        verify(rpc, never()).receiveBlock(anyString(), anyString(), anyString());
        assertEquals(1, scheduled.size());
        scheduled.get(0).run();
        verify(rpc).receiveBlock(BANK_WALLET, BANK_ADDR, "block1");
    }

    @Test
    void LightModeAutoReceiveOffPrimaryThreadRunsInline() throws TransactionError
    {
        java.util.List<Runnable> scheduled = new java.util.ArrayList<>();
        service.setThreadGuard(new MainThreadGuard(() -> false, scheduled::add));
        when(configEngine.isLightMode()).thenReturn(true);
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("block1");

        assertTrue(service.deposit(BANK_NAME, 50.0));

        assertTrue(scheduled.isEmpty());
        verify(rpc).receiveBlock(BANK_WALLET, BANK_ADDR, "block1");
    }

    @Test
    void NodeModeAutoReceiveIsAlwaysInline() throws TransactionError
    {
        java.util.List<Runnable> scheduled = new java.util.ArrayList<>();
        service.setThreadGuard(new MainThreadGuard(() -> true, scheduled::add));
        when(configEngine.isLightMode()).thenReturn(false);
        BankRecord bank = new BankRecord(BANK_NAME, BANK_ADDR, OWNER_UUID, 0);
        when(db.getBankRecord(BANK_NAME)).thenReturn(bank);
        when(rpc.getBalance(MASTER_ADDR)).thenReturn(500.0);
        when(rpc.sendTransaction(MASTER_ADDR, BANK_ADDR, 50.0)).thenReturn("block1");

        assertTrue(service.deposit(BANK_NAME, 50.0));

        assertTrue(scheduled.isEmpty());
        verify(rpc).receiveBlock(BANK_WALLET, BANK_ADDR, "block1");
    }

    // -------------------------------------------------------------------------
    // createBank: backfill gate, duplicate-key handling, long skip runs
    // -------------------------------------------------------------------------

    @Test
    void CreateBankWaitsForBackfill_andFailsWithoutAllocatingWhenItDoesNotFinish()
    {
        BackfillGate closed = new BackfillGate(false, MainThreadGuard.neverPrimary());
        service.setBackfillGate(closed, 50L);
        when(db.bankNameExists(anyString())).thenReturn(false);

        assertNull(service.createBank("new-bank", OWNER_UUID));
        assertTrue(service.isWalletSetupPending());
        verify(db, never()).getMaxBankWalletIndex();
        verify(rpc, never()).accountCreate(anyInt(), anyString());
    }

    @Test
    void CreateBankProceedsOnceBackfillHasFinished()
    {
        BackfillGate gate = new BackfillGate(false, MainThreadGuard.neverPrimary());
        service.setBackfillGate(gate, 5_000L);
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(1, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        new Thread(() ->
        {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            gate.open();
        }).start();

        assertNotNull(service.createBank("new-bank", OWNER_UUID));
        assertFalse(service.isWalletSetupPending());
    }

    @Test
    void CreateBankOnPrimaryThreadNeverBlocksOnTheGate()
    {
        service.setBackfillGate(new BackfillGate(false, new MainThreadGuard(() -> true, Runnable::run)), 60_000L);
        when(db.bankNameExists(anyString())).thenReturn(false);

        long start = System.nanoTime();
        assertNull(service.createBank("new-bank", OWNER_UUID));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000, "must return immediately on the primary thread");
    }

    @Test
    void CreateBankDuplicateKeyMeansIndexTaken_triesNextIndex()
    {
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(1, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(rpc.accountCreate(2, BANK_WALLET)).thenReturn(OTHER_BANK_ADDR);
        when(db.createBankRecord(argThat(b -> b != null && b.getWalletIndex() == 1)))
                .thenThrow(new DuplicateWalletException("taken by another server", null));
        when(db.createBankRecord(argThat(b -> b != null && b.getWalletIndex() == 2))).thenReturn(true);

        BankRecord created = service.createBank("new-bank", OWNER_UUID);

        assertNotNull(created);
        assertEquals(2, created.getWalletIndex());
        assertEquals(OTHER_BANK_ADDR, created.getAddress());
        verify(db).addBankMember("new-bank", OWNER_UUID);
    }

    @Test
    void CreateBankKeepsSkippingTakenAddressesBeyondTenAttempts()
    {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++)
        {
            String name = "legacy" + i;
            names.add(name);
            when(db.getBankRecord(name)).thenReturn(new BankRecord(name, addr(i), OWNER_UUID, 0L));
        }
        when(db.getAllBankNames()).thenReturn(names);
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenAnswer(inv -> addr(inv.getArgument(0)));
        when(db.createBankRecord(any())).thenReturn(true);

        BankRecord created = service.createBank("new-bank", OWNER_UUID);

        assertNotNull(created, "15 consecutive taken addresses must not abort creation");
        assertEquals(16, created.getWalletIndex());
    }

    @Test
    void CreateBankGivesUpAfterWalletIndexScanLimit()
    {
        when(configEngine.getWalletIndexScanLimit()).thenReturn(3);
        when(db.bankNameExists(anyString())).thenReturn(false);
        when(db.getMaxBankWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(anyInt(), eq(BANK_WALLET))).thenAnswer(inv -> addr(inv.getArgument(0)));
        when(db.createBankRecord(any())).thenThrow(new DuplicateWalletException("taken", null));

        assertNull(service.createBank("new-bank", OWNER_UUID));
        verify(db, times(3)).createBankRecord(any());
    }

    private static String addr(int i)
    {
        String alphabet = "13456789abcdefghijkmnopqrstuwxyz";
        return "ban_3" + String.valueOf(alphabet.charAt(i % 32)).repeat(58) + alphabet.charAt(i / 32 % 32);
    }
}
