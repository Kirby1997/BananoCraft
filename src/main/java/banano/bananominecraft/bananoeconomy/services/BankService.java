package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.validation.Validator;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Business logic for all Vault bank account operations.
 *
 * <p>All methods are no-ops (return {@code false} / {@code null}) when
 * {@link #isBankingEnabled()} returns {@code false}.  Callers must gate on
 * that check before exposing bank functionality.</p>
 *
 * <p>Deposit/withdraw semantics follow the Vault convention: the calling plugin
 * is responsible for moving funds to/from the player via
 * {@code withdrawPlayer}/{@code depositPlayer} before/after calling these
 * methods.  {@code deposit} moves funds from the master wallet into the bank
 * sub-account; {@code withdraw} moves them back.</p>
 *
 * <p>Balance reads are cache-first: the cache is pre-warmed asynchronously
 * on startup via {@link #preloadBankBalancesAsync(TaskTracker)} and
 * invalidated after every successful deposit or withdrawal so the next read
 * fetches a fresh on-chain value.</p>
 */
public class BankService
{
    private final Plugin       plugin;
    private final ConfigEngine configEngine;
    private final RPC          rpc;
    private final IDBConnector db;

    private final ConcurrentHashMap<String, Double> bankBalanceCache = new ConcurrentHashMap<>();

    // Serializes blocks on the master-wallet account (sends from it and receives into it).
    // Replaced with EconomyFuncs' lock via shareMasterWalletLock so that bank transfers and
    // Vault player deposits/withdrawals don't interleave on the same account chain.
    private volatile ReentrantLock masterWalletLock = new ReentrantLock(true);

    // Serialises bank-account index allocation (see createBank).
    private final Object bankIndexLock = new Object();

    private static final int    BANK_NAME_MAX_LENGTH = 64;
    private static final int    MAX_ADDRESS_ATTEMPTS = 10;
    private static final String BANK_NAME_PATTERN    = "[a-zA-Z0-9_-]+";

    public BankService(Plugin plugin, ConfigEngine configEngine, RPC rpc, IDBConnector db)
    {
        this.plugin       = plugin;
        this.configEngine = configEngine;
        this.rpc          = rpc;
        this.db           = db;
    }

    /** Uses the given lock (normally EconomyFuncs' master-wallet lock) for master-wallet blocks. */
    public void shareMasterWalletLock(ReentrantLock lock)
    {
        if (lock != null)
        {
            this.masterWalletLock = lock;
        }
    }

    /**
     * Best-effort: the transfer has already succeeded regardless of whether this does. If it
     * fails, the block is simply left pending until the next receive.
     */
    private void tryAutoReceive(String walletId, String recipient, String blockHash)
    {
        try
        {
            if (walletId == null)
            {
                rpc.receiveBlock(recipient, blockHash);
            }
            else
            {
                rpc.receiveBlock(walletId, recipient, blockHash);
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to auto-receive block " + blockHash + " for " + recipient, e);
        }
    }

    public boolean isBankingEnabled()
    {
        return configEngine.isBankSeedConfigured();
    }

    // -------------------------------------------------------------------------
    // Bank lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates a new bank sub-account derived from the bank wallet seed.
     * The owner is automatically added as a member.
     *
     * @return the created {@link BankRecord}, or {@code null} on failure
     */
    public BankRecord createBank(String name, String ownerUuid)
    {
        if (name == null || name.isEmpty() || name.length() > BANK_NAME_MAX_LENGTH
                || !name.matches(BANK_NAME_PATTERN))
        {
            plugin.getLogger().warning("Bank creation refused — invalid name: '" + name + "'.");
            return null;
        }

        if (db.bankNameExists(name))
        {
            return null;
        }

        // Bank-account indices are allocated as (highest stored bank index + 1) in both wallet modes,
        // so the whole allocate -> derive -> store sequence is serialised: two concurrent creations
        // must never be given the same index.
        synchronized (bankIndexLock)
        {
            // Never hand out an address an existing bank already owns (e.g. a legacy bank whose index
            // was never matched), so keep the set of taken addresses to skip past.
            Set<String> takenAddresses = new HashSet<>();
            for (String existingName : db.getAllBankNames())
            {
                BankRecord existing = db.getBankRecord(existingName);
                if (existing != null && existing.getAddress() != null)
                {
                    takenAddresses.add(existing.getAddress());
                }
            }

            int index;
            try
            {
                index = db.getMaxBankWalletIndex() + 1;
            }
            catch (IllegalStateException e)
            {
                plugin.getLogger().log(Level.SEVERE, "Could not read the highest bank wallet index for '" + name
                        + "' - not creating the bank.", e);
                return null;
            }

            String address = null;
            for (int attempt = 0; attempt < MAX_ADDRESS_ATTEMPTS; attempt++, index++)
            {
                String candidate = rpc.accountCreate(index, configEngine.getBankWalletId());
                if (candidate == null || RPC.ACCOUNT_CREATION_FAILED.equals(candidate)
                        || !Validator.validateAddress(candidate))
                {
                    plugin.getLogger().severe("Failed to derive bank address for '" + name + "'.");
                    return null;
                }
                if (!takenAddresses.contains(candidate))
                {
                    address = candidate;
                    break;
                }
            }

            if (address == null)
            {
                plugin.getLogger().severe("Could not derive an unused bank address for '" + name + "' after "
                        + MAX_ADDRESS_ATTEMPTS + " attempts — aborting.");
                return null;
            }

            BankRecord bank = new BankRecord(name, address, ownerUuid, System.currentTimeMillis(), index);

            if (!db.createBankRecord(bank))
            {
                plugin.getLogger().warning("Failed to persist bank record for '" + name + "'.");
                return null;
            }

            db.addBankMember(name, ownerUuid);

            plugin.getLogger().info("Bank '" + name + "' created at " + address);

            return bank;
        }
    }

    public boolean bankExists(String name)
    {
        return db.bankNameExists(name);
    }

    // -------------------------------------------------------------------------
    // Balance queries
    // -------------------------------------------------------------------------

    /**
     * Returns the current balance for the given bank, using a local cache to
     * avoid blocking RPC calls on repeated reads.
     *
     * <p>Cache misses result in a synchronous RPC fetch that populates the cache
     * for subsequent reads.  The cache is invalidated after every
     * {@link #deposit} / {@link #withdraw} call so the next read reflects the
     * on-chain state.</p>
     */
    public double getBankBalance(String bankName)
    {
        Double cached = bankBalanceCache.get(bankName);
        if (cached != null)
        {
            return cached;
        }

        BankRecord bank = db.getBankRecord(bankName);
        if (bank == null)
        {
            return 0.0;
        }

        double balance = rpc.getBalance(bank.getAddress());
        bankBalanceCache.put(bankName, balance);

        return balance;
    }

    public boolean bankHas(String bankName, double amount)
    {
        return getBankBalance(bankName) >= amount;
    }

    /**
     * Asynchronously fetches and caches the balances of every known bank.
     * Should be called once on plugin startup, after the bank wallet is verified.
     * The task is tracked so it is cancelled cleanly on plugin disable.
     */
    public void preloadBankBalancesAsync(TaskTracker taskTracker)
    {
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskAsynchronously(plugin, () ->
                {
                    List<String> names = db.getAllBankNames();
                    int cached = 0;

                    for (String name : names)
                    {
                        BankRecord bank = db.getBankRecord(name);

                        if (bank != null)
                        {
                            double balance = rpc.getBalance(bank.getAddress());
                            // Skip caching 0.0 — it's indistinguishable from an RPC
                            // failure (node down). The regular getBankBalance path will
                            // cache it on the next successful read.
                            if (balance > 0.0)
                            {
                                bankBalanceCache.put(name, balance);
                                cached++;
                            }
                        }
                    }
                    plugin.getLogger().info(
                            "Bank balance cache pre-loaded (" + cached + "/" + names.size() + " banks).");
                });
        taskTracker.track(task);
    }

    // -------------------------------------------------------------------------
    // Transfers
    // -------------------------------------------------------------------------

    /**
     * Moves {@code amount} from the master wallet into the bank sub-account.
     * Intended to be called after the calling plugin has already deducted the
     * same amount from the player via {@code Economy#withdrawPlayer}.
     */
    public boolean deposit(String bankName, double amount)
    {
        BankRecord bank = db.getBankRecord(bankName);
        if (bank == null)
        {
            return false;
        }

        if (!Double.isFinite(amount) || amount <= 0)
        {
            return false;
        }

        ReentrantLock lock = this.masterWalletLock;
        lock.lock();

        try
        {
            if (rpc.getBalance(rpc.getMasterWallet()) < amount)
            {
                return false;
            }

            String blockHash = rpc.sendTransaction(rpc.getMasterWallet(), bank.getAddress(), amount);
            tryAutoReceive(configEngine.getBankWalletId(), bank.getAddress(), blockHash);
            bankBalanceCache.remove(bankName);

            return true;
        }
        catch (TransactionError e)
        {
            plugin.getLogger().log(Level.WARNING, "Bank deposit failed for '" + bankName + "'.", e);

            return false;
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Moves {@code amount} from the bank sub-account into the master wallet.
     * Intended to be called before the calling plugin credits the same amount
     * to the player via {@code Economy#depositPlayer}.
     */
    public boolean withdraw(String bankName, double amount)
    {
        BankRecord bank = db.getBankRecord(bankName);
        if (bank == null)
        {
            return false;
        }

        if (!Double.isFinite(amount) || amount <= 0)
        {
            return false;
        }

        double liveBalance = rpc.getBalance(bank.getAddress());
        if (liveBalance < amount)
        {
            // Update the cache with the fresh value we just fetched — prevents a
            // stale (higher) balance from persisting after a failed withdrawal.
            bankBalanceCache.put(bankName, liveBalance);
            return false;
        }

        try
        {
            String blockHash = rpc.sendTransaction(bank.getAddress(), rpc.getMasterWallet(), amount,
                    configEngine.getBankWalletId());
            bankBalanceCache.remove(bankName);

            // Receiving creates a block on the master-wallet chain, so serialize it like a send.
            ReentrantLock lock = this.masterWalletLock;
            lock.lock();

            try
            {
                tryAutoReceive(null, rpc.getMasterWallet(), blockHash);
            }
            finally
            {
                lock.unlock();
            }

            return true;
        }
        catch (TransactionError e)
        {
            plugin.getLogger().log(Level.WARNING, "Bank withdrawal failed for '" + bankName + "'.", e);

            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Metadata queries
    // -------------------------------------------------------------------------

    public List<String> getBanks()
    {
        return db.getAllBankNames();
    }

    public boolean isBankOwner(String bankName, String playerUuid)
    {
        return db.isBankOwner(bankName, playerUuid);
    }

    public boolean isBankMember(String bankName, String playerUuid)
    {
        return db.isBankMember(bankName, playerUuid);
    }
}
