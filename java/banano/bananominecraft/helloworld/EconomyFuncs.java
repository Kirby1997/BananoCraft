package banano.bananominecraft.helloworld;

import org.bukkit.entity.Player;

public class EconomyFuncs {



    public static Double getBalance(Player player){
        String UUID = player.getUniqueId().toString();
        String playerWallet = DB.getWallet(UUID);
        Double balance = RPC.getBalance(playerWallet);

        return balance;
    }



    public static void accountCreate(Player player){
        String UUID = player.getUniqueId().toString();
        String playerName = player.getName();
        try {

            if (!DB.accountExists(UUID)) {
                System.out.println(DB.accountExists(UUID));
                String wallet =  RPC.accountCreate();
                DB.storeAccount(player, wallet);
                System.out.println("Created new wallet for " + playerName);
            }
            else{
                System.out.println(playerName + " already has an account");
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
            String block = RPC.sendTransaction(sender,EconomyFuncs.getMasterWallet(),(int)amount);
            System.out.println(block);
            return true;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }

    public static boolean addBalanceTP(Player player, double amount){
        // MASTER WALLET TO PLAYER
        String sender = getMasterWallet();
        String UUID = player.getUniqueId().toString();
        double serverBalance = RPC.getBalance(sender);
        System.out.println("Server balance: " + serverBalance);
        if (serverBalance - amount < 0) return false;

        try{
            String playerWallet = DB.getWallet(UUID);
            System.out.println("Player wallet: " + playerWallet);
            String block = RPC.sendTransaction(sender,playerWallet,(int)amount);
            System.out.println(block);
            return true;
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }


    }


    public static String getMasterWallet(){

        return "ACCOUNT NUMBER 0 OF WALLET";

    }
}
