package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

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

    void close();

}
