package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface IDBConnector
{
    // -------------------------------------------------------------------------
    // Player records
    // -------------------------------------------------------------------------

    PlayerRecord getPlayerRecord(UUID playerId);
    PlayerRecord getPlayerRecord(Player player);
    PlayerRecord getOfflinePlayerRecord(OfflinePlayer player);
    boolean updatePlayerRecord(PlayerRecord playerRecord);
    PlayerRecord createPlayerRecord(Player player, String walletAddress);
    boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer);
    void unloadPlayerRecord(Player player);

    boolean hasPlayerRecord(Player player);
    boolean hasPlayerRecord(OfflinePlayer player);

    // -------------------------------------------------------------------------
    // Offline payments
    // -------------------------------------------------------------------------

    boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord);
    List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer);
    void deleteOfflinePaymentRecords(Player forPlayer);
    double getOfflinePaymentsTotal(Player forPlayer);

    // -------------------------------------------------------------------------
    // Freeze queries
    // -------------------------------------------------------------------------

    List<PlayerRecord> getFrozenPlayers();
    List<PlayerRecord> getUnfrozenPlayers();

    // -------------------------------------------------------------------------
    // Bank accounts
    // -------------------------------------------------------------------------

    BankRecord getBankRecord(String bankName);
    boolean createBankRecord(BankRecord bank);
    boolean bankNameExists(String bankName);
    boolean deleteBankRecord(String bankName);
    List<String> getAllBankNames();

    boolean isBankOwner(String bankName, String playerUuid);
    boolean isBankMember(String bankName, String playerUuid);
    boolean addBankMember(String bankName, String playerUuid);
    boolean removeBankMember(String bankName, String playerUuid);

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void close();
}
