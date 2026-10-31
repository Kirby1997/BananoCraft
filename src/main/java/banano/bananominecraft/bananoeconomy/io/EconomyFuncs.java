package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.validation.Validator;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
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
    /** Maximum wallet-creation retries before giving up, to prevent an infinite loop. */
    private static final int MAX_WALLET_RETRIES = 10;

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

    public EconomyFuncs(Plugin plugin, IDBConnector db, RPC rpc, ConfigEngine configEngine)
    {
        this.plugin = plugin;
        this.db = db;
        this.rpc = rpc;
        this.configEngine = configEngine;
    }

    /** Returns the (lazily created) lock serializing sends from the given wallet address. */
    private ReentrantLock lockForWallet(String wallet)
    {
        return this.walletLocks.computeIfAbsent(wallet, key -> new ReentrantLock(true));
    }

    /**
     * Best-effort: the transfer has already succeeded regardless of whether this does. If it
     * fails, the block is simply left pending — it will still be picked up later by the node's
     * own auto-receive, the confirmation websocket, or a manual /receive.
     */
    private void tryAutoReceive(String recipientWallet, String blockHash)
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
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

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
            playerRecord.setFrozen(true);
            this.db.updatePlayerRecord(playerRecord);

            return true;
        }

        return false;
    }

    public boolean freezePlayer(String playerName)
    {
        Player player = Bukkit.getPlayer(playerName);

        if (player != null)
        {
            return freezePlayer(player);
        }
        else
        {
            List<OfflinePlayer> offlinePlayers = new ArrayList<>();

            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers())
            {
                if (offlinePlayer.getName().equalsIgnoreCase(playerName))
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
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

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
            playerRecord.setFrozen(false);
            this.db.updatePlayerRecord(playerRecord);

            return true;
        }

        return false;
    }

    public boolean unfreezePlayer(String playerName)
    {
        Player player = Bukkit.getPlayer(playerName);

        if (player != null)
        {
            return unfreezePlayer(player);
        }
        else
        {
            List<OfflinePlayer> offlinePlayers = new ArrayList<>();

            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers())
            {
                if (offlinePlayer.getName().equalsIgnoreCase(playerName))
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
        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if (playerRecord == null || playerRecord.isFrozen())
        {
            return false;
        }

        ReentrantLock lock = lockForWallet(playerRecord.getWallet());
        lock.lock();

        try
        {
            double balance = rpc.getBalance(playerRecord.getWallet());

            if (balance - amount < 0)
            {
                return false;
            }

            try
            {
                String blockHash = rpc.sendTransaction(playerRecord.getWallet(), rpc.getMasterWallet(), amount);
                tryAutoReceive(rpc.getMasterWallet(), blockHash);
                return true;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "removeBalanceFP failed for offline player.", e);
                return false;
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public boolean addBalanceTP(OfflinePlayer player, double amount)
    {
        this.masterWalletLock.lock();

        try
        {
            String sender = rpc.getMasterWallet();
            double serverBalance = rpc.getBalance(sender);
            PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

            if (playerRecord == null || serverBalance - amount < 0 || playerRecord.isFrozen())
            {
                return false;
            }

            try
            {
                String blockHash = rpc.sendTransaction(sender, playerRecord.getWallet(), amount);
                tryAutoReceive(playerRecord.getWallet(), blockHash);
                return true;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "addBalanceTP failed for offline player.", e);
                return false;
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
                String wallet = rpc.accountCreate(-1);

                if (!wallet.equalsIgnoreCase(RPC.ACCOUNT_CREATION_FAILED))
                {
                    int retries = 0;
                    while (this.db.isAlreadyAssignedToOtherPlayer(wallet, player))
                    {
                        if (++retries >= MAX_WALLET_RETRIES)
                        {
                            plugin.getLogger().severe("Could not find a unique wallet for " + playerName
                                    + " after " + MAX_WALLET_RETRIES + " attempts — aborting.");
                            return false;
                        }
                        wallet = rpc.accountCreate(-1);
                    }

                    PlayerRecord playerRecord = this.db.createPlayerRecord(player, wallet);

                    if (playerRecord != null)
                    {
                        plugin.getLogger().info("Created new wallet for " + playerName);
                        return true;
                    }
                    else
                    {
                        plugin.getLogger().warning("Could not create new wallet for " + playerName);
                    }
                }
                else
                {
                    plugin.getLogger().warning("Could not create new wallet for " + playerName);
                }
            }
            else
            {
                PlayerRecord playerRecord = this.db.getPlayerRecord(player);

                if (playerRecord != null)
                {
                    plugin.getLogger().info("Player wallet loaded for " + playerName);
                    return true;
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

    public void unloadAccount(Player player)
    {
        this.db.unloadPlayerRecord(player);
    }

    public boolean removeBalanceFP(Player player, double amount)
    {
        // PLAYER TO MASTER WALLET
        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord == null || playerRecord.isFrozen())
        {
            return false;
        }

        ReentrantLock lock = lockForWallet(playerRecord.getWallet());
        lock.lock();

        try
        {
            double balance = getBalance(player);

            if (balance - amount < 0)
            {
                return false;
            }

            try
            {
                String sender = playerRecord.getWallet();
                String blockHash = rpc.sendTransaction(sender, rpc.getMasterWallet(), amount);
                tryAutoReceive(rpc.getMasterWallet(), blockHash);
                return true;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "removeBalanceFP failed for player.", e);
                return false;
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public boolean addBalanceTP(Player player, double amount)
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
                return false;
            }

            try
            {
                String playerWallet = playerRecord.getWallet();
                String blockHash = rpc.sendTransaction(sender, playerWallet, amount);
                tryAutoReceive(playerWallet, blockHash);
                return true;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "addBalanceTP failed for player.", e);
                return false;
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
