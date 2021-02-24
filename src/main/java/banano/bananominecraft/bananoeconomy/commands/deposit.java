package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.DB;
import banano.bananominecraft.bananoeconomy.Main;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URL;

public class deposit implements CommandExecutor {
    static Plugin plugin = Main.getPlugin(Main.class);

    private static URL getURL() throws Exception{
        URL url = new URL(plugin.getConfig().getString("exploreaccount"));
        return url;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){

        if (sender instanceof Player) {

            Player player = (Player) sender;
            if (DB.isFrozen(player)){
                player.sendMessage("u r frozen!!!!!!!!!");
                return false;
            }
            String playerWallet = DB.getWallet(player);
            try{URL walletURL = new URL( getURL() + playerWallet);
                player.spigot().sendMessage(( new ComponentBuilder( "Deposit bans to this address: " ).color( net.md_5.bungee.api.ChatColor.YELLOW ).append( playerWallet ).color( net.md_5.bungee.api.ChatColor.WHITE ).bold(true).create()));
                TextComponent addrlink = new TextComponent( "Click me to view your account in the block explorer" );
                addrlink.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, walletURL.toString() ) );
                addrlink.setUnderlined(true);
                player.spigot().sendMessage(addrlink);}
            catch (Exception e){
                System.out.println(e);
            }


        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }
}
