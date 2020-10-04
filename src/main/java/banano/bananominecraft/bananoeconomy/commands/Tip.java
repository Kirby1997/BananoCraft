package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.DB;
import banano.bananominecraft.bananoeconomy.RPC;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Tip implements CommandExecutor {
    private final JavaPlugin plugin;

    public Tip(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;
        if (DB.isFrozen(player)) {
            sender.sendMessage("u r frozen!!!!!!!!!");
            return false;
        }

        if (args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == player) {
                player.sendMessage("You cannot tip yourself");
            } else if (target instanceof Player) {
                if (DB.isFrozen(target)) {
                    sender.sendMessage(args[1] + "is frozen!!!!!!!!!");
                    return false;
                }
                double amount = Double.parseDouble(args[0]);
                if (amount <= 0) {
                    player.sendMessage("Amount has to be greater than 0");
                    return false;
                }
                player.sendMessage("Tipping " + target.getDisplayName() + " with " + amount + " bans.");
                String sWallet = DB.getWallet(player);
                if (RPC.getBalance(sWallet) >= amount && amount > 0) {
                    String tWallet = DB.getWallet(target);
                    String blockHash = RPC.sendTransaction(sWallet, tWallet, amount);
                    String blockURL = "https://creeper.banano.cc/explorer/block/" + blockHash;

                    TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");
                    blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL));
                    blocklink.setUnderlined(true);

                    String amountstr = Double.toString(amount);
                    player.spigot().sendMessage((new ComponentBuilder("You have sent ").color(ChatColor.YELLOW).append(amountstr).color(ChatColor.WHITE).bold(true).append(" to ").color(ChatColor.YELLOW)
                            .append(target.getDisplayName()).color(ChatColor.WHITE).bold(true).append(" with block ID : ").append(blockHash).color(ChatColor.YELLOW).bold(true).create()));
                    player.spigot().sendMessage(blocklink);

                    target.spigot().sendMessage((new ComponentBuilder("You have received ").color(ChatColor.YELLOW).append(amountstr).color(ChatColor.WHITE).bold(true).append(" from ").color(ChatColor.YELLOW)
                            .append(player.getDisplayName()).color(ChatColor.WHITE).bold(true).append(" with block ID : ").append(blockHash).color(ChatColor.YELLOW).bold(true).create()));
                    target.spigot().sendMessage(blocklink);

                } else {
                    player.sendMessage("Insufficient balance");
                }

            } else {
                player.sendMessage("Player needs to be online for you to tip them");
            }
        } else {
            player.sendMessage("You need to enter an amount to send and a player to send to");
            player.sendMessage("/tip [amount] [playername]");
        }
    }
}
