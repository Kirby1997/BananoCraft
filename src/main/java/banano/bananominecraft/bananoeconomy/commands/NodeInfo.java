package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.RPC;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class NodeInfo implements CommandExecutor {

    private final JavaPlugin plugin;

    public NodeInfo(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try {

                if (sender instanceof Player || sender instanceof ConsoleCommandSender) {

                    List<String> payload = RPC.getBlockCount();
                    String checked = payload.get(0);
                    String unchecked = payload.get(1);
                    String msg0 = "The node IP is: " + RPC.getURL();
                    String msg1 = "Checked blocks: " + checked + " - Unchecked blocks: " + unchecked;
                    String msg2 = "The server wallet is " + RPC.getMasterWallet();
                    String msg3 = "It currently contains: " + RPC.getBalance(RPC.getMasterWallet());

                    if(sender instanceof Player) {

                        Player player = (Player) sender;

                        if (sender.isOp()) {
                            player.sendMessage(msg0);
                        }

                        player.sendMessage(msg1);
                        player.sendMessage(msg2);
                        player.sendMessage(msg3);

                    }
                    if(sender instanceof ConsoleCommandSender) {

                        System.out.println(msg0);
                        System.out.println(msg1);
                        System.out.println(msg2);
                        System.out.println(msg3);

                    }

                }

            }
            catch (Exception e){

                e.printStackTrace();

            }

        });

        return false;
    }
}
