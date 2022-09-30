package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;

public class Balance implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;

    public Balance(Plugin plugin, EconomyFuncs economyFuncs) {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

                try {

                    final Double balance = economyFuncs.getBalance(player);
                    final DecimalFormat df = new DecimalFormat("#.##");

                    player.sendMessage(ChatColor.YELLOW + "Your current balance is: " + df.format(balance) + " bans");

                }
                catch (Exception ex) {

                    player.sendMessage(ChatColor.RED + "An error occurred retrieving your balance! Please try again in a moment.");

                }

            });

        } else {

            System.out.println("You need to be a player.");

        }

        return false;
    }



}
