package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.DB;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class deposit implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;
            if (DB.isFrozen(player)){
                player.sendMessage("u r frozen!!!!!!!!!");
                return false;
            }
            String playerWallet = DB.getWallet(player);

            String walletURL = "https://creeper.banano.cc/explorer/account/" + playerWallet;
            player.spigot().sendMessage(( new ComponentBuilder( "Deposit bans to this address: " ).color( net.md_5.bungee.api.ChatColor.YELLOW ).append( playerWallet ).color( net.md_5.bungee.api.ChatColor.WHITE ).bold(true).create()));
            TextComponent addrlink = new TextComponent( "Click me to view your account in the block explorer" );
            addrlink.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, walletURL ) );
            addrlink.setUnderlined(true);
            player.spigot().sendMessage(addrlink);
        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }
}
