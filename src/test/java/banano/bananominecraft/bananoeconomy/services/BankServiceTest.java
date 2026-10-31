package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
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
    private static final String BANK_ADDR    = "ban_3bankaddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
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
        when(rpc.accountCreate(-1, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(true);

        assertNotNull(service.createBank("treasury",    OWNER_UUID));
        assertNotNull(service.createBank("guild-vault", OWNER_UUID));
        assertNotNull(service.createBank("bank_1",      OWNER_UUID));
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
        when(rpc.accountCreate(-1, BANK_WALLET)).thenReturn(RPC.ACCOUNT_CREATION_FAILED);

        assertNull(service.createBank(BANK_NAME, OWNER_UUID));
        verify(db, never()).createBankRecord(any());
    }

    @Test
    void CreateBankReturnsNullWhenPersistFails()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(false);
        when(rpc.accountCreate(-1, BANK_WALLET)).thenReturn(BANK_ADDR);
        when(db.createBankRecord(any())).thenReturn(false);

        assertNull(service.createBank(BANK_NAME, OWNER_UUID));
        verify(db, never()).addBankMember(anyString(), anyString());
    }

    @Test
    void CreateBankSuccessReturnsBankRecord()
    {
        when(db.bankNameExists(BANK_NAME)).thenReturn(false);
        when(rpc.accountCreate(-1, BANK_WALLET)).thenReturn(BANK_ADDR);
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
        when(rpc.accountCreate(-1, BANK_WALLET)).thenReturn(BANK_ADDR);
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
}
