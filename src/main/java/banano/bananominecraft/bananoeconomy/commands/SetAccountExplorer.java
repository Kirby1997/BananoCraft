package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetAccountExplorer extends BaseCommand implements CommandExecutor {

    private final ConfigEngine configEngine;

    public SetAccountExplorer(ConfigEngine configEngine) {
        this.configEngine = configEngine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Player player = null;

        if(sender instanceof Player) {

            player = (Player) sender;

            if (player != null
                    && !player.hasPermission("BananoEconomy.changeaccountexplorer")
                    && !player.isOp()) {

                SendMessage(player, "You do not have permission to use this command!", ChatColor.RED);

                return false;

            }

        }

        // Did they call things correctly?
        if (args.length != 1) {

            SendMessage(player, "You need to provide the new account explorer address in the format 'http://[url/ip address][:port]/' with no spaces.", ChatColor.RED);
            return false;

        }

        String newExplorerAddress = args[0];

        if(!newExplorerAddress.endsWith("/")) {

            newExplorerAddress += "/";

        }

        configEngine.setExplorerAccount(newExplorerAddress);

        configEngine.save();

        SendMessage(player, "The account explorer address has been set to '" + newExplorerAddress + "'.", ChatColor.GREEN);

        return true;

    }

}
