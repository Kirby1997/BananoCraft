package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
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
            economyFuncs.unloadAccount(event.getPlayer());
        }
        catch (Exception ex)
        {
            plugin.getLogger().log(Level.WARNING, "Error during player leave cleanup.", ex);
        }
    }
}
