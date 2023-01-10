package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EconomyFuncs {

    private final IDBConnector db;

    public EconomyFuncs(IDBConnector db) {
        this.db = db;
    }

    public boolean freezePlayer(Player player) {

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if(playerRecord != null) {

            return freezePlayer(playerRecord);

        }

        return false;

    }

    public boolean freezePlayer(OfflinePlayer player) {

        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if(playerRecord != null) {

            return freezePlayer(playerRecord);

        }

        return false;

    }

    private boolean freezePlayer(PlayerRecord playerRecord) {

        if(playerRecord != null) {

            playerRecord.setFrozen(true);
            this.db.updatePlayerRecord(playerRecord);

            return true;

        }

        return false;

    }

    public boolean freezePlayer(String playerName) {

        Player player = Bukkit.getPlayer(playerName);

        if(player != null) {

            return freezePlayer(player);

        }
        else {

            List<OfflinePlayer> offlinePlayers = new ArrayList<OfflinePlayer>();

            for(OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {

                if(offlinePlayer.getName().equalsIgnoreCase(playerName)) {

                    offlinePlayers.add(offlinePlayer);

                }

            }

            if(offlinePlayers.size() == 1) {

                PlayerRecord offlineRecord = this.db.getOfflinePlayerRecord(offlinePlayers.get(0));

                return freezePlayer(offlineRecord);

            }

        }

        return false;

    }

    public boolean unfreezePlayer(Player player) {

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if(playerRecord != null) {

            return unfreezePlayer(playerRecord);

        }

        return false;

    }

    public boolean unfreezePlayer(OfflinePlayer player) {

        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if(playerRecord != null) {

            return unfreezePlayer(playerRecord);

        }

        return false;

    }

    private boolean unfreezePlayer(PlayerRecord playerRecord) {

        if(playerRecord != null) {

            playerRecord.setFrozen(false);
            this.db.updatePlayerRecord(playerRecord);

            return true;

        }

        return false;

    }

    public boolean unfreezePlayer(String playerName) {

        Player player = Bukkit.getPlayer(playerName);

        if(player != null) {

            return unfreezePlayer(player);

        }
        else {

            List<OfflinePlayer> offlinePlayers = new ArrayList<OfflinePlayer>();

            for(OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {

                if(offlinePlayer.getName().equalsIgnoreCase(playerName)) {

                    offlinePlayers.add(offlinePlayer);

                }

            }

            if(offlinePlayers.size() == 1) {

                PlayerRecord offlineRecord = this.db.getOfflinePlayerRecord(offlinePlayers.get(0));

                return unfreezePlayer(offlineRecord);

            }

        }

        return false;

    }

    public Double getBalance(Player player){

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if(playerRecord != null) {

            String playerWallet = playerRecord.getWallet();

            if (playerWallet == null || playerRecord.isFrozen()) {
                return 0.0;
            }

            Double balance = RPC.getBalance(playerWallet);
            System.out.println(balance);

            return balance;

        }

        return 0d;
    }

    public boolean accountExists(Player player){

        return this.db.hasPlayerRecord(player);

    }

    public void accountCreate(Player player){

        String playerName = player.getName();

        try {

            if (!this.db.hasPlayerRecord(player)) {

                String wallet = RPC.accountCreate(-1);

                while(this.db.isAlreadyAssignedToOtherPlayer(wallet, player)) {
                    wallet =  RPC.accountCreate(-1);
                }

                PlayerRecord playerRecord = this.db.createPlayerRecord(player, wallet);

                if(playerRecord != null) {
                    System.out.println("Created new wallet for " + playerName);
                }
                else {
                    System.out.println("Could not create new wallet for " + playerName);
                }
            }
            else {

                PlayerRecord playerRecord = this.db.getPlayerRecord(player);

                if(playerRecord != null) {

                    System.out.println("Player wallet loaded for " + playerName);

                }
                else {

                    System.out.println("Player wallet could not be loaded for " + playerName + "!");

                }

            }

        }
        catch (Exception e){
            System.out.println(e);
        }
    }

    public void unloadAccount(Player player) {

        this.db.unloadPlayerRecord(player);

    }


    public boolean removeBalanceFP(Player player, double amount){
        // PLAYER TO MASTER WALLET
        double balance = getBalance(player);

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord == null
              || balance - amount < 0
              || playerRecord.isFrozen()) {

            return false;

        }

        try{

            String sender = playerRecord.getWallet();
            String block = RPC.sendTransaction(sender,RPC.getMasterWallet(),amount);

            return true;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }

    public boolean addBalanceTP(Player player, double amount){
        // MASTER WALLET TO PLAYER
        String sender = RPC.getMasterWallet();
        double serverBalance = RPC.getBalance(sender);

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if (playerRecord == null
              || serverBalance - amount < 0
              || isFrozen(player)) {

            return false;

        }

        try{
            String playerWallet = playerRecord.getWallet();
            String block = RPC.sendTransaction(sender,playerWallet,amount);
            return true;

        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }

    public boolean isFrozen(Player player) {

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if(playerRecord != null) {

            return playerRecord.isFrozen();

        }

        return false;

    }

    public String getWallet(Player player) {

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if(playerRecord != null) {

            return playerRecord.getWallet();

        }

        return "";

    }

    public boolean hasWallet(Player player) {

        PlayerRecord playerRecord = this.db.getPlayerRecord(player);

        if(playerRecord != null) {

            String wallet = playerRecord.getWallet();

            return wallet != null
                     && wallet.startsWith("ban_");

        }

        return false;

    }

    public boolean hasWallet(OfflinePlayer player) {

        PlayerRecord playerRecord = this.db.getOfflinePlayerRecord(player);

        if(playerRecord != null) {

            String wallet = playerRecord.getWallet();

            return wallet != null
                    && wallet.startsWith("ban_");

        }

        return false;

    }

}
