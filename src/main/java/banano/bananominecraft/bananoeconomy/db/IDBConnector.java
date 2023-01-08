package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface IDBConnector {

    PlayerRecord getPlayerRecord(Player player);
    PlayerRecord getOfflinePlayerRecord(OfflinePlayer player);
    boolean updatePlayerRecord(PlayerRecord playerRecord);
    PlayerRecord createPlayerRecord(Player player, String walletAddress);
    boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer);
    void unloadPlayerRecord(Player player);

    boolean hasPlayerRecord(Player player);
    boolean hasPlayerRecord(OfflinePlayer player);

    boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord);
    List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer);
    void deleteOfflinePaymentRecords(Player forPlayer);
    double getOfflinePaymentsTotal(Player forPlayer);

    List<PlayerRecord> getFrozenPlayers();
    List<PlayerRecord> getUnfrozenPlayers();

    void close();

}
