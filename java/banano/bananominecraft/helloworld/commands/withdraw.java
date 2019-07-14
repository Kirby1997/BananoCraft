package banano.bananominecraft.helloworld.commands;

import banano.bananominecraft.helloworld.DB;
import banano.bananominecraft.helloworld.RPC;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class withdraw implements CommandExecutor {


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {
            Player player = (Player) sender;
            String UUID = player.getUniqueId().toString();
            String playerWallet = DB.getWallet(UUID);
            try {
                int amount = Integer.parseInt(args[0]);
                String amountStr = Integer.toString(amount);
                if (args.length == 2) {
                    String withdrawAddr = args[1];
                    RPC rpc = new RPC();
                    String blockHash = rpc.sendTransaction(playerWallet,withdrawAddr,amount);
                    player.sendMessage(blockHash);
                    System.out.println(blockHash);
                    String blockURL = "https://creeper.banano.cc/explorer/block/" + blockHash;

                    player.spigot().sendMessage(( new ComponentBuilder( "You have sent " ).color( ChatColor.YELLOW ).append( amountStr ).color( ChatColor.WHITE ).bold(true).append( " to " ).color( ChatColor.YELLOW )
                            .append(withdrawAddr).color(ChatColor.WHITE).bold(true).append(" with block ID : ").append(blockHash).color(ChatColor.YELLOW).bold(true).create()));


                    TextComponent blocklink = new TextComponent( "Click me to view the transaction in the block explorer" );
                    blocklink.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, blockURL ) );
                    blocklink.setUnderlined(true);
                    player.spigot().sendMessage(blocklink);
                }
            } catch (Exception e)
            {
                e.printStackTrace();
            }


        }
        return false;
    }
}








