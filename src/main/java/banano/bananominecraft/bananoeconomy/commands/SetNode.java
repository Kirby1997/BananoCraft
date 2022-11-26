package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.RPC;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetNode implements CommandExecutor {

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
                    && !player.hasPermission("bananoeconomy.changenode")
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

        return false;
    }

    protected void SendMessage(Player player, String message, ChatColor messageColour) {
        if(player != null) {
            player.sendMessage(messageColour + message);
        }
        else {
            System.out.println(message);
        }
    }

}
