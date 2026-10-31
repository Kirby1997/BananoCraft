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
        if (!hasPlayerRecord(player))
        {
            PlayerRecord playerRecord = new PlayerRecord(
                    player.getUniqueId().toString(),
                    player.getName(),
                    walletAddress,
                    false);

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
