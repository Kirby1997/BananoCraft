package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.RPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class nodeinfo implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "INSERT NODE IP HERE";

        //String nodeIP = getConfig().getString("IP");
        try{
            List<String> payload = RPC.getBlockCount();
            String checked = payload.get(0);
            String unchecked = payload.get(1);


            if(sender instanceof Player){
                Player player = (Player) sender;
                player.sendMessage("The node IP is: " + RPC.getURL());
                player.sendMessage("Checked Blocks: " + checked + " - Unchecked Blocks: " + unchecked);
                player.sendMessage("The server wallet is " + RPC.getMasterWallet());
                player.sendMessage("It currently contains: " + RPC.getBalance(RPC.getMasterWallet()));
            }
            else {
                System.out.println("The node IP is: " + RPC.getURL());
                System.out.println("Checked Blocks: " + checked + " - Unchecked Blocks: " + unchecked);
                System.out.println("The server wallet is " + RPC.getMasterWallet());
                System.out.println("It currently contains: " + RPC.getBalance(RPC.getMasterWallet()));
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
