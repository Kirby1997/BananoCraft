package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.WithdrawService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
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
import org.bukkit.scheduler.BukkitTask;

import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;

public class WithdrawCommand implements CommandExecutor
{
    private final JavaPlugin plugin;
    private final EconomyFuncs economyFuncs;
    private final ConfigEngine configEngine;
    private final TaskTracker taskTracker;
    private final I18n i18n;
    private final WithdrawService withdrawService;

    public WithdrawCommand(final JavaPlugin plugin, EconomyFuncs economyFuncs, RPC rpc,
                           ConfigEngine configEngine, TaskTracker taskTracker, I18n i18n)
    {
        this(plugin, economyFuncs, configEngine, taskTracker, i18n, new WithdrawService(rpc));
    }

    /** Service-injecting constructor — pass a mock {@link WithdrawService} in tests. */
    public WithdrawCommand(final JavaPlugin plugin, EconomyFuncs economyFuncs,
                           ConfigEngine configEngine, TaskTracker taskTracker, I18n i18n,
                           WithdrawService withdrawService)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.configEngine = configEngine;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
        this.withdrawService = withdrawService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        BukkitTask task = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                if (sender instanceof Player player)
                {
                    final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

                    if (economyFuncs.isFrozen(player))
                    {
                        player.sendMessage(i18n.get(locale, "withdraw.frozen"));
                        return;
                    }

                    String playerWallet = economyFuncs.getWallet(player);

                    try
                    {
                        double amount = withdrawService.resolveAmount(playerWallet, args[0]);

                        if (amount <= 0)
                        {
                            player.sendMessage(i18n.get(locale, "withdraw.amount_not_positive"));
                            return;
                        }

                        String amountStr = Double.toString(amount);

                        // args[0] = amount, args[1] = destination address
                        if (args.length == 2)
                        {
                            final String withdrawAddr = args[1];

                            WithdrawService.WithdrawResult result =
                                    withdrawService.withdraw(playerWallet, withdrawAddr, amount);

                            if (!result.success())
                            {
                                player.sendMessage(i18n.get(locale, "withdraw.failed", amountStr, withdrawAddr, result.userError()));
                                return;
                            }

                            final String blockHash = result.blockHash();
                            player.sendMessage(blockHash);

                            try
                            {
                                final URL blockURL = new URL(configEngine.getExplorerBlock() + blockHash);

                                player.spigot().sendMessage(new ComponentBuilder("You have sent ")
                                        .color(ChatColor.YELLOW)
                                        .append(amountStr).color(ChatColor.WHITE).bold(true)
                                        .append(" to ").color(ChatColor.YELLOW)
                                        .append(withdrawAddr).color(ChatColor.WHITE).bold(true)
                                        .append(" with block ID : ")
                                        .append(blockHash).color(ChatColor.YELLOW).bold(true)
                                        .create());

                                TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");
                                blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                                blocklink.setUnderlined(true);
                                player.spigot().sendMessage(blocklink);
                            }
                            catch (Exception e)
                            {
                                plugin.getLogger().log(Level.WARNING, "Failed to build block explorer link.", e);
                            }
                        }
                        else
                        {
                            throw new Exception();
                        }
                    }
                    catch (Exception e)
                    {
                        player.sendMessage(i18n.get(I18n.parseMinecraftLocale(player.getLocale()), "withdraw.usage"));
                    }
                }
            }
        }.runTaskAsynchronously(plugin);

        taskTracker.track(task);
        return true;
    }
}
