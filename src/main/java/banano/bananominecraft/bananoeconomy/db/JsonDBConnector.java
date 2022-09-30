package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import com.google.gson.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;

public class JsonDBConnector extends BaseDBConnector {

    private final String DATA_DIRECTORY = "data";
    private File dataLocation;
    private final Plugin plugin;

    private HashMap<String,String> claimedWallets = new HashMap<>(); // Wallet, PlayerUUID

    public JsonDBConnector(Plugin plugin) {

        this.plugin = plugin;

        initialiseDataDirectory();
        loadClaimedWallets();

    }

    private void loadClaimedWallets() {

        File dataSource = this.dataLocation;
        claimedWallets = new HashMap<>();

        new BukkitRunnable() {

            @Override
            public void run() {

                if(dataSource.isDirectory()) {

                    for (final File fileEntry : dataSource.listFiles()) {

                        Gson gson = new GsonBuilder().create();

                        try {
                            Reader fileReader = new FileReader(fileEntry);

                            PlayerRecord playerRecord = gson.fromJson(fileReader, PlayerRecord.class);

                            fileReader.close();

                            if (playerRecord != null) {

                                claimedWallets.put(playerRecord.getWallet(), playerRecord.getPlayerUUID());

                            }

                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }

                    }

                }

            }

        }.runTaskAsynchronously(plugin);

    }

    private void initialiseDataDirectory() {

        this.dataLocation = new File(plugin.getDataFolder(), DATA_DIRECTORY);

        if(!this.dataLocation.exists()) {

            this.dataLocation.mkdirs();

        }

    }

    @Override
    public void close() {

    }

    @Override
    protected PlayerRecord loadPlayerRecord(Player player) {

        return getPlayerRecord(player.getUniqueId(), true);

    }

    private PlayerRecord getPlayerRecord(UUID playerUUID, boolean cacheRecord) {

        if(this.playerRecords.containsKey(playerUUID)) {

            return this.playerRecords.get(playerUUID);

        }
        else {

            try {

                File file = new File(this.dataLocation, playerUUID.toString() + ".json");
                System.out.println("Loading player file from " + file.getAbsolutePath());

                if(!file.exists()) {

                    file.createNewFile();

                }

                Gson gson = new GsonBuilder().create();

                Reader fileReader = new FileReader(file);

                PlayerRecord playerRecord = gson.fromJson(fileReader, PlayerRecord.class);

                fileReader.close();

                if(playerRecord != null) {

                    if (cacheRecord
                          && !playerRecords.containsKey(playerUUID)) {

                        playerRecords.put(playerUUID, playerRecord);

                    }

                    System.out.println("Player loaded: " + playerRecord.getPlayerName());

                    return playerRecord;

                }

            }
            catch (IOException ex) {

                System.out.println("Loading player file failed!");
                ex.printStackTrace();

            }

        }

        return null;

    }

    @Override
    public PlayerRecord getOfflinePlayerRecord(OfflinePlayer player) {

        return getPlayerRecord(player.getUniqueId(), false);

    }

    @Override
    protected boolean insertPlayerRecord(PlayerRecord playerRecord) {

        try {

            File file = new File(this.dataLocation, playerRecord.getPlayerUUID() + ".json");

            System.out.println("Saving player file to " + file.getAbsolutePath());

            if(!file.exists()) {

                file.createNewFile();

            }

            Gson gson = new GsonBuilder().create();

            Writer fileWriter = new FileWriter(file, false);

            gson.toJson(playerRecord, fileWriter);

            fileWriter.flush();
            fileWriter.close();

            System.out.println("Player file '" + file.getName() + "' has been saved successfully.");

            return true;

        }
        catch (IOException ex) {

            System.out.println("Player file save has failed.");
            ex.printStackTrace();

        }

        return false;
    }

    @Override
    public boolean updatePlayerRecord(PlayerRecord playerRecord) {

        // In this case we're overwriting everything in the file anyway - same method as insert, so no need to duplicate!
        return insertPlayerRecord(playerRecord);

    }

    @Override
    public boolean hasPlayerRecord(Player player) {

        return hasPlayerRecord(player.getUniqueId());

    }

    @Override
    public boolean hasPlayerRecord(OfflinePlayer player) {

        return hasPlayerRecord(player.getUniqueId());

    }

    private boolean hasPlayerRecord(UUID playerUUID) {

        File file = new File(this.dataLocation, playerUUID + ".json");

        return file.exists();

    }

    public boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer) {

        String playerUUID = currentPlayer.getUniqueId().toString();

        return this.claimedWallets.containsKey(walletAddress)
                 && !this.claimedWallets.get(walletAddress).equalsIgnoreCase(playerUUID);

    }

}
