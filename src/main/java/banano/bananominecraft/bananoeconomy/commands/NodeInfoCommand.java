package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.NodeInfoService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public class NodeInfoCommand implements CommandExecutor
{
    private final JavaPlugin plugin;
    private final TaskTracker taskTracker;
    private final NodeInfoService nodeInfoService;

    public NodeInfoCommand(final JavaPlugin plugin, RPC rpc, TaskTracker taskTracker)
    {
        this(plugin, taskTracker, new NodeInfoService(rpc));
    }

    /** Service-injecting constructor — pass a mock {@link NodeInfoService} in tests. */
    public NodeInfoCommand(final JavaPlugin plugin, TaskTracker taskTracker, NodeInfoService nodeInfoService)
    {
        this.plugin = plugin;
        this.taskTracker = taskTracker;
        this.nodeInfoService = nodeInfoService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            if (!plugin.isEnabled())
            {
                return;
            }

            try
            {
                if (sender instanceof Player || sender instanceof ConsoleCommandSender)
                {
                    NodeInfoService.NodeInfo info = nodeInfoService.gather();
                    String msg0 = "The node IP is: " + info.nodeUrl();
                    String msg1 = "Checked blocks: " + info.checkedBlocks() + " - Unchecked blocks: " + info.uncheckedBlocks();
                    String msg2 = "The server wallet is " + info.masterWallet();
                    String msg3 = "It currently contains: " + info.balance();

                    if (sender instanceof Player player)
                    {
                        if (sender.isOp())
                        {
                            player.sendMessage(msg0);
                        }

                        player.sendMessage(msg1);
                        player.sendMessage(msg2);
                        player.sendMessage(msg3);
                    }
                    else if (sender instanceof ConsoleCommandSender console)
                    {
                        console.sendMessage(msg0);
                        console.sendMessage(msg1);
                        console.sendMessage(msg2);
                        console.sendMessage(msg3);
                    }
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "NodeInfo command failed.", e);
            }
        });

        taskTracker.track(task);
        return false;
    }
}
