package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class ShowOfflineTransactionsCommand implements CommandExecutor
{
    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;
    private final ConfigEngine configEngine;
    private final TaskTracker taskTracker;
    private final I18n i18n;
    private final MessageGenerator messageGenerator;

    public ShowOfflineTransactionsCommand(Plugin plugin, EconomyFuncs economyFuncs, IDBConnector db,
                                          ConfigEngine configEngine, TaskTracker taskTracker,
                                          I18n i18n, MessageGenerator messageGenerator)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.db = db;
        this.configEngine = configEngine;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
        this.messageGenerator = messageGenerator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (sender instanceof Player player)
        {
            final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

            if (economyFuncs.isFrozen(player))
            {
                player.sendMessage(i18n.get(locale, "offline.frozen"));
                return false;
            }

            if (!this.configEngine.getEnableOfflinePayment())
            {
                player.sendMessage(i18n.get(locale, "offline.disabled"));
                return false;
            }

            BukkitTask task = new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    if (!plugin.isEnabled())
                    {
                        return;
                    }

                    try
                    {
                        List<OfflinePaymentRecord> offlinePayments = db.getOfflinePaymentRecords(player);

                        if (!offlinePayments.isEmpty())
                        {
                            int index = 1;

                            for (OfflinePaymentRecord paymentRecord : offlinePayments)
                            {
                                player.sendMessage(ChatColor.WHITE
                                        + i18n.get(locale, "offline.transaction_header",
                                                   index, paymentRecord.transactionDate()));

                                player.spigot().sendMessage(
                                        messageGenerator.generateTipReceiverMessage(locale, paymentRecord));
                                player.spigot().sendMessage(
                                        messageGenerator.generateBlockExplorerLink(locale, paymentRecord.blockHash()));

                                index++;
                            }

                            db.deleteOfflinePaymentRecords(player);
                        }
                        else
                        {
                            player.sendMessage(ChatColor.YELLOW + i18n.get(locale, "offline.none"));
                        }
                    }
                    catch (Exception ex)
                    {
                        plugin.getLogger().log(Level.WARNING, "Failed to display offline transactions.", ex);
                    }
                }
            }.runTaskAsynchronously(this.plugin);

            taskTracker.track(task);
        }
        else
        {
            plugin.getLogger().warning("showofflinetips command issued by non-player sender: " + sender.getName());
        }

        return false;
    }
}
