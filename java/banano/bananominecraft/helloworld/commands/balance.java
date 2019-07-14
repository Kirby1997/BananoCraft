package banano.bananominecraft.helloworld.commands;

import banano.bananominecraft.helloworld.DB;
import banano.bananominecraft.helloworld.RPC;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class balance implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RPC rpc = new RPC();
        if (sender instanceof Player) {

            Player player = (Player) sender;
            String UUID = player.getUniqueId().toString();
            String playerWallet = DB.getWallet(UUID);
            int balance = rpc.getBalance(playerWallet);
            player.sendMessage(ChatColor.YELLOW + "Your current balance is: " + balance);

        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }
}
