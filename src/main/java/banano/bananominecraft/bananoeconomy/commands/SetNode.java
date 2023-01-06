package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.RPC;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetNode extends BaseCommand implements CommandExecutor {

    private final ConfigEngine configEngine;

    public SetNode(ConfigEngine configEngine) {
        this.configEngine = configEngine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Player player = null;

        if(sender instanceof Player) {

            player = (Player) sender;

            if (player != null
                    && !player.hasPermission("BananoEconomy.changenode")
                    && !player.isOp()) {

                SendMessage(player, "You do not have permission to use this command!", ChatColor.RED);

                return false;

            }

        }

        // Did they call things correctly?
        if (args.length != 1) {

            SendMessage(player, "You need to provide the new node address in the format 'http://[url/ip address][:port]' with no spaces.", ChatColor.RED);
            return false;

        }

        String newNodeAddress = args[0];

        //Pattern pattern = Pattern.compile("/[-a-zA-Z0-9@:%_\+.~#?&//=]{2,256}\.[a-z]{2,4}\b(\/[-a-zA-Z0-9@:%_\+.~#?&//=]*)?/gi", Pattern.CASE_INSENSITIVE);
        //Matcher matcher = pattern.matcher(newNodeAddress);

        //if(!matcher.matches()) {

        //    SendMessage(player, "The provided address appears to be invalid! Please check it and try again.", ChatColor.RED);
        //    return false;

        //}

        configEngine.setNodeAddress(newNodeAddress);

        configEngine.save();

        SendMessage(player, "The node address has been set to '" + newNodeAddress + "'.", ChatColor.GREEN);

        String masterWallet = this.configEngine.getMasterWallet();

        if(!RPC.wallet_exists()){

            SendMessage(player, "The master wallet does not exist... attempting to configure the master wallet.", ChatColor.AQUA);

            RPC.walletCreate();

            masterWallet = RPC.accountCreate(0);

            this.configEngine.setMasterWallet(masterWallet);
            this.configEngine.save();

        }

        if(player != null) {

            player.spigot().sendMessage(MessageGenerator.generateClickableAddressMessage(this.configEngine, "The master wallet has been set to: ", masterWallet));

        }
        else {

            SendMessage(player, "The master wallet address is " + masterWallet, ChatColor.AQUA);

        }

        return true;
    }

}
