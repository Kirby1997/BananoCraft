package banano.bananominecraft.helloworld.commands;

import banano.bananominecraft.helloworld.EconomyFuncs;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class balance implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;

            Double balance = EconomyFuncs.getBalance(player);
            player.sendMessage(ChatColor.YELLOW + "Your current balance is: " + balance);

        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }



}
