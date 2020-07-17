package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.DB;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnFreeze implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] playersToUnFreeze) {
        if (!commandSender.isOp()){
            commandSender.sendMessage("You do not have permission!!");
            return false;
        }
        if (playersToUnFreeze.length < 1) {
            return false;
        }

        for (String player : playersToUnFreeze) {
            Player p = Bukkit.getPlayer(player);
            if (p == null){
                boolean ret = DB.unfreezePlayer(player);
                if (!ret) {
                    commandSender.sendMessage(String.format("%s no existy.", player));
                    return false;
                }
            }else{
                unfreezePlayer(p);
            }
            commandSender.sendMessage(String.format("%s's account has been unfrozen!", p.getName()));
        }
        return true;
    }

    public boolean unfreezePlayer(Player player){
        return DB.unfreezePlayer(player);
    }
}
