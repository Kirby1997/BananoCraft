package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final int    BANK_NAME_MAX_LENGTH = 64;
    private static final String BANK_NAME_PATTERN    = "[a-zA-Z0-9_-]+";

    public BankService(Plugin plugin, ConfigEngine configEngine, RPC rpc, IDBConnector db)
    {
        this.plugin       = plugin;
        this.configEngine = configEngine;
        this.rpc          = rpc;
        this.db           = db;
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

        String address = rpc.accountCreate(-1, configEngine.getBankWalletId());
        if (RPC.ACCOUNT_CREATION_FAILED.equals(address))
        {
            plugin.getLogger().severe("Failed to derive bank address for '" + name + "'.");
            return null;
        }

        BankRecord bank = new BankRecord(name, address, ownerUuid, System.currentTimeMillis());

        if (!db.createBankRecord(bank))
        {
            plugin.getLogger().warning("Failed to persist bank record for '" + name + "'.");
            return null;
        }

        db.addBankMember(name, ownerUuid);

        plugin.getLogger().info("Bank '" + name + "' created at " + address);

        return bank;
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

        if (rpc.getBalance(rpc.getMasterWallet()) < amount)
        {
            return false;
        }

        try
        {
            rpc.sendTransaction(rpc.getMasterWallet(), bank.getAddress(), amount);
            bankBalanceCache.remove(bankName);

            return true;
        }
        catch (TransactionError e)
        {
            plugin.getLogger().log(Level.WARNING, "Bank deposit failed for '" + bankName + "'.", e);

            return false;
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
            rpc.sendTransaction(bank.getAddress(), rpc.getMasterWallet(), amount,
                    configEngine.getBankWalletId());
            bankBalanceCache.remove(bankName);

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
