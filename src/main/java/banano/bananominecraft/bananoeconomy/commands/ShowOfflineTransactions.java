package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class ShowOfflineTransactions implements CommandExecutor {

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;
    private final ConfigEngine configEngine;

    public ShowOfflineTransactions(Plugin plugin, EconomyFuncs economyFuncs, IDBConnector db, ConfigEngine configEngine) {

        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.db = db;
        this.configEngine = configEngine;

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){

        if (sender instanceof Player) {

            Player player = (Player) sender;

            if (economyFuncs.isFrozen(player)){

                player.sendMessage("You cannot access your wallet because it is frozen!");
                return false;

            }

            if(!this.configEngine.getEnableOfflinePayment()) {

                player.sendMessage("Offline tips are disabled!");
                return false;

            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {

                        List<OfflinePaymentRecord> offlinePayments = db.getOfflinePaymentRecords(player);

                        if(offlinePayments.size() > 0) {

                            int index = 1;

                            for(OfflinePaymentRecord paymentRecord : offlinePayments) {

                                player.sendMessage(ChatColor.WHITE + "Transaction " + index + ": " + paymentRecord.getTransactionDate());

                                player.spigot().sendMessage(MessageGenerator.generateTipReceiverMessage(paymentRecord));
                                player.spigot().sendMessage(MessageGenerator.generateBlockExplorerLink(configEngine, paymentRecord.getBlockHash()));

                                index++;

                            }

                            db.deleteOfflinePaymentRecords(player);

                        }
                        else {

                            player.sendMessage(ChatColor.YELLOW + "You have no offline transactions to display.");

                        }

                    }
                    catch (Exception ex) {

                        ex.printStackTrace();

                    }
                }
            }.runTaskAsynchronously(this.plugin);


        } else {
            System.out.println("You need to be a player");
        }

        return false;
    }
}
