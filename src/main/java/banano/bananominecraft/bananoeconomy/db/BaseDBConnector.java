package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BaseDBConnector implements IDBConnector
{
    protected ConcurrentHashMap<UUID, PlayerRecord>       playerRecords = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, BankRecord>       bankRecords   = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, Set<String>>      bankMembers   = new ConcurrentHashMap<>();

    public BaseDBConnector() {}

    // -------------------------------------------------------------------------
    // Player records
    // -------------------------------------------------------------------------

    @Override
    public PlayerRecord getPlayerRecord(UUID playerId)
    {
        return playerRecords.getOrDefault(playerId, null);
    }

    @Override
    public PlayerRecord getPlayerRecord(Player player)
    {
        PlayerRecord playerRecord = playerRecords.getOrDefault(player.getUniqueId(), null);

        if (playerRecord == null)
        {
            playerRecord = loadPlayerRecord(player);
        }

        return playerRecord;
    }

    protected PlayerRecord loadPlayerRecord(Player player)
    {
        return null;
    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player)
    {
        return null;
    }

    @Override
    public PlayerRecord createPlayerRecord(Player player, String walletAddress)
    {
        return createPlayerRecord(player, walletAddress, null);
    }

    @Override
    public PlayerRecord createPlayerRecord(Player player, String walletAddress, Integer walletIndex)
    {
        if (!hasPlayerRecord(player))
        {
            PlayerRecord playerRecord = new PlayerRecord(
                    player.getUniqueId().toString(),
                    player.getName(),
                    walletAddress,
                    false,
                    walletIndex);

            if (this.insertPlayerRecord(playerRecord))
            {
                this.playerRecords.put(player.getUniqueId(), playerRecord);
                return playerRecord;
            }
        }

        return this.playerRecords.getOrDefault(player.getUniqueId(), null);
    }

    protected boolean insertPlayerRecord(PlayerRecord playerRecord)
    {
        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord)
    {
        return false;
    }

    /**
     * Persists a new wallet address via {@link #updatePlayerRecord(PlayerRecord)}
     * (implemented per backend) and, only once that succeeds, updates the cached
     * record so a failed write never leaves the cache out of step with storage.
     */
    @Override
    public boolean updatePlayerWallet(Player player, String wallet)
    {
        return updatePlayerWallet(player, wallet, null);
    }

    @Override
    public boolean updatePlayerWallet(Player player, String wallet, Integer walletIndex)
    {
        if (player == null || wallet == null || wallet.isEmpty())
        {
            return false;
        }

        PlayerRecord existing = getPlayerRecord(player);

        if (existing == null)
        {
            return false;
        }

        PlayerRecord updated = new PlayerRecord(
                existing.getPlayerUUID(),
                existing.getPlayerName(),
                wallet,
                existing.isFrozen(),
                walletIndex != null ? walletIndex : existing.getWalletIndex());

        if (!updatePlayerRecord(updated))
        {
            return false;
        }

        // Mutate the cached instance in place so anyone already holding it sees the new wallet.
        existing.setWallet(wallet);
        if (walletIndex != null)
        {
            existing.setWalletIndex(walletIndex);
        }
        this.playerRecords.put(player.getUniqueId(), existing);

        return true;
    }

    @Override
    public void unloadPlayerRecord(Player player)
    {
        if (player != null && player.getUniqueId() != null)
        {
            UUID uuid = player.getUniqueId();

            if (this.playerRecords.containsKey(uuid))
            {
                try
                {
                    PlayerRecord playerRecord = this.playerRecords.get(uuid);
                    updatePlayerRecord(playerRecord);
                    this.playerRecords.remove(uuid);
                }
                catch (Exception ex)
                {
                    java.util.logging.Logger.getLogger(getClass().getName())
                            .log(java.util.logging.Level.WARNING,
                                    "Failed to save/unload player record for UUID: " + uuid, ex);
                }
            }
        }
    }

    @Override
    public boolean hasPlayerRecord(Player player)
    {
        return false;
    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player)
    {
        return false;
    }

    @Override
    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer)
    {
        return false;
    }

    // -------------------------------------------------------------------------
    // Wallet derivation indices — cache-backed defaults (subclasses query/persist)
    // -------------------------------------------------------------------------

    @Override
    public int getMaxPlayerWalletIndex()
    {
        int max = 0;
        for (PlayerRecord record : playerRecords.values())
        {
            if (record.getWalletIndex() != null)
            {
                max = Math.max(max, record.getWalletIndex());
            }
        }
        return max;
    }

    @Override
    public int getMaxBankWalletIndex()
    {
        int max = 0;
        for (BankRecord record : bankRecords.values())
        {
            if (record.getWalletIndex() != null)
            {
                max = Math.max(max, record.getWalletIndex());
            }
        }
        return max;
    }

    @Override
    public Integer getPlayerWalletIndex(String address)
    {
        if (address == null)
        {
            return null;
        }
        for (PlayerRecord record : playerRecords.values())
        {
            if (address.equals(record.getWallet()))
            {
                return record.getWalletIndex();
            }
        }
        return null;
    }

    @Override
    public Integer getBankWalletIndex(String address)
    {
        if (address == null)
        {
            return null;
        }
        for (BankRecord record : bankRecords.values())
        {
            if (address.equals(record.getAddress()))
            {
                return record.getWalletIndex();
            }
        }
        return null;
    }

    @Override
    public List<PlayerRecord> getPlayerRecordsMissingWalletIndex()
    {
        return this.playerRecords.values().stream().filter(x -> x.getWalletIndex() == null).toList();
    }

    @Override
    public boolean setPlayerWalletIndex(String playerUuid, int walletIndex)
    {
        if (playerUuid == null || !persistPlayerWalletIndex(playerUuid, walletIndex))
        {
            return false;
        }

        try
        {
            PlayerRecord cached = this.playerRecords.get(UUID.fromString(playerUuid));
            if (cached != null)
            {
                cached.setWalletIndex(walletIndex);
            }
        }
        catch (IllegalArgumentException ignored)
        {
            // not a UUID: nothing can be cached under it
        }
        return true;
    }

    /** Writes the index to storage. Backends override; the default cannot persist anything. */
    protected boolean persistPlayerWalletIndex(String playerUuid, int walletIndex)
    {
        return false;
    }

    @Override
    public boolean setBankWalletIndex(String bankName, int walletIndex)
    {
        if (bankName == null || !persistBankWalletIndex(bankName, walletIndex))
        {
            return false;
        }

        BankRecord cached = this.bankRecords.get(bankName);
        if (cached != null)
        {
            cached.setWalletIndex(walletIndex);
        }
        return true;
    }

    /** Writes the index to storage. Backends override; the default cannot persist anything. */
    protected boolean persistBankWalletIndex(String bankName, int walletIndex)
    {
        return false;
    }

    // -------------------------------------------------------------------------
    // Offline payments
    // -------------------------------------------------------------------------

    @Override
    public boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord)
    {
        return false;
    }

    @Override
    public List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer)
    {
        return new ArrayList<>();
    }

    @Override
    public void deleteOfflinePaymentRecords(Player forPlayer) {}

    @Override
    public double getOfflinePaymentsTotal(Player forPlayer)
    {
        return 0;
    }

    // -------------------------------------------------------------------------
    // Freeze queries
    // -------------------------------------------------------------------------

    @Override
    public List<PlayerRecord> getFrozenPlayers()
    {
        return this.playerRecords.values().stream().filter(x -> x.isFrozen()).toList();
    }

    @Override
    public List<PlayerRecord> getUnfrozenPlayers()
    {
        return this.playerRecords.values().stream().filter(x -> !x.isFrozen()).toList();
    }

    // -------------------------------------------------------------------------
    // Bank accounts — cache-backed defaults (subclasses add persistence)
    // -------------------------------------------------------------------------

    @Override
    public BankRecord getBankRecord(String bankName)
    {
        return bankRecords.getOrDefault(bankName, null);
    }

    @Override
    public boolean createBankRecord(BankRecord bank)
    {
        return false;
    }

    @Override
    public boolean bankNameExists(String bankName)
    {
        return bankRecords.containsKey(bankName);
    }

    @Override
    public boolean deleteBankRecord(String bankName)
    {
        return false;
    }

    @Override
    public List<String> getAllBankNames()
    {
        return new ArrayList<>(bankRecords.keySet());
    }

    @Override
    public boolean isBankOwner(String bankName, String playerUuid)
    {
        BankRecord bank = bankRecords.get(bankName);
        return bank != null && bank.getOwnerUuid().equals(playerUuid);
    }

    @Override
    public boolean isBankMember(String bankName, String playerUuid)
    {
        Set<String> members = bankMembers.get(bankName);
        return members != null && members.contains(playerUuid);
    }

    @Override
    public boolean addBankMember(String bankName, String playerUuid)
    {
        return false;
    }

    @Override
    public boolean removeBankMember(String bankName, String playerUuid)
    {
        return false;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close() {}
}
