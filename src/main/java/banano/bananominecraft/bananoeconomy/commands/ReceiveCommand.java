package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.ReceiveService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Lets a player manually pocket any pending ("receivable") deposits waiting for their wallet.
 *
 * <p>The node normally auto-pockets deposits, with {@code BananoWebSocket} then notifying the
 * player — this command is a manual backstop for deposits still sitting pending (e.g. received
 * while the player, or the node's auto-receive, was unavailable).</p>
 */
public class ReceiveCommand implements CommandExecutor
{
    private final JavaPlugin plugin;
    private final EconomyFuncs economyFuncs;
    private final TaskTracker taskTracker;
    private final I18n i18n;
    private final ReceiveService receiveService;

    public ReceiveCommand(JavaPlugin plugin, EconomyFuncs economyFuncs, RPC rpc,
                          ConfigEngine configEngine, TaskTracker taskTracker, I18n i18n)
    {
        this(plugin, economyFuncs, taskTracker, i18n, new ReceiveService(rpc, configEngine));
    }

    /** Service-injecting constructor — pass a mock {@link ReceiveService} in tests. */
    public ReceiveCommand(JavaPlugin plugin, EconomyFuncs economyFuncs, TaskTracker taskTracker,
                          I18n i18n, ReceiveService receiveService)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
        this.receiveService = receiveService;
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

                if (!(sender instanceof Player player))
                {
                    return;
                }

                final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

                if (economyFuncs.isFrozen(player))
                {
                    player.sendMessage(i18n.get(locale, "receive.frozen"));
                    return;
                }

                String playerWallet = economyFuncs.getWallet(player);
                ReceiveService.ReceiveResult result = receiveService.receiveAllPending(playerWallet);

                if (!result.receivedBlocks().isEmpty())
                {
                    player.sendMessage(i18n.get(locale, "receive.success", result.receivedBlocks().size()));
                }
                else if (result.success())
                {
                    player.sendMessage(i18n.get(locale, "receive.none_pending"));
                }

                if (!result.success())
                {
                    player.sendMessage(i18n.get(locale, "receive.failed", result.userError()));
                }
            }
        }.runTaskAsynchronously(plugin);

        taskTracker.track(task);
        return true;
    }
}
