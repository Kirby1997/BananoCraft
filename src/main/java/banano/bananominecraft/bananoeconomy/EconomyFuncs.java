package banano.bananominecraft.bananoeconomy;

import org.bukkit.entity.Player;

public class EconomyFuncs {


    public static Double getBalance(Player player){
        String UUID = player.getUniqueId().toString();
        String playerWallet = DB.getWallet(UUID);
        Double balance = RPC.getBalance(playerWallet);
        System.out.println(balance);
        return balance;
    }



    public static void accountCreate(Player player){
        String UUID = player.getUniqueId().toString();
        String playerName = player.getName();
        try {

            if (!DB.accountExists(UUID)) {

                String wallet =  RPC.accountCreate(-1);
                DB.storeAccount(player, wallet);
                System.out.println("Created new wallet for " + playerName);
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }


    public static boolean removeBalanceFP(Player player, double amount){
    // PLAYER TO MASTER WALLET
        double balance = getBalance(player);
        String UUID = player.getUniqueId().toString();
        if (balance - amount < 0) return false;

        try{
            String sender = DB.getWallet(UUID);
            String block = RPC.sendTransaction(sender,RPC.getMasterWallet(),amount);

            return true;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }

    public static boolean addBalanceTP(Player player, double amount){
        // MASTER WALLET TO PLAYER
        String sender = RPC.getMasterWallet();
        String UUID = player.getUniqueId().toString();
        double serverBalance = RPC.getBalance(sender);

        if (serverBalance - amount < 0) return false;

        try{
            String playerWallet = DB.getWallet(UUID);

            String block = RPC.sendTransaction(sender,playerWallet,amount);

            return true;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }



}
