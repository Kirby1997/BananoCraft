package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class BaseDBConnector implements IDBConnector {

    protected HashMap<UUID, PlayerRecord> playerRecords = new HashMap<UUID, PlayerRecord>();

    public BaseDBConnector() {

    }

    @Override
    public PlayerRecord getPlayerRecord(Player player) {

        PlayerRecord playerRecord = playerRecords.getOrDefault(player.getUniqueId(), null);

        if(playerRecord == null) {

            playerRecord = loadPlayerRecord(player);

        }

        return playerRecord;

    }

    protected PlayerRecord loadPlayerRecord(Player player) {

        return null;

    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player) {

        return null;

    }

    @Override
    public PlayerRecord createPlayerRecord(Player player, String walletAddress) {

        if(!hasPlayerRecord(player)) {

            PlayerRecord playerRecord = new PlayerRecord(player.getUniqueId().toString(), player.getName(),
                    walletAddress, false);

            if(this.insertPlayerRecord(playerRecord)) {

                this.playerRecords.put(player.getUniqueId(), playerRecord);

                return playerRecord;

            }

        }

        return this.playerRecords.getOrDefault(player.getUniqueId(), null);

    }

    protected boolean insertPlayerRecord(PlayerRecord playerRecord) {
        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord) {
        return false;
    }

    @Override
    public void unloadPlayerRecord(Player player) {

        if(player != null
            && player.getUniqueId() != null) {

            UUID uuid = player.getUniqueId();

            if (this.playerRecords.containsKey(uuid)) {

                try {

                    PlayerRecord playerRecord = this.playerRecords.get(uuid);

                    updatePlayerRecord(playerRecord);

                    this.playerRecords.remove(uuid);

                }
                catch (Exception ex) {


                }

            }

        }

    }

    @Override
    public boolean hasPlayerRecord(Player player) {
        return false;
    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player) {
        return false;
    }

    @Override
    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer) {
        return false;
    }

    @Override
    public void close() {

    }
}
