package banano.bananominecraft.helloworld.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class poop implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (sender instanceof Player) {

                Player player = (Player) sender;
                if(player.hasPermission("HelloWorld.poop")) {
                    player.sendMessage("Your poop smells nice");
                }
                else{
                    player.sendMessage("EWWIE. You don't have permission to poo");
                }
            } else {
                System.out.println("You need to be a player");
            }
        return false;
    }
}