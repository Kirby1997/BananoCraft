package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.helpers.StringHelper;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class TransactionHistoryCommand implements CommandExecutor
{
    public static final String ARG_ALL = "all";

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final TaskTracker taskTracker;
    private final ConfigEngine configEngine;
    private final I18n i18n;
    private final MessageGenerator messageGenerator;

    public TransactionHistoryCommand(Plugin plugin, EconomyFuncs economyFuncs, TaskTracker taskTracker,
                                     ConfigEngine configEngine, I18n i18n, MessageGenerator messageGenerator)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.taskTracker = taskTracker;
        this.configEngine = configEngine;
        this.i18n = i18n;
        this.messageGenerator = messageGenerator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (sender instanceof Player player)
        {
            final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

            int recordCount = ConfigEngine.DEFAULT_HISTORY_TRANSACTIONS;
            int maxTransactions = configEngine.getMaximumTransactionHistoryCount();

            if (args.length > 0)
            {
                if (args[0].equalsIgnoreCase(ARG_ALL))
                {
                    recordCount = maxTransactions;
                }
                else
                {
                    try
                    {
                        recordCount = Integer.parseInt(args[0]);
                    }
                    catch (NumberFormatException e)
                    {
                        player.sendMessage(ChatColor.RED + i18n.get(locale, "history.invalid_count"));
                        return true;
                    }
                }
            }

            if (recordCount < ConfigEngine.MIN_HISTORY_TRANSACTIONS)
            {
                player.sendMessage(ChatColor.RED + i18n.get(locale, "history.count_too_low",
                        ConfigEngine.MIN_HISTORY_TRANSACTIONS));
                return true;
            }

            if (recordCount > maxTransactions)
            {
                player.sendMessage(ChatColor.RED + i18n.get(locale, "history.count_too_high", maxTransactions));
                return true;
            }

            int finalRecordCount = recordCount;
            BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                try
                {
                    final List<TransactionRecord> records =
                            economyFuncs.getTransactionHistory(player.getUniqueId(), finalRecordCount);

                    if (records != null && !records.isEmpty())
                    {
                        player.sendMessage(ChatColor.GOLD + TransactionRecord.getHeaderString(i18n, locale));

                        for (TransactionRecord record : records)
                        {
                            String displayAddress = StringHelper.left(record.address(), 15)
                                    + "..."
                                    + StringHelper.right(record.address(), 15);

                            player.spigot().sendMessage(record.toRecordString(messageGenerator));

                            if (record.direction() == TransactionDirection.Send)
                            {
                                player.sendMessage(ChatColor.YELLOW + "To:   " + displayAddress);
                            }
                            else
                            {
                                player.sendMessage(ChatColor.GREEN + "From: " + displayAddress);
                            }
                        }
                    }
                    else
                    {
                        player.sendMessage(ChatColor.YELLOW + i18n.get(locale, "history.no_records"));
                    }
                }
                catch (Exception ex)
                {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to retrieve history for player: " + player.getName(), ex);
                    player.sendMessage(ChatColor.RED + i18n.get(locale, "history.error"));
                }
            });

            taskTracker.track(task);
        }
        else
        {
            plugin.getLogger().warning("Transaction History command issued by non-player sender: " + sender.getName());
        }

        return true;
    }
}
