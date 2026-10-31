package banano.bananominecraft.bananoeconomy.commands;

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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BalanceCommand implements CommandExecutor
{
    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final TaskTracker taskTracker;
    private final I18n i18n;

    public BalanceCommand(Plugin plugin, EconomyFuncs economyFuncs, TaskTracker taskTracker, I18n i18n)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (sender instanceof Player player)
        {
            final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

            BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                try
                {
                    final Double balance = economyFuncs.getBalance(player);
                    final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));

                    player.sendMessage(ChatColor.YELLOW + i18n.get(locale, "balance.current", df.format(balance)));
                }
                catch (Exception ex)
                {
                    player.sendMessage(ChatColor.RED + i18n.get(locale, "balance.error"));
                }
            });

            taskTracker.track(task);
        }
        else
        {
            plugin.getLogger().warning("Balance command issued by non-player sender: " + sender.getName());
        }

        return false;
    }
}
