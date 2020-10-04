package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.DB;
import banano.bananominecraft.bananoeconomy.RPC;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
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
            if (DB.isFrozen(player)){
                player.sendMessage("u r frozen!!!!!!!!!");
                return false;
            }
            String UUID = player.getUniqueId().toString();
            String playerWallet = DB.getWallet(player);
            try {
                double amount = Double.parseDouble(args[0]);

                if(amount <= 0){
                    player.sendMessage("Amount has to be greater than 0");
                    return false;
                }
                String amountStr = Double.toString(amount);
                if (args.length == 2) {
                    final String withdrawAddr = args[1];
                    final String blockHash;
                    try {
                        blockHash = RPC.sendTransaction(playerWallet, withdrawAddr, amount);
                    } catch (final TransactionError error) {
                        player.sendMessage(String.format("/withdraw %f %s failed with: %s", amount, withdrawAddr, error.getUserError()));
                        return false;
                    }
                    player.sendMessage(blockHash);

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
                player.sendMessage("Wrong formatting. /withdraw <amount> <address>");
                e.printStackTrace();
            }


        }
        return false;
    }
}








