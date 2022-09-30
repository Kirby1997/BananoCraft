package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnFreeze implements CommandExecutor {

    private final EconomyFuncs economyFuncs;

    public UnFreeze(EconomyFuncs economyFuncs) {
        this.economyFuncs = economyFuncs;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] playersToUnFreeze) {
        if (playersToUnFreeze.length < 1) {
            return false;
        }

        for (String player : playersToUnFreeze) {

            Player p = Bukkit.getPlayer(player);

            if (p == null) {

                boolean ret = economyFuncs.unfreezePlayer(player);

                if (!ret) {
                    commandSender.sendMessage(String.format("%s could not be identified.", player));
                    return false;
                }

            }
            else {
                economyFuncs.unfreezePlayer(p);
            }

            commandSender.sendMessage(String.format("%s's account has been unfrozen!", player));
        }
        return true;
    }
}
