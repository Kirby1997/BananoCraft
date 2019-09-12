package banano.bananominecraft.helloworld.commands;

import banano.bananominecraft.helloworld.EconomyFuncs;
import banano.bananominecraft.helloworld.RPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class nodeinfo implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "INSERT NODE IP HERE";

        //String nodeIP = getConfig().getString("IP");
        try{
            String payload = "{\"action\": \"block_count\"}";
            String response = RPC.sendPost(payload);




            if(sender instanceof Player){
                Player player = (Player) sender;
                //player.sendMessage("The node IP is: " + url);
                player.sendMessage(response);
                player.sendMessage("The server wallet is " + EconomyFuncs.getMasterWallet());
                player.sendMessage("It currently contains: " + RPC.getBalance(EconomyFuncs.getMasterWallet()));
            }
            else {
                System.out.println("The node IP is: " + url);
                System.out.println("Response: " + response);
                System.out.println("The server wallet is " + EconomyFuncs.getMasterWallet());
                System.out.println("It currently contains: " + RPC.getBalance(EconomyFuncs.getMasterWallet()));
            }
        }
        catch (Exception e){
            String response = "bob";
            System.out.println(response);
            e.printStackTrace();
        }









        return false;


    }
}
