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
                   commandSender.sendMessage(String.format("%s no existy.", player));
                    return false;
                }
                p = op.getPlayer();
            }
            unfreezePlayer(p);
            commandSender.sendMessage(String.format("%s's account has been unfrozen!", p.getName()));
        }

        return true;
    }

    public boolean unfreezePlayer(Player player){
        return DB.unfreezePlayer(player);
    }
}
