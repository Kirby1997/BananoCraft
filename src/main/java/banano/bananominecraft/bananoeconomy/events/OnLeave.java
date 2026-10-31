package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public class OnLeave implements Listener
{
    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final BananoWebSocket webSocket;

    public OnLeave(Plugin plugin, EconomyFuncs economyFuncs, BananoWebSocket webSocket)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.webSocket = webSocket;
    }

    @EventHandler
    public void onLeaveServer(PlayerQuitEvent event)
    {
        try
        {
            // Retrieve wallet before unloading the record from memory
            String wallet = economyFuncs.getWallet(event.getPlayer());
            webSocket.unwatchAccount(wallet);
            unloadAccount(event.getPlayer());
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Error during player leave cleanup.", ex);
        }
    }

    /**
     * Persisting the record is a DB write that can block for the full connection timeout when
     * the database is down, so it runs off the main thread. While the plugin is disabling, async
     * tasks can't be scheduled — fall back to a synchronous unload (the shutdown path flushes
     * all records synchronously anyway).
     */
    private void unloadAccount(Player player)
    {
        if (!plugin.isEnabled())
        {
            economyFuncs.unloadAccount(player);
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
        {
            try
            {
                economyFuncs.unloadAccount(player);
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.WARNING, "Error unloading account for " + player.getName(), ex);
            }
        });
    }
}
