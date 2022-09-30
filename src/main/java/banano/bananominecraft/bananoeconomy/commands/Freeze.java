package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Freeze implements CommandExecutor {

    private final EconomyFuncs economyFuncs;

    public Freeze(EconomyFuncs economyFuncs) {
        this.economyFuncs = economyFuncs;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] playersToFreeze) {

        if (playersToFreeze.length < 1) {
            return false;
        }

        for (String player : playersToFreeze) {

            Player p = Bukkit.getPlayer(player);

            if (p == null){

                boolean ret = economyFuncs.freezePlayer(player);

                if (!ret) {

                    commandSender.sendMessage(String.format("%s could not be found!", player));
                    return false;

                }

            }
            else{

                economyFuncs.freezePlayer(p);

            }

            commandSender.sendMessage(String.format("%s's account has been frozen!", player));

        }

        return true;
    }

}
