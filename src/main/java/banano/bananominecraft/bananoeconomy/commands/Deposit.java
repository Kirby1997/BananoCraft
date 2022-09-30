package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.RPC;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URL;

public class Deposit implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;

    public Deposit(Plugin plugin, EconomyFuncs economyFuncs) {

        this.plugin = plugin;
        this.economyFuncs = economyFuncs;

    }

    private URL getURL() throws Exception {
        URL url = new URL(this.plugin.getConfig().getString("exploreaccount"));
        return url;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){

        if (sender instanceof Player) {

            Player player = (Player) sender;

            if (economyFuncs.isFrozen(player)){

                player.sendMessage("You cannot access your wallet because it is frozen!");
                return false;

            }

            String playerWallet = economyFuncs.getWallet(player);
            String walletOwner = "your";

            if(args.length > 0
                && args[0].equalsIgnoreCase("server")) {

                playerWallet = RPC.getMasterWallet();
                walletOwner = "server";

            }

            try{

                TextComponent clickableWallet = new TextComponent(playerWallet);
                clickableWallet.setClickEvent(new ClickEvent( ClickEvent.Action.COPY_TO_CLIPBOARD, playerWallet.toString()));

                TextComponent walletHoverText = new TextComponent("Click here to copy this wallet address to the clipboard");
                walletHoverText.setColor(ChatColor.WHITE);
                clickableWallet.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[] { walletHoverText}));

                URL walletURL = new URL( getURL() + playerWallet);
                player.spigot().sendMessage(( new ComponentBuilder( "Deposit bans to " + walletOwner + " address: " )
                                                      .color( net.md_5.bungee.api.ChatColor.YELLOW )
                                                      .append(clickableWallet)
                                                      .color( net.md_5.bungee.api.ChatColor.WHITE )
                                                      .bold(true).create()));
                TextComponent addrlink = new TextComponent( "Click me to view " + walletOwner + " account in the block explorer" );
                addrlink.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, walletURL.toString() ) );
                addrlink.setUnderlined(true);
                player.spigot().sendMessage(addrlink);

            }
            catch (Exception e){
                System.out.println(e);
            }


        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }
}
