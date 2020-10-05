package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.RPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class NodeInfo implements CommandExecutor {

    private final JavaPlugin plugin;

    public NodeInfo(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        //String nodeIP = getConfig().getString("IP");
        try{
            List<String> payload = RPC.getBlockCount();
            String checked = payload.get(0);
            String unchecked = payload.get(1);


            if(sender instanceof Player){
                Player player = (Player) sender;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (sender.isOp()) {
                        try {
                            player.sendMessage("The node IP is: " + RPC.getURL());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    player.sendMessage("Checked Blocks: " + checked + " - Unchecked Blocks: " + unchecked);
                    player.sendMessage("The server wallet is " + RPC.getMasterWallet());
                    player.sendMessage("It currently contains: " + RPC.getBalance(RPC.getMasterWallet()));
                });
            }
           if(sender instanceof ConsoleCommandSender) {
                System.out.println("The node IP is: " + RPC.getURL());
                System.out.println("Checked Blocks: " + checked + " - Unchecked Blocks: " + unchecked);
                System.out.println("The server wallet is " + RPC.getMasterWallet());
                System.out.println("It currently contains: " + RPC.getBalance(RPC.getMasterWallet()));
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }

        return false;


    }
}
