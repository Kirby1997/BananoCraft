package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;

public class Balance implements CommandExecutor {
    private final JavaPlugin plugin;

    public Balance(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {
            Player player = (Player) sender;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                final Double balance = EconomyFuncs.getBalance(player);
                final DecimalFormat df = new DecimalFormat("#.##");
                player.sendMessage(ChatColor.YELLOW + "Your current balance is: " + df.format(balance));
            });
        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }



}
