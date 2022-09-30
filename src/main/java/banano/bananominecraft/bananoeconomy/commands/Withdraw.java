package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.URL;

public class Withdraw implements CommandExecutor {


    private final JavaPlugin plugin;
    private final EconomyFuncs economyFuncs;

    public Withdraw(final JavaPlugin plugin, EconomyFuncs economyFuncs) {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
    }

    private URL getURL() throws Exception{

        URL url = new URL(plugin.getConfig().getString("exploreblock"));
        return url;

    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        new BukkitRunnable() {
            @Override
            public void run() {

                if (sender instanceof Player) {

                    Player player = (Player) sender;

                    if (economyFuncs.isFrozen(player)){

                        player.sendMessage("Your account has been frozen");
                        return;

                    }

                    String playerWallet = economyFuncs.getWallet(player);

                    try {

                        double amount = Double.parseDouble(args[0]);

                        if (amount <= 0) {
                            player.sendMessage("Amount has to be greater than 0");
                            return;
                        }

                        String amountStr = Double.toString(amount);

                        // We can keep args.length to 2 by injecting JSON without spaces
                        if (args.length == 2) {

                            final String withdrawAddr = args[1];
                            final String blockHash;

                            try {
                                blockHash = RPC.sendTransaction(playerWallet, withdrawAddr, amount);
                            } catch (final TransactionError error) {
                                player.sendMessage(String.format("/withdraw %f %s failed with: %s", amount, withdrawAddr, error.getUserError()));
                                return;
                            }

                            player.sendMessage(blockHash);

                            try{
                                final URL blockURL = new URL ( getURL() + blockHash);

                                player.spigot().sendMessage((new ComponentBuilder("You have sent ").color(ChatColor.YELLOW).append(amountStr).color(ChatColor.WHITE).bold(true).append(" to ").color(ChatColor.YELLOW)
                                        .append(withdrawAddr).color(ChatColor.WHITE).bold(true).append(" with block ID : ").append(blockHash).color(ChatColor.YELLOW).bold(true).create()));


                                TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");
                                blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                                blocklink.setUnderlined(true);
                                player.spigot().sendMessage(blocklink);
                            }
                            catch (Exception e){
                                System.out.println(e);
                            }
                        } else {
                            throw new Exception();
                        }
                    } catch (Exception e) {
                        player.sendMessage("Wrong formatting. /withdraw <amount> <address>");
                    }

                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }
}







