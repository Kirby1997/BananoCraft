package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.validation.Validator;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.classes.TransferOutcome;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.DuplicateWalletException;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.wallet.BackfillGate;
import banano.bananominecraft.bananoeconomy.wallet.MainThreadGuard;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class EconomyFuncs
{
    /** Used when the configured walletIndexScanLimit is unusable (&le; 0). */
    private static final int DEFAULT_INDEX_SCAN_LIMIT = 10_000;

    /** How long a join waits (off the main thread) for the startup wallet-index backfill. */
    static final long DEFAULT_BACKFILL_WAIT_MILLIS = 30_000L;

    /** Most pending blocks pocketed per player join (light mode); the rest wait for /receive. */
    static final int JOIN_RECEIVE_MAX = 5;

    /** Message logged/shown when new-account allocation has to wait for the backfill. */
    public static final String SETUP_IN_PROGRESS =
            "Wallet setup is still in progress after the server start - please rejoin in a minute.";

    private final Plugin plugin;
    private final IDBConnector db;
    private final RPC rpc;
    private final ConfigEngine configEngine;

    // Every deposit sends from this single master-wallet account, and Banano (like other
    // Nano-family chains) requires strictly sequential blocks per account. Without this lock,
    // concurrent deposits (e.g. a player redeeming several vouchers in quick succession) race:
    // each reads the same stale master-wallet balance and fires its own send, so the checks and
    // sends interleave incorrectly. Fair (FIFO) so concurrent callers are served in arrival order.
    private final ReentrantLock masterWalletLock = new ReentrantLock(true);

    // Withdrawals send from the player's own wallet, so different players' withdrawals are
    // independent accounts and don't need to wait on each other - only the same wallet's own
    // concurrent withdrawals do. One lock per wallet address, created lazily.
    private final ConcurrentHashMap<String, ReentrantLock> walletLocks = new ConcurrentHashMap<>();

    // Serialises player-wallet index allocation (max stored index + 1) from reading the maximum
    // until the new record is stored, so two concurrent joins can never be given the same index.
    private final Object walletIndexLock = new Object();

    // Light mode generates proof-of-work locally; Vault calls are synchronous and may arrive on the
    // primary thread, so anything slow that is not needed for the answer is moved off it.
    private volatile MainThreadGuard threadGuard;

    // New-index allocation (max stored index + 1) is only reliable once the startup backfill has
    // matched legacy records to their indices. Open by default (nothing to wait for).
    private volatile BackfillGate backfillGate = BackfillGate.alwaysOpen();
    private volatile long backfillWaitMillis = DEFAULT_BACKFILL_WAIT_MILLIS;

    public EconomyFuncs(Plugin plugin, IDBConnector db, RPC rpc, ConfigEngine configEngine)
    {
        this.plugin = plugin;
        this.db = db;
        this.rpc = rpc;
        this.configEngine = configEngine;
        this.threadGuard = MainThreadGuard.forPlugin(plugin);
    }

    /** Replaces the primary-thread guard (tests). */
    public void setThreadGuard(MainThreadGuard guard)
    {
        if (guard != null)
        {
            this.threadGuard = guard;
        }
    }

    /** Gates new-account index allocation on the startup wallet-index backfill. */
    public void setBackfillGate(BackfillGate gate, long maxWaitMillis)
    {
        if (gate != null)
        {
            this.backfillGate = gate;
            this.backfillWaitMillis = maxWaitMillis;
        }
    }

    /** {@code true} while the startup backfill is still running (new accounts cannot be created yet). */
    public boolean isWalletSetupPending()
    {
        return !this.backfillGate.isOpen();
    }

    /** The lock serializing blocks on the master-wallet account; shared with BankService. */
    public ReentrantLock getMasterWalletLock()
    {
        return this.masterWalletLock;
    }

    /** Returns the (lazily created) lock serializing sends from the given wallet address. */
    private ReentrantLock lockForWallet(String wallet)
    {
        return this.walletLocks.computeIfAbsent(wallet, key -> new ReentrantLock(true));
    }

    /**
     * Best-effort: the transfer has already succeeded regardless of whether this does. If it
     * fails, the block is simply left pending - it is picked up later by the node's own
     * auto-receive (node mode), by the receive-on-join / confirmation websocket (light mode, where
     * the plugin signs for every account itself), or by a manual /receive.
     *
     * <p>In light mode a receive needs proof-of-work, which can take seconds, and this is reached
     * from synchronous Vault calls that may be on the primary thread: there the receive is scheduled
     * on a background thread instead of blocking the server.</p>
     */
    private void tryAutoReceive(String recipientWallet, String blockHash)
    {
        if (configEngine.isLightMode())
        {
            threadGuard.runOffMainThread(() -> receiveNow(recipientWallet, blockHash));
        }
        else
        {
            receiveNow(recipientWallet, blockHash);
        }
    }

    private void receiveNow(String recipientWallet, String blockHash)
    {
        try
        {
            rpc.receiveBlock(recipientWallet, blockHash);
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to auto-receive block " + blockHash + " for " + recipientWallet, e);
        }
    }

    /**
     * Light mode only (no-op in node mode, where the node auto-receives): called off the main thread
     * when a player joins. Starts the proof-of-work for the player's next block and pockets a bounded
     * number of pending blocks, best-effort. Never throws.
     */
    public void prepareWalletOnJoin(Player player)
    {
        if (!configEngine.isLightMode())
        {
            return;
        }

        threadGuard.runOffMainThread(() ->
        {
            try
            {
                PlayerRecord record = db.getPlayerRecord(player);
                if (record == null || record.isFrozen() || !isUsableWallet(record.getWallet()))
                {
                    return;
                }

                String wallet = record.getWallet();
                rpc.precomputeWork(wallet);

                int received = 0;
                for (String blockHash : rpc.receivablePending(wallet))
                {
                    if (received >= JOIN_RECEIVE_MAX)
                    {
                        break;
                    }
                    rpc.receiveBlock(wallet, blockHash);
                    received++;
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING,
                        "Receive-on-join stopped for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    private void logUnknownOutcome(String playerName, double amount, String direction)
    {
        plugin.getLogger().severe("UNKNOWN OUTCOME for player " + playerName + ", amount " + amount
                + " BAN, direction " + direction + ": " + TransferOutcome.UNKNOWN_NOTE
                + ". Reporting success to the caller; the wallet engine will resolve that block before"
                + " anything else is built on the account.");
    }

    public boolean freezePlayer(Player player)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord != null)
        {
            return freezePlayer(playerRecord);
        }

        return false;
    }

    public boolean freezePlayer(OfflinePlayer player)
    {
        // An online player's record is cached and is what the frozen checks read: change that
        // instance, never a separate copy that a concurrent load could overwrite.
        Player online = player.getPlayer();
        PlayerRecord playerRecord = online != null
                ? this.db.getPlayerRecord(online)
                : this.db.getOfflinePlayerRecord(player);

        if (playerRecord != null)
        {
            return freezePlayer(playerRecord);
        }

        return false;
    }

    private boolean freezePlayer(PlayerRecord playerRecord)
    {
        if (playerRecord != null)
        {
            boolean previous = playerRecord.isFrozen();
            playerRecord.setFrozen(true);

            if (this.db.updatePlayerRecord(playerRecord))
            {
                return true;
            }

            // Persisting failed: roll back the in-memory (cached) state so it matches the DB.
            playerRecord.setFrozen(previous);
            plugin.getLogger().warning("Failed to persist freeze state for " + playerRecord.getPlayerName());
        }

        return false;
    }

    public boolean freezePlayer(String playerName)
    {
        Player player = Bukkit.getPlayerExact(playerName);

        if (player != null)
        {
            return freezePlayer(player);
        }
        else
        {
            List<OfflinePlayer> offlinePlayers = new ArrayList<>();

            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers())
            {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName))
                {
                    offlinePlayers.add(offlinePlayer);
                }
            }

            if (offlinePlayers.size() == 1)
            {
                PlayerRecord offlineRecord = this.db.getOfflinePlayerRecord(offlinePlayers.get(0));
                return freezePlayer(offlineRecord);
            }
        }

        return false;
    }

    public boolean unfreezePlayer(Player player)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord != null)
        {
            return unfreezePlayer(playerRecord);
        }

        return false;
    }

    public boolean unfreezePlayer(OfflinePlayer player)
    {
        // An online player's record is cached and is what the frozen checks read: change that
        // instance, never a separate copy that a concurrent load could overwrite.
        Player online = player.getPlayer();
        PlayerRecord playerRecord = online != null
                ? this.db.getPlayerRecord(online)
                : this.db.getOfflinePlayerRecord(player);

        if (playerRecord != null)
        {
            return unfreezePlayer(playerRecord);
        }

        return false;
    }

    private boolean unfreezePlayer(PlayerRecord playerRecord)
    {
        if (playerRecord != null)
        {
            boolean previous = playerRecord.isFrozen();
            playerRecord.setFrozen(false);

            if (this.db.updatePlayerRecord(playerRecord))
            {
                return true;
            }

            // Persisting failed: roll back the in-memory (cached) state so it matches the DB.
            playerRecord.setFrozen(previous);
            plugin.getLogger().warning("Failed to persist unfreeze state for " + playerRecord.getPlayerName());
        }

        return false;
    }

    public boolean unfreezePlayer(String playerName)
    {
        Player player = Bukkit.getPlayerExact(playerName);

        if (player != null)
        {
            return unfreezePlayer(player);
        }
        else
        {
            List<OfflinePlayer> offlinePlayers = new ArrayList<>();

            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers())
            {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName))
                {
                    offlinePlayers.add(offlinePlayer);
                }
            }

            if (offlinePlayers.size() == 1)
            {
                PlayerRecord offlineRecord = this.db.getOfflinePlayerRecord(offlinePlayers.get(0));
                return unfreezePlayer(offlineRecord);
            }
        }

        return false;
    }

    public Double getBalance(Player player)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord != null)
        {
            String playerWallet = playerRecord.getWallet();

            if (playerWallet == null || playerRecord.isFrozen())
            {
                return 0.0;
            }

            return rpc.getBalance(playerWallet);
        }

        return 0d;
    }

    public Double getBalance(OfflinePlayer player)
    {
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if (playerRecord != null)
        {
            String playerWallet = playerRecord.getWallet();

            if (playerWallet == null || playerRecord.isFrozen())
            {
                return 0.0;
            }

            return rpc.getBalance(playerWallet);
        }

        return 0d;
    }

    public List<TransactionRecord> getTransactionHistory(UUID playerId, int lastXTransactions)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(playerId);
        int maxTransactions = this.configEngine.getMaximumTransactionHistoryCount();

        if (playerRecord == null
              || playerRecord.isFrozen()
              || lastXTransactions < 0
              || lastXTransactions > maxTransactions)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve history for account: " + playerId, playerRecord);

            return null;
        }

        try
        {
            return rpc.getTransactionHistory(playerRecord.getWallet(), lastXTransactions);
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "getTransactionHistory failed for offline player.", e);
            return null;
        }
    }

    public boolean removeBalanceFP(OfflinePlayer player, double amount)
    {
        return removeBalanceFPOutcome(player, amount).isSuccess();
    }

    /**
     * As {@link #removeBalanceFP(OfflinePlayer, double)} but distinguishes
     * {@link TransferOutcome#UNKNOWN} (light mode: published, fate unknown) from a plain success.
     */
    public TransferOutcome removeBalanceFPOutcome(OfflinePlayer player, double amount)
    {
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if (playerRecord == null || playerRecord.isFrozen())
        {
            return TransferOutcome.FAILED;
        }

        ReentrantLock lock = lockForWallet(playerRecord.getWallet());
        lock.lock();

        try
        {
            double balance = rpc.getBalance(playerRecord.getWallet());

            if (balance - amount < 0)
            {
                return TransferOutcome.FAILED;
            }

            try
            {
                String blockHash = rpc.sendTransaction(playerRecord.getWallet(), rpc.getMasterWallet(), amount);
                tryAutoReceive(rpc.getMasterWallet(), blockHash);
                return TransferOutcome.OK;
            }
            catch (UnknownOutcomeError e)
            {
                logUnknownOutcome(playerRecord.getPlayerName(), amount, "withdraw (player to server)");
                return TransferOutcome.UNKNOWN;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "removeBalanceFP failed for offline player.", e);
                return TransferOutcome.FAILED;
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public boolean addBalanceTP(OfflinePlayer player, double amount)
    {
        return addBalanceTPOutcome(player, amount).isSuccess();
    }

    /**
     * As {@link #addBalanceTP(OfflinePlayer, double)} but distinguishes {@link TransferOutcome#UNKNOWN}
     * (light mode: published, fate unknown) from a plain success.
     */
    public TransferOutcome addBalanceTPOutcome(OfflinePlayer player, double amount)
    {
        this.masterWalletLock.lock();

        try
        {
            String sender = rpc.getMasterWallet();
            double serverBalance = rpc.getBalance(sender);
            PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

            if (playerRecord == null || serverBalance - amount < 0 || playerRecord.isFrozen())
            {
                return TransferOutcome.FAILED;
            }

            try
            {
                String blockHash = rpc.sendTransaction(sender, playerRecord.getWallet(), amount);
                tryAutoReceive(playerRecord.getWallet(), blockHash);
                return TransferOutcome.OK;
            }
            catch (UnknownOutcomeError e)
            {
                logUnknownOutcome(playerRecord.getPlayerName(), amount, "deposit (server to player)");
                return TransferOutcome.UNKNOWN;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "addBalanceTP failed for offline player.", e);
                return TransferOutcome.FAILED;
            }
        }
        finally
        {
            this.masterWalletLock.unlock();
        }
    }

    public boolean accountExists(Player player)
    {
        return this.db.hasPlayerRecord(player);
    }

    public boolean accountCreate(Player player)
    {
        String playerName = player.getName();

        try
        {
            if (!this.db.hasPlayerRecord(player))
            {
                if (!awaitBackfill(playerName))
                {
                    return false;
                }

                synchronized (this.walletIndexLock)
                {
                    if (allocateAndStoreWallet(player, false))
                    {
                        plugin.getLogger().info("Created new wallet for " + playerName);
                        return true;
                    }

                    plugin.getLogger().warning("Could not create new wallet for " + playerName);
                }
            }
            else
            {
                PlayerRecord playerRecord = this.db.getPlayerRecord(player);

                if (playerRecord != null)
                {
                    if (isUsableWallet(playerRecord.getWallet()))
                    {
                        plugin.getLogger().info("Player wallet loaded for " + playerName);
                        return true;
                    }

                    // Legacy/broken record: an earlier failed creation stored the sentinel (or some
                    // other invalid address) as the wallet. Repair it with a fresh account.
                    plugin.getLogger().warning("Stored wallet for " + playerName + " is invalid - creating a new one.");

                    if (!awaitBackfill(playerName))
                    {
                        return false;
                    }

                    synchronized (this.walletIndexLock)
                    {
                        if (allocateAndStoreWallet(player, true))
                        {
                            plugin.getLogger().info("Repaired wallet for " + playerName);
                            return true;
                        }
                    }

                    plugin.getLogger().warning("Could not repair wallet for " + playerName + "!");
                }
                else
                {
                    plugin.getLogger().warning("Player wallet could not be loaded for " + playerName + "!");
                }
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Exception creating account for " + playerName, e);
        }

        return false;
    }

    /**
     * Waits (bounded, never on the primary thread) for the startup wallet-index backfill: until legacy
     * records have been matched to their indices, "highest stored index + 1" could collide with one.
     */
    private boolean awaitBackfill(String playerName)
    {
        if (this.backfillGate.awaitOpen(this.backfillWaitMillis))
        {
            return true;
        }

        plugin.getLogger().warning("Not creating a wallet for " + playerName + " yet: " + SETUP_IN_PROGRESS);
        return false;
    }

    /** True if the address is a well-formed Banano address and not the creation-failure sentinel. */
    private static boolean isUsableWallet(String wallet)
    {
        return wallet != null
                && !wallet.equalsIgnoreCase(RPC.ACCOUNT_CREATION_FAILED)
                && Validator.validateAddress(wallet);
    }

    /** A newly derived wallet address together with the seed-derivation index it was created at. */
    private record NewWallet(String address, int index) {}

    private int scanLimit()
    {
        int limit = this.configEngine.getWalletIndexScanLimit();
        return limit > 0 ? limit : DEFAULT_INDEX_SCAN_LIMIT;
    }

    /**
     * Allocates an index, derives its address and stores it (a new record, or {@code repair}: a new
     * wallet for the existing record). If storage reports the address/index as already owned by another
     * record - e.g. written meanwhile by another server sharing the database - nothing is overwritten
     * and the next index is tried. Callers hold {@link #walletIndexLock}.
     */
    private boolean allocateAndStoreWallet(Player player, boolean repair)
    {
        int index = this.db.getMaxPlayerWalletIndex() + 1;
        final int limit = scanLimit();
        final int stop = index + limit;

        while (index < stop)
        {
            NewWallet created = createUniqueWallet(player, index, stop);

            if (created == null)
            {
                return false;
            }

            try
            {
                if (repair)
                {
                    return this.db.updatePlayerWallet(player, created.address(), created.index());
                }

                return this.db.createPlayerRecord(player, created.address(), created.index()) != null;
            }
            catch (DuplicateWalletException e)
            {
                plugin.getLogger().warning("Wallet index " + created.index() + " (" + created.address()
                        + ") is already taken in the database - trying the next index.");
                index = created.index() + 1;
            }
        }

        plugin.getLogger().severe("Could not find a free wallet index for " + player.getName() + " - aborting.");
        return false;
    }

    /**
     * Derives the first account at or after {@code startIndex} (at an EXPLICIT derivation index) that is
     * valid and not assigned to any other player: keeps skipping addresses the database says are already
     * assigned (e.g. a legacy record that was never matched to an index) until {@code stopIndex}.
     * Callers hold {@link #walletIndexLock} until the new record is stored.
     *
     * <p>Used identically in node and light mode (node mode passes the index to {@code account_create}).
     * Returns null (after logging a warning) if creation fails or no unique account is found.</p>
     */
    private NewWallet createUniqueWallet(Player player, int startIndex, int stopIndex)
    {
        String playerName = player.getName();

        for (int index = startIndex; index < stopIndex; index++)
        {
            String wallet = rpc.accountCreate(index);

            if (!isUsableWallet(wallet))
            {
                plugin.getLogger().warning("Account creation failed for " + playerName + ": " + wallet);
                return null;
            }

            if (!this.db.isAlreadyAssignedToOtherPlayer(wallet, player))
            {
                return new NewWallet(wallet, index);
            }
        }

        plugin.getLogger().severe("Could not find a unique wallet for " + playerName
                + " between index " + startIndex + " and " + (stopIndex - 1) + " (walletIndexScanLimit) - aborting.");
        return null;
    }

    public void unloadAccount(Player player)
    {
        this.db.unloadPlayerRecord(player);
    }

    public boolean removeBalanceFP(Player player, double amount)
    {
        return removeBalanceFPOutcome(player, amount).isSuccess();
    }

    /** As {@link #removeBalanceFP(Player, double)}, distinguishing {@link TransferOutcome#UNKNOWN}. */
    public TransferOutcome removeBalanceFPOutcome(Player player, double amount)
    {
        // PLAYER TO MASTER WALLET
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord == null || playerRecord.isFrozen())
        {
            return TransferOutcome.FAILED;
        }

        ReentrantLock lock = lockForWallet(playerRecord.getWallet());
        lock.lock();

        try
        {
            double balance = getBalance(player);

            if (balance - amount < 0)
            {
                return TransferOutcome.FAILED;
            }

            try
            {
                String sender = playerRecord.getWallet();
                String blockHash = rpc.sendTransaction(sender, rpc.getMasterWallet(), amount);
                tryAutoReceive(rpc.getMasterWallet(), blockHash);
                return TransferOutcome.OK;
            }
            catch (UnknownOutcomeError e)
            {
                logUnknownOutcome(playerRecord.getPlayerName(), amount, "withdraw (player to server)");
                return TransferOutcome.UNKNOWN;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "removeBalanceFP failed for player.", e);
                return TransferOutcome.FAILED;
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public boolean addBalanceTP(Player player, double amount)
    {
        return addBalanceTPOutcome(player, amount).isSuccess();
    }

    /** As {@link #addBalanceTP(Player, double)}, distinguishing {@link TransferOutcome#UNKNOWN}. */
    public TransferOutcome addBalanceTPOutcome(Player player, double amount)
    {
        // MASTER WALLET TO PLAYER
        this.masterWalletLock.lock();

        try
        {
            String sender = rpc.getMasterWallet();
            double serverBalance = rpc.getBalance(sender);
            PlayerRecord playerRecord = this.db.getPlayerRecord(player);

            if (playerRecord == null || serverBalance - amount < 0 || isFrozen(player))
            {
                return TransferOutcome.FAILED;
            }

            try
            {
                String playerWallet = playerRecord.getWallet();
                String blockHash = rpc.sendTransaction(sender, playerWallet, amount);
                tryAutoReceive(playerWallet, blockHash);
                return TransferOutcome.OK;
            }
            catch (UnknownOutcomeError e)
            {
                logUnknownOutcome(playerRecord.getPlayerName(), amount, "deposit (server to player)");
                return TransferOutcome.UNKNOWN;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "addBalanceTP failed for player.", e);
                return TransferOutcome.FAILED;
            }
        }
        finally
        {
            this.masterWalletLock.unlock();
        }
    }

    public boolean isFrozen(Player player)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord != null)
        {
            return playerRecord.isFrozen();
        }

        return false;
    }

    public boolean isFrozen(OfflinePlayer player)
    {
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if (playerRecord != null)
        {
            return playerRecord.isFrozen();
        }

        return false;
    }

    public String getWallet(Player player)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord != null)
        {
            return playerRecord.getWallet();
        }

        return "";
    }

    public boolean hasWallet(Player player)
    {
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord != null)
        {
            return Validator.validateAddress(playerRecord.getWallet());
        }

        return false;
    }

    public boolean hasWallet(OfflinePlayer player)
    {
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if (playerRecord != null)
        {
            return Validator.validateAddress(playerRecord.getWallet());
        }

        return false;
    }
}
