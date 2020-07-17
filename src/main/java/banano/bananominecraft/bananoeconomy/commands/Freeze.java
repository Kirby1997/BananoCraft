package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.DB;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Freeze implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] playersToFreeze) {
        if (!commandSender.isOp()){
            commandSender.sendMessage("You do not have permission!!");
            return false;
        }
        if (playersToFreeze.length < 1) {
            return false;
        }

        for (String player : playersToFreeze) {
            Player p = Bukkit.getPlayer(player);
            if (p == null){
                OfflinePlayer op = Bukkit.getOfflinePlayer(player);
                if (op == null || !op.hasPlayedBefore()){
                    System.out.printf("%s no existy.", player);
                    return false;
                }
                p = op.getPlayer();
            }
            freezePlayer(p);
            commandSender.sendMessage(String.format("%s's account has been frozen!", p.getName()));
        }

        return true;
    }

    public boolean freezePlayer(Player player){
        return DB.freezePlayer(player);
    }
}
