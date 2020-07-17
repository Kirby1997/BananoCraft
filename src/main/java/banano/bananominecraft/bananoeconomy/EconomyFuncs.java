package banano.bananominecraft.bananoeconomy;

import org.bukkit.entity.Player;

public class EconomyFuncs {


    public static Double getBalance(Player player){

        String playerWallet = DB.getWallet(player);
        if (playerWallet == null)
            return 0.0;

        Double balance = RPC.getBalance(playerWallet);
        System.out.println(balance);

        return balance;
    }



    public static void accountCreate(Player player){

        String playerName = player.getName();

        try {
            if (!DB.accountExists(player)) {
                String wallet =  RPC.accountCreate(-1);
                DB.storeAccount(player, wallet);
                System.out.println("Created new wallet for " + playerName);
            }

        }
        catch (Exception e){
            System.out.println(e);
        }
    }


    public static boolean removeBalanceFP(Player player, double amount){
    // PLAYER TO MASTER WALLET
        double balance = getBalance(player);

        if (balance - amount < 0) return false;

        try{
            String sender = DB.getWallet(player);
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
        double serverBalance = RPC.getBalance(sender);

        if (serverBalance - amount < 0) return false;

        try{
            String playerWallet = DB.getWallet(player);
            String block = RPC.sendTransaction(sender,playerWallet,amount);
            return true;

        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }



}
