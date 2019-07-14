package banano.bananominecraft.helloworld.commands;

import banano.bananominecraft.helloworld.RPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class nodeinfo implements CommandExecutor {
    RPC rpc = new RPC();
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "http://127.0.0.1:7072";
        Player player = (Player) sender;
        //String nodeIP = getConfig().getString("IP");
        player.sendMessage("The node IP is: " + url);
        String response;
        try{
            String payload = "{\"action\": \"block_count\"}";
            response = rpc.sendPost(payload);
            player.sendMessage(response);
            System.out.println("Response\n\n\n" + response);
        }
        catch (Exception e){
            response = "bob";
            player.sendMessage(response);
            e.printStackTrace();
        }
        return false;
    }
}
