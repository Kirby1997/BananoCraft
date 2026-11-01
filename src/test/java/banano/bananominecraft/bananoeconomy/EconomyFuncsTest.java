package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.classes.TransferOutcome;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.db.DuplicateWalletException;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.wallet.BackfillGate;
import banano.bananominecraft.bananoeconomy.wallet.MainThreadGuard;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class EconomyFuncsTest {

    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";
    private static final String MASTER_WALLET = "ban_1masterwalletaddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx1111";

    @Mock private IDBConnector db;
    @Mock private Plugin plugin;
    @Mock private RPC rpc;
    @Mock private Player player;
    @Mock private OfflinePlayer offlinePlayer;
    @Mock private ConfigEngine configEngine;

    private EconomyFuncs economyFuncs;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        economyFuncs = new EconomyFuncs(plugin, db, rpc, configEngine);
    }

    // --- getBalance(Player) ---

    @Test
    void getBalance_player_returnsZero_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertEquals(0d, economyFuncs.getBalance(player));
    }

    @Test
    void getBalance_player_returnsZero_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(player));
    }

    @Test
    void getBalance_player_returnsZero_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", null, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(player));
    }

    @Test
    void getBalance_player_delegatesToRPC() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(42.0);

        assertEquals(42.0, economyFuncs.getBalance(player));
    }

    // --- getBalance(OfflinePlayer) ---

    @Test
    void getBalance_offlinePlayer_returnsZero_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertEquals(0d, economyFuncs.getBalance(offlinePlayer));
    }

    @Test
    void getBalance_offlinePlayer_returnsZero_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(offlinePlayer));
    }

    @Test
    void getBalance_offlinePlayer_delegatesToRPC() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(7.5);

        assertEquals(7.5, economyFuncs.getBalance(offlinePlayer));
    }

    // --- isFrozen(Player) ---

    @Test
    void isFrozen_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.isFrozen(player));
    }

    @Test
    void isFrozen_returnsTrue_whenRecordIsFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertTrue(economyFuncs.isFrozen(player));
    }

    @Test
    void isFrozen_returnsFalse_whenRecordIsNotFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertFalse(economyFuncs.isFrozen(player));
    }

    // --- hasWallet(Player) ---

    @Test
    void hasWallet_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.hasWallet(player));
    }

    @Test
    void hasWallet_player_returnsFalse_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", null, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertFalse(economyFuncs.hasWallet(player));
    }

    @Test
    void hasWallet_player_returnsTrue_whenWalletStartsWithBan() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertTrue(economyFuncs.hasWallet(player));
    }

    // --- removeBalanceFP(OfflinePlayer, double) ---

    @Test
    void removeBalanceFP_offline_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsFalse_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsFalse_whenInsufficientBalance() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(3.0);

        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsTrue_onSuccessfulTransaction() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("block123");

        assertTrue(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));

        verify(rpc).receiveBlock(MASTER_WALLET, "block123");
    }

    @Test
    void removeBalanceFP_offline_stillReturnsTrue_whenAutoReceiveFails() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("block123");
        when(rpc.receiveBlock(MASTER_WALLET, "block123")).thenThrow(new TransactionError("Block not found"));

        // The send itself already succeeded, so a failed auto-receive is not reported as a failure.
        assertTrue(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    @Test
    void removeBalanceFP_offline_returnsFalse_whenTransactionThrows() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenThrow(new TransactionError("Node error"));

        assertFalse(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
    }

    // --- addBalanceTP(OfflinePlayer, double) ---

    @Test
    void addBalanceTP_offline_returnsFalse_whenNoRecord() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsFalse_whenServerInsufficientFunds() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(2.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsTrue_onSuccessfulTransaction() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("block456");

        assertTrue(economyFuncs.addBalanceTP(offlinePlayer, 5.0));

        verify(rpc).receiveBlock(WALLET, "block456");
    }

    @Test
    void addBalanceTP_offline_stillReturnsTrue_whenAutoReceiveFails() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("block456");
        when(rpc.receiveBlock(WALLET, "block456")).thenThrow(new TransactionError("Block not found"));

        assertTrue(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsFalse_whenFrozen() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void addBalanceTP_offline_returnsFalse_whenTransactionThrows() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new TransactionError("Node error"));

        assertFalse(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    // --- getBalance(OfflinePlayer) missing case ---

    @Test
    void getBalance_offlinePlayer_returnsZero_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", null, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertEquals(0d, economyFuncs.getBalance(offlinePlayer));
    }

    // --- hasWallet(OfflinePlayer) ---

    @Test
    void hasWallet_offlinePlayer_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.hasWallet(offlinePlayer));
    }

    @Test
    void hasWallet_offlinePlayer_returnsFalse_whenNullWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", null, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertFalse(economyFuncs.hasWallet(offlinePlayer));
    }

    @Test
    void hasWallet_offlinePlayer_returnsTrue_whenValidWallet() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        assertTrue(economyFuncs.hasWallet(offlinePlayer));
    }

    // --- getWallet(Player) ---

    @Test
    void getWallet_returnsEmpty_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertEquals("", economyFuncs.getWallet(player));
    }

    @Test
    void getWallet_returnsWalletAddress_whenRecordExists() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        assertEquals(WALLET, economyFuncs.getWallet(player));
    }

    // --- removeBalanceFP(Player, double) ---

    @Test
    void removeBalanceFP_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsFalse_whenFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        // getBalance also calls getPlayerRecord; frozen → returns 0.0 from getBalance
        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsFalse_whenInsufficientBalance() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(3.0);  // balance < amount

        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsTrue_onSuccessfulTransaction() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("blockABC");

        assertTrue(economyFuncs.removeBalanceFP(player, 5.0));

        verify(rpc).receiveBlock(MASTER_WALLET, "blockABC");
    }

    @Test
    void removeBalanceFP_player_stillReturnsTrue_whenAutoReceiveFails() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("blockABC");
        when(rpc.receiveBlock(MASTER_WALLET, "blockABC")).thenThrow(new TransactionError("Block not found"));

        assertTrue(economyFuncs.removeBalanceFP(player, 5.0));
    }

    @Test
    void removeBalanceFP_player_returnsFalse_whenTransactionThrows() throws Exception {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenThrow(new TransactionError("Network error"));

        assertFalse(economyFuncs.removeBalanceFP(player, 5.0));
    }

    // --- addBalanceTP(Player, double) ---

    @Test
    void addBalanceTP_player_returnsFalse_whenNoRecord() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(null);

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsFalse_whenServerInsufficientFunds() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(2.0);  // less than 5.0
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsFalse_whenFrozen() {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        // Record is frozen; isFrozen() will call db.getPlayerRecord(player) a second time
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsTrue_onSuccessfulTransaction() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("blockDEF");

        assertTrue(economyFuncs.addBalanceTP(player, 5.0));

        verify(rpc).receiveBlock(WALLET, "blockDEF");
    }

    @Test
    void addBalanceTP_player_stillReturnsTrue_whenAutoReceiveFails() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("blockDEF");
        when(rpc.receiveBlock(WALLET, "blockDEF")).thenThrow(new TransactionError("Block not found"));

        assertTrue(economyFuncs.addBalanceTP(player, 5.0));
    }

    @Test
    void addBalanceTP_player_returnsFalse_whenTransactionThrows() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new TransactionError("Timeout"));

        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    // --- getTransactionHistory(UUID, int) ---

    @Test
    void getTransactionHistory_returnsNull_whenNoRecord() {
        UUID id = UUID.randomUUID();
        when(db.getPlayerRecord(id)).thenReturn(null);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        assertNull(economyFuncs.getTransactionHistory(id, 10));
    }

    @Test
    void getTransactionHistory_returnsNull_whenFrozen() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        assertNull(economyFuncs.getTransactionHistory(id, 10));
    }

    @Test
    void getTransactionHistory_returnsNull_whenCountNegative() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        assertNull(economyFuncs.getTransactionHistory(id, -1));
    }

    @Test
    void getTransactionHistory_returnsNull_whenCountExceedsMax() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(50);

        assertNull(economyFuncs.getTransactionHistory(id, 51));
    }

    @Test
    void getTransactionHistory_delegatesToRPC() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);

        List<TransactionRecord> expected = List.of(
                new TransactionRecord(LocalDateTime.now(), WALLET, TransactionDirection.Receive, 5.0, "abc123def456abc1", false)
        );
        when(rpc.getTransactionHistory(WALLET, 10)).thenReturn(expected);

        List<TransactionRecord> result = economyFuncs.getTransactionHistory(id, 10);
        assertEquals(expected, result);
    }

    @Test
    void getTransactionHistory_returnsNull_whenRPCThrows() {
        UUID id = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(id.toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(id)).thenReturn(record);
        when(configEngine.getMaximumTransactionHistoryCount()).thenReturn(100);
        when(rpc.getTransactionHistory(WALLET, 10)).thenThrow(new RuntimeException("Node unreachable"));

        assertNull(economyFuncs.getTransactionHistory(id, 10));
    }

    // --- freezePlayer(Player) ---

    @Test
    void freezePlayer_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.freezePlayer(player));
    }

    @Test
    void freezePlayer_player_returnsTrue_andMarksRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(db.updatePlayerRecord(record)).thenReturn(true);

        assertTrue(economyFuncs.freezePlayer(player));
        assertTrue(record.isFrozen(), "Record should be marked frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- freezePlayer(OfflinePlayer) ---

    @Test
    void freezePlayer_offlinePlayer_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.freezePlayer(offlinePlayer));
    }

    @Test
    void freezePlayer_offlinePlayer_returnsTrue_andMarksRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(db.updatePlayerRecord(record)).thenReturn(true);

        assertTrue(economyFuncs.freezePlayer(offlinePlayer));
        assertTrue(record.isFrozen(), "Record should be marked frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- unfreezePlayer(Player) ---

    @Test
    void unfreezePlayer_player_returnsFalse_whenNoRecord() {
        when(db.getPlayerRecord(player)).thenReturn(null);
        assertFalse(economyFuncs.unfreezePlayer(player));
    }

    @Test
    void unfreezePlayer_player_returnsTrue_andClearsRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(db.updatePlayerRecord(record)).thenReturn(true);

        assertTrue(economyFuncs.unfreezePlayer(player));
        assertFalse(record.isFrozen(), "Record should no longer be frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- unfreezePlayer(OfflinePlayer) ---

    @Test
    void unfreezePlayer_offlinePlayer_returnsFalse_whenNoRecord() {
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(null);
        assertFalse(economyFuncs.unfreezePlayer(offlinePlayer));
    }

    @Test
    void unfreezePlayer_offlinePlayer_returnsTrue_andClearsRecordFrozen() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(db.updatePlayerRecord(record)).thenReturn(true);

        assertTrue(economyFuncs.unfreezePlayer(offlinePlayer));
        assertFalse(record.isFrozen(), "Record should no longer be frozen after call");
        verify(db).updatePlayerRecord(record);
    }

    // --- freeze/unfreeze persistence failures ---

    @Test
    void freezePlayer_player_returnsFalse_andRollsBack_whenPersistFails() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(db.updatePlayerRecord(record)).thenReturn(false);

        assertFalse(economyFuncs.freezePlayer(player));
        assertFalse(record.isFrozen(), "In-memory frozen flag must be rolled back on DB failure");
    }

    @Test
    void unfreezePlayer_offlinePlayer_returnsFalse_andRollsBack_whenPersistFails() {
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Bob", WALLET, true);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(db.updatePlayerRecord(record)).thenReturn(false);

        assertFalse(economyFuncs.unfreezePlayer(offlinePlayer));
        assertTrue(record.isFrozen(), "In-memory frozen flag must be rolled back on DB failure");
    }

    // --- accountCreate(Player) ---

    @Test
    void accountCreate_newPlayer_createsRecord_whenWalletValid() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(rpc.accountCreate(anyInt())).thenReturn(WALLET);
        when(db.isAlreadyAssignedToOtherPlayer(WALLET, player)).thenReturn(false);
        when(db.createPlayerRecord(player, WALLET, 1))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));

        assertTrue(economyFuncs.accountCreate(player));
        verify(db).createPlayerRecord(player, WALLET, 1);
        verify(rpc).accountCreate(1);
    }

    @Test
    void accountCreate_databaseUnavailable_createsNoNodeAccount() {
        // A DB that cannot answer must not be read as "no record": that would issue an
        // orphaned node account on every join during an outage.
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenThrow(new IllegalStateException("Database unavailable"));

        assertFalse(economyFuncs.accountCreate(player));
        verify(rpc, never()).accountCreate(anyInt());
    }

    @Test
    void accountCreate_newPlayer_returnsFalse_whenCreationFails() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(rpc.accountCreate(anyInt())).thenReturn(RPC.ACCOUNT_CREATION_FAILED);

        assertFalse(economyFuncs.accountCreate(player));
        verify(db, never()).createPlayerRecord(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_newPlayer_returnsFalse_whenRetryReturnsSentinel() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(rpc.accountCreate(anyInt())).thenReturn(WALLET, RPC.ACCOUNT_CREATION_FAILED);
        when(db.isAlreadyAssignedToOtherPlayer(WALLET, player)).thenReturn(true);

        assertFalse(economyFuncs.accountCreate(player));
        verify(db, never()).createPlayerRecord(any(Player.class), anyString(), any());
        verify(db, never()).isAlreadyAssignedToOtherPlayer(eq(RPC.ACCOUNT_CREATION_FAILED), any(Player.class));
    }

    @Test
    void accountCreate_newPlayer_returnsFalse_whenAddressInvalid() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(rpc.accountCreate(anyInt())).thenReturn("not-an-address");

        assertFalse(economyFuncs.accountCreate(player));
        verify(db, never()).createPlayerRecord(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_existingValidRecord_doesNotCreateAccount() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(true);
        when(db.getPlayerRecord(player))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));

        assertTrue(economyFuncs.accountCreate(player));
        verify(rpc, never()).accountCreate(anyInt());
        verify(db, never()).updatePlayerWallet(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_existingSentinelRecord_isRepaired() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(true);
        when(db.getPlayerRecord(player)).thenReturn(
                new PlayerRecord(UUID.randomUUID().toString(), "Alice", RPC.ACCOUNT_CREATION_FAILED, false));
        when(rpc.accountCreate(anyInt())).thenReturn(WALLET);
        when(db.isAlreadyAssignedToOtherPlayer(WALLET, player)).thenReturn(false);
        when(db.updatePlayerWallet(player, WALLET, 1)).thenReturn(true);

        assertTrue(economyFuncs.accountCreate(player));
        verify(db).updatePlayerWallet(player, WALLET, 1);
        verify(db, never()).createPlayerRecord(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_existingSentinelRecord_returnsFalse_whenRepairCreationFails() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(true);
        when(db.getPlayerRecord(player)).thenReturn(
                new PlayerRecord(UUID.randomUUID().toString(), "Alice", RPC.ACCOUNT_CREATION_FAILED, false));
        when(rpc.accountCreate(anyInt())).thenReturn(RPC.ACCOUNT_CREATION_FAILED);

        assertFalse(economyFuncs.accountCreate(player));
        verify(db, never()).updatePlayerWallet(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_existingSentinelRecord_returnsFalse_whenPersistFails() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(true);
        when(db.getPlayerRecord(player)).thenReturn(
                new PlayerRecord(UUID.randomUUID().toString(), "Alice", RPC.ACCOUNT_CREATION_FAILED, false));
        when(rpc.accountCreate(anyInt())).thenReturn(WALLET);
        when(db.isAlreadyAssignedToOtherPlayer(WALLET, player)).thenReturn(false);
        when(db.updatePlayerWallet(player, WALLET, 1)).thenReturn(false);

        assertFalse(economyFuncs.accountCreate(player));
    }

    private static final String ALPHABET = "13456789abcdefghijkmnopqrstuwxyz";

    /** A Validator-valid ban_ address that is unique per index (test fake for account derivation). */
    private static String addressForIndex(int index) {
        StringBuilder sb = new StringBuilder("ban_1");
        for (int i = 0; i < 59; i++) {
            sb.append(ALPHABET.charAt((index + i * 7) % ALPHABET.length()));
        }
        return sb.toString();
    }

    @Test
    void accountCreate_newPlayer_usesExplicitIndex_maxStoredPlusOne() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(41);
        when(rpc.accountCreate(42)).thenReturn(WALLET);
        when(db.createPlayerRecord(player, WALLET, 42))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false, 42));

        assertTrue(economyFuncs.accountCreate(player));
        verify(rpc).accountCreate(42);
        verify(rpc, never()).accountCreate(-1);
        verify(db).createPlayerRecord(player, WALLET, 42);
    }

    @Test
    void accountCreate_newPlayer_firstEverIndexIsOne_neverTheMasterIndexZero() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(1)).thenReturn(WALLET);
        when(db.createPlayerRecord(player, WALLET, 1))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false, 1));

        assertTrue(economyFuncs.accountCreate(player));
        verify(rpc, never()).accountCreate(0);
    }

    @Test
    void accountCreate_newPlayer_triesNextIndex_whenAddressAlreadyAssigned() {
        String taken = addressForIndex(5);
        String free = addressForIndex(6);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(4);
        when(rpc.accountCreate(5)).thenReturn(taken);
        when(rpc.accountCreate(6)).thenReturn(free);
        when(db.isAlreadyAssignedToOtherPlayer(taken, player)).thenReturn(true);
        when(db.isAlreadyAssignedToOtherPlayer(free, player)).thenReturn(false);
        when(db.createPlayerRecord(player, free, 6))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", free, false, 6));

        assertTrue(economyFuncs.accountCreate(player));
        verify(db).createPlayerRecord(player, free, 6);
    }

    @Test
    void accountCreate_newPlayer_givesUp_afterWalletIndexScanLimitAssignedAddresses() {
        when(configEngine.getWalletIndexScanLimit()).thenReturn(10);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(anyInt())).thenAnswer(inv -> addressForIndex(inv.getArgument(0)));
        when(db.isAlreadyAssignedToOtherPlayer(anyString(), eq(player))).thenReturn(true);

        assertFalse(economyFuncs.accountCreate(player));
        verify(rpc, times(10)).accountCreate(anyInt());
        verify(db, never()).createPlayerRecord(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_newPlayer_returnsFalse_whenMaxIndexUnreadable() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenThrow(new IllegalStateException("Database unavailable"));

        assertFalse(economyFuncs.accountCreate(player));
        verify(rpc, never()).accountCreate(anyInt());
    }

    @Test
    void accountCreate_concurrentJoins_neverShareAnIndex() throws Exception {
        // A stateful fake store: the max index only moves when a record is stored, so any gap between
        // reading the max and storing the record would hand two players the same index.
        AtomicInteger storedMax = new AtomicInteger(0);
        java.util.List<Integer> issued = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        when(db.hasPlayerRecord(any(Player.class))).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenAnswer(inv -> storedMax.get());
        when(rpc.accountCreate(anyInt())).thenAnswer(inv ->
        {
            Thread.sleep(20); // widen the race window
            return addressForIndex(inv.getArgument(0));
        });
        when(db.createPlayerRecord(any(Player.class), anyString(), any())).thenAnswer(inv ->
        {
            Integer index = inv.getArgument(2);
            issued.add(index);
            storedMax.accumulateAndGet(index, Math::max);
            return new PlayerRecord(UUID.randomUUID().toString(), "P", inv.getArgument(1), false, index);
        });

        int joins = 8;
        ExecutorService pool = Executors.newFixedThreadPool(joins);
        CountDownLatch go = new CountDownLatch(1);
        java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < joins; i++) {
            Player p = mock(Player.class);
            when(p.getName()).thenReturn("P" + i);
            results.add(pool.submit(() -> {
                go.await();
                return economyFuncs.accountCreate(p);
            }));
        }
        go.countDown();
        for (Future<Boolean> f : results) {
            assertTrue(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertEquals(joins, issued.size());
        assertEquals(joins, new java.util.HashSet<>(issued).size(), "indices must be unique: " + issued);
        assertEquals(1, java.util.Collections.min(issued));
        assertEquals(joins, java.util.Collections.max(issued));
    }

    // --- accountExists(Player) ---

    @Test
    void accountExists_returnsTrue_whenDBHasRecord() {
        when(db.hasPlayerRecord(player)).thenReturn(true);
        assertTrue(economyFuncs.accountExists(player));
    }

    @Test
    void accountExists_returnsFalse_whenDBHasNoRecord() {
        when(db.hasPlayerRecord(player)).thenReturn(false);
        assertFalse(economyFuncs.accountExists(player));
    }

    // --- Concurrency: addBalanceTP/removeBalanceFP must serialize sends against the same account ---

    @Test
    void addBalanceTP_concurrentCallsForDifferentPlayers_neverSendConcurrently() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(1000.0);

        OfflinePlayer playerA = mock(OfflinePlayer.class);
        OfflinePlayer playerB = mock(OfflinePlayer.class);
        String walletA = WALLET;
        String walletB = "ban_1differentwalletaddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx2222";
        when(db.getOfflinePlayerRecord(playerA)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", walletA, false));
        when(db.getOfflinePlayerRecord(playerB)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Bob", walletB, false));

        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        when(rpc.sendTransaction(eq(MASTER_WALLET), anyString(), anyDouble())).thenAnswer(invocation -> {
            int current = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            Thread.sleep(75);
            concurrent.decrementAndGet();
            return "block";
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> taskA = () -> {
            ready.countDown();
            go.await();
            return economyFuncs.addBalanceTP(playerA, 5.0);
        };
        Callable<Boolean> taskB = () -> {
            ready.countDown();
            go.await();
            return economyFuncs.addBalanceTP(playerB, 5.0);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> futureA = pool.submit(taskA);
        Future<Boolean> futureB = pool.submit(taskB);

        ready.await();
        go.countDown();

        assertTrue(futureA.get(5, TimeUnit.SECONDS));
        assertTrue(futureB.get(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, maxConcurrent.get(),
                "addBalanceTP must never allow two concurrent sends from the shared master wallet, even for different recipients");
    }

    @Test
    void removeBalanceFP_concurrentCallsForSameWallet_neverSendConcurrently() throws Exception {
        OfflinePlayer playerA = mock(OfflinePlayer.class);
        OfflinePlayer playerB = mock(OfflinePlayer.class);
        // Same wallet for both callers - simulates the same underlying account being hit twice at once.
        when(db.getOfflinePlayerRecord(playerA)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(db.getOfflinePlayerRecord(playerB)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice2", WALLET, false));
        when(rpc.getBalance(WALLET)).thenReturn(1000.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);

        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        when(rpc.sendTransaction(eq(WALLET), eq(MASTER_WALLET), anyDouble())).thenAnswer(invocation -> {
            int current = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            Thread.sleep(75);
            concurrent.decrementAndGet();
            return "block";
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> taskA = () -> {
            ready.countDown();
            go.await();
            return economyFuncs.removeBalanceFP(playerA, 5.0);
        };
        Callable<Boolean> taskB = () -> {
            ready.countDown();
            go.await();
            return economyFuncs.removeBalanceFP(playerB, 5.0);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> futureA = pool.submit(taskA);
        Future<Boolean> futureB = pool.submit(taskB);

        ready.await();
        go.countDown();

        assertTrue(futureA.get(5, TimeUnit.SECONDS));
        assertTrue(futureB.get(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, maxConcurrent.get(), "removeBalanceFP must serialize concurrent sends from the same wallet");
    }

    @Test
    void removeBalanceFP_concurrentCallsForDifferentWallets_areNotSerializedAgainstEachOther() throws Exception {
        OfflinePlayer playerA = mock(OfflinePlayer.class);
        OfflinePlayer playerB = mock(OfflinePlayer.class);
        String walletA = WALLET;
        String walletB = "ban_1differentwalletaddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx2222";
        when(db.getOfflinePlayerRecord(playerA)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", walletA, false));
        when(db.getOfflinePlayerRecord(playerB)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Bob", walletB, false));
        when(rpc.getBalance(walletA)).thenReturn(1000.0);
        when(rpc.getBalance(walletB)).thenReturn(1000.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);

        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        CountDownLatch bothEntered = new CountDownLatch(2);

        when(rpc.sendTransaction(anyString(), eq(MASTER_WALLET), anyDouble())).thenAnswer(invocation -> {
            int current = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            bothEntered.countDown();
            // Give the other thread a real chance to overlap before releasing this one.
            bothEntered.await(2, TimeUnit.SECONDS);
            concurrent.decrementAndGet();
            return "block";
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> taskA = () -> {
            ready.countDown();
            go.await();
            return economyFuncs.removeBalanceFP(playerA, 5.0);
        };
        Callable<Boolean> taskB = () -> {
            ready.countDown();
            go.await();
            return economyFuncs.removeBalanceFP(playerB, 5.0);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> futureA = pool.submit(taskA);
        Future<Boolean> futureB = pool.submit(taskB);

        ready.await();
        go.countDown();

        assertTrue(futureA.get(5, TimeUnit.SECONDS));
        assertTrue(futureB.get(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(2, maxConcurrent.get(),
                "Different wallets are independent accounts and must not be serialized against each other");
    }

    // --- accountCreate: skipping assigned addresses, duplicate keys, backfill gate ---

    @Test
    void accountCreate_newPlayer_keepsSkippingAssignedAddresses_beyondTenAttempts() {
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(anyInt())).thenAnswer(inv -> addressForIndex(inv.getArgument(0)));
        when(db.isAlreadyAssignedToOtherPlayer(anyString(), eq(player))).thenAnswer(inv ->
                java.util.stream.IntStream.rangeClosed(1, 25).anyMatch(i -> addressForIndex(i).equals(inv.getArgument(0))));
        when(db.createPlayerRecord(eq(player), anyString(), any()))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false, 26));

        assertTrue(economyFuncs.accountCreate(player));
        verify(db).createPlayerRecord(player, addressForIndex(26), 26);
    }

    @Test
    void accountCreate_duplicateKeyOnInsert_triesNextIndex_neverOverwrites() {
        String first = addressForIndex(1);
        String second = addressForIndex(2);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(1)).thenReturn(first);
        when(rpc.accountCreate(2)).thenReturn(second);
        when(db.createPlayerRecord(player, first, 1)).thenThrow(new DuplicateWalletException("taken", null));
        when(db.createPlayerRecord(player, second, 2))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", second, false, 2));

        assertTrue(economyFuncs.accountCreate(player));
        verify(db).createPlayerRecord(player, second, 2);
    }

    @Test
    void accountCreate_duplicateKeyOnRepair_triesNextIndex() {
        String first = addressForIndex(1);
        String second = addressForIndex(2);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(true);
        when(db.getPlayerRecord(player)).thenReturn(
                new PlayerRecord(UUID.randomUUID().toString(), "Alice", RPC.ACCOUNT_CREATION_FAILED, false));
        when(rpc.accountCreate(1)).thenReturn(first);
        when(rpc.accountCreate(2)).thenReturn(second);
        when(db.updatePlayerWallet(player, first, 1)).thenThrow(new DuplicateWalletException("taken", null));
        when(db.updatePlayerWallet(player, second, 2)).thenReturn(true);

        assertTrue(economyFuncs.accountCreate(player));
        verify(db).updatePlayerWallet(player, second, 2);
    }

    @Test
    void accountCreate_duplicateKeyEveryTime_givesUpAtScanLimit() {
        when(configEngine.getWalletIndexScanLimit()).thenReturn(4);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(0);
        when(rpc.accountCreate(anyInt())).thenAnswer(inv -> addressForIndex(inv.getArgument(0)));
        when(db.createPlayerRecord(eq(player), anyString(), any())).thenThrow(new DuplicateWalletException("taken", null));

        assertFalse(economyFuncs.accountCreate(player));
        verify(db, times(4)).createPlayerRecord(eq(player), anyString(), any());
    }

    @Test
    void accountCreate_newPlayer_failsFastWithoutAllocating_whileBackfillRunning() {
        economyFuncs.setBackfillGate(new BackfillGate(false, MainThreadGuard.neverPrimary()), 50L);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);

        assertFalse(economyFuncs.accountCreate(player));
        assertTrue(economyFuncs.isWalletSetupPending());
        verify(db, never()).getMaxPlayerWalletIndex();
        verify(rpc, never()).accountCreate(anyInt());
        verify(db, never()).createPlayerRecord(any(Player.class), anyString(), any());
    }

    @Test
    void accountCreate_newPlayer_waitsForBackfill_thenAllocates() {
        BackfillGate gate = new BackfillGate(false, MainThreadGuard.neverPrimary());
        economyFuncs.setBackfillGate(gate, 5_000L);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(false);
        when(db.getMaxPlayerWalletIndex()).thenReturn(3);
        when(rpc.accountCreate(4)).thenReturn(WALLET);
        when(db.createPlayerRecord(player, WALLET, 4))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false, 4));

        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            gate.open();
        }).start();

        assertTrue(economyFuncs.accountCreate(player));
        assertFalse(economyFuncs.isWalletSetupPending());
    }

    @Test
    void accountCreate_existingValidRecord_doesNotWaitForBackfill() {
        economyFuncs.setBackfillGate(new BackfillGate(false, MainThreadGuard.neverPrimary()), 60_000L);
        when(player.getName()).thenReturn("Alice");
        when(db.hasPlayerRecord(player)).thenReturn(true);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));

        assertTrue(economyFuncs.accountCreate(player));
    }

    // --- Unknown outcome: success for Vault/boolean callers, SEVERE log, no receive ---

    private java.util.List<java.util.logging.LogRecord> captureSevere() {
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        Logger logger = Logger.getLogger("economyfuncs-unknown-test");
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) {
                if (r.getLevel() == java.util.logging.Level.SEVERE) records.add(r);
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
        when(plugin.getLogger()).thenReturn(logger);
        return records;
    }

    private void assertUnknownLogged(java.util.List<java.util.logging.LogRecord> severe, String direction) {
        assertEquals(1, severe.size());
        String message = severe.get(0).getMessage();
        assertTrue(message.contains("Alice"), message);
        assertTrue(message.contains("5.0"), message);
        assertTrue(message.contains(direction), message);
        assertTrue(message.contains("outcome unknown \u2013 reconcile with /history"), message);
    }

    @Test
    void removeBalanceFP_player_unknownOutcome_isSuccess_andLoggedSevere() throws Exception {
        java.util.List<java.util.logging.LogRecord> severe = captureSevere();
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenThrow(new UnknownOutcomeError());

        assertEquals(TransferOutcome.UNKNOWN, economyFuncs.removeBalanceFPOutcome(player, 5.0));
        assertTrue(economyFuncs.removeBalanceFP(player, 5.0));
        verify(rpc, never()).receiveBlock(anyString(), anyString());
        assertEquals(2, severe.size());
        severe.remove(1);
        assertUnknownLogged(severe, "withdraw");
    }

    @Test
    void removeBalanceFP_offline_unknownOutcome_isSuccess_andLoggedSevere() throws Exception {
        java.util.List<java.util.logging.LogRecord> severe = captureSevere();
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenThrow(new UnknownOutcomeError());

        assertEquals(TransferOutcome.UNKNOWN, economyFuncs.removeBalanceFPOutcome(offlinePlayer, 5.0));
        assertUnknownLogged(severe, "withdraw");
        assertTrue(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
        verify(rpc, never()).receiveBlock(anyString(), anyString());
    }

    @Test
    void addBalanceTP_player_unknownOutcome_isSuccess_andLoggedSevere() throws Exception {
        java.util.List<java.util.logging.LogRecord> severe = captureSevere();
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new UnknownOutcomeError());

        assertEquals(TransferOutcome.UNKNOWN, economyFuncs.addBalanceTPOutcome(player, 5.0));
        assertUnknownLogged(severe, "deposit");
        assertTrue(economyFuncs.addBalanceTP(player, 5.0));
        verify(rpc, never()).receiveBlock(anyString(), anyString());
    }

    @Test
    void addBalanceTP_offline_unknownOutcome_isSuccess_andLoggedSevere() throws Exception {
        java.util.List<java.util.logging.LogRecord> severe = captureSevere();
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getOfflinePlayerRecord(offlinePlayer))
                .thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new UnknownOutcomeError());

        assertEquals(TransferOutcome.UNKNOWN, economyFuncs.addBalanceTPOutcome(offlinePlayer, 5.0));
        assertUnknownLogged(severe, "deposit");
        assertTrue(economyFuncs.addBalanceTP(offlinePlayer, 5.0));
    }

    @Test
    void plainTransactionError_isStillFailure_notUnknown() throws Exception {
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenThrow(new TransactionError("Timeout"));

        assertEquals(TransferOutcome.FAILED, economyFuncs.addBalanceTPOutcome(player, 5.0));
        assertFalse(economyFuncs.addBalanceTP(player, 5.0));
    }

    // --- Light mode: no synchronous proof-of-work on the primary thread ---

    @Test
    void lightMode_autoReceiveOnPrimaryThread_isScheduledAsync() throws Exception {
        java.util.List<Runnable> scheduled = new java.util.ArrayList<>();
        economyFuncs.setThreadGuard(new MainThreadGuard(() -> true, scheduled::add));
        when(configEngine.isLightMode()).thenReturn(true);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("blockDEF");

        assertTrue(economyFuncs.addBalanceTP(player, 5.0));

        verify(rpc, never()).receiveBlock(anyString(), anyString());
        assertEquals(1, scheduled.size());
        scheduled.get(0).run();
        verify(rpc).receiveBlock(WALLET, "blockDEF");
    }

    @Test
    void lightMode_removeBalanceReceiveOnPrimaryThread_isScheduledAsync_forEveryOverload() throws Exception {
        java.util.List<Runnable> scheduled = new java.util.ArrayList<>();
        economyFuncs.setThreadGuard(new MainThreadGuard(() -> true, scheduled::add));
        when(configEngine.isLightMode()).thenReturn(true);
        PlayerRecord record = new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false);
        when(db.getPlayerRecord(player)).thenReturn(record);
        when(db.getOfflinePlayerRecord(offlinePlayer)).thenReturn(record);
        when(rpc.getBalance(WALLET)).thenReturn(10.0);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.sendTransaction(WALLET, MASTER_WALLET, 5.0)).thenReturn("b1", "b2");
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("b3");

        assertTrue(economyFuncs.removeBalanceFP(player, 5.0));
        assertTrue(economyFuncs.removeBalanceFP(offlinePlayer, 5.0));
        assertTrue(economyFuncs.addBalanceTP(offlinePlayer, 5.0));

        verify(rpc, never()).receiveBlock(anyString(), anyString());
        assertEquals(3, scheduled.size());
        scheduled.forEach(Runnable::run);
        verify(rpc).receiveBlock(MASTER_WALLET, "b1");
        verify(rpc).receiveBlock(MASTER_WALLET, "b2");
        verify(rpc).receiveBlock(WALLET, "b3");
    }

    @Test
    void lightMode_autoReceiveOffPrimaryThread_runsInline() throws Exception {
        economyFuncs.setThreadGuard(new MainThreadGuard(() -> false, r -> fail("must not schedule")));
        when(configEngine.isLightMode()).thenReturn(true);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("blockDEF");

        assertTrue(economyFuncs.addBalanceTP(player, 5.0));
        verify(rpc).receiveBlock(WALLET, "blockDEF");
    }

    @Test
    void nodeMode_autoReceiveOnPrimaryThread_staysInline() throws Exception {
        economyFuncs.setThreadGuard(new MainThreadGuard(() -> true, r -> fail("must not schedule")));
        when(configEngine.isLightMode()).thenReturn(false);
        when(rpc.getMasterWallet()).thenReturn(MASTER_WALLET);
        when(rpc.getBalance(MASTER_WALLET)).thenReturn(100.0);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.sendTransaction(MASTER_WALLET, WALLET, 5.0)).thenReturn("blockDEF");

        assertTrue(economyFuncs.addBalanceTP(player, 5.0));
        verify(rpc).receiveBlock(WALLET, "blockDEF");
    }

    // --- Light mode: receive + work precompute when a player joins ---

    @Test
    void prepareWalletOnJoin_lightMode_precomputesWork_andPocketsPendingBlocks() throws Exception {
        when(configEngine.isLightMode()).thenReturn(true);
        economyFuncs.setThreadGuard(MainThreadGuard.neverPrimary());
        when(player.getName()).thenReturn("Alice");
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.receivablePending(WALLET)).thenReturn(List.of("h1", "h2"));

        economyFuncs.prepareWalletOnJoin(player);

        verify(rpc).precomputeWork(WALLET);
        verify(rpc).receiveBlock(WALLET, "h1");
        verify(rpc).receiveBlock(WALLET, "h2");
    }

    @Test
    void prepareWalletOnJoin_isBounded() throws Exception {
        when(configEngine.isLightMode()).thenReturn(true);
        economyFuncs.setThreadGuard(MainThreadGuard.neverPrimary());
        when(player.getName()).thenReturn("Alice");
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) many.add("h" + i);
        when(rpc.receivablePending(WALLET)).thenReturn(many);

        economyFuncs.prepareWalletOnJoin(player);

        verify(rpc, times(EconomyFuncs.JOIN_RECEIVE_MAX)).receiveBlock(eq(WALLET), anyString());
    }

    @Test
    void prepareWalletOnJoin_stopsQuietly_onReceiveFailure_andNeverThrows() throws Exception {
        when(configEngine.isLightMode()).thenReturn(true);
        economyFuncs.setThreadGuard(MainThreadGuard.neverPrimary());
        when(player.getName()).thenReturn("Alice");
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, false));
        when(rpc.receivablePending(WALLET)).thenReturn(List.of("h1", "h2"));
        when(rpc.receiveBlock(WALLET, "h1")).thenThrow(new TransactionError("network"));

        assertDoesNotThrow(() -> economyFuncs.prepareWalletOnJoin(player));
        verify(rpc, never()).receiveBlock(WALLET, "h2");
    }

    @Test
    void prepareWalletOnJoin_nodeMode_doesNothing() throws Exception {
        when(configEngine.isLightMode()).thenReturn(false);
        economyFuncs.prepareWalletOnJoin(player);
        verifyNoInteractions(rpc);
    }

    @Test
    void prepareWalletOnJoin_skipsFrozenAndWalletlessPlayers() throws Exception {
        when(configEngine.isLightMode()).thenReturn(true);
        economyFuncs.setThreadGuard(MainThreadGuard.neverPrimary());
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", WALLET, true));
        economyFuncs.prepareWalletOnJoin(player);
        when(db.getPlayerRecord(player)).thenReturn(new PlayerRecord(UUID.randomUUID().toString(), "Alice", RPC.ACCOUNT_CREATION_FAILED, false));
        economyFuncs.prepareWalletOnJoin(player);
        verifyNoInteractions(rpc);
    }

    @Test
    void prepareWalletOnJoin_onPrimaryThread_isScheduledAsync() throws Exception {
        java.util.List<Runnable> scheduled = new java.util.ArrayList<>();
        when(configEngine.isLightMode()).thenReturn(true);
        economyFuncs.setThreadGuard(new MainThreadGuard(() -> true, scheduled::add));

        economyFuncs.prepareWalletOnJoin(player);

        assertEquals(1, scheduled.size());
        verifyNoInteractions(rpc);
    }
}
