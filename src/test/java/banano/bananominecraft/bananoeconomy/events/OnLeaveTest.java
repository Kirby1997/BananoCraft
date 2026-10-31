package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class OnLeaveTest
{
    private Plugin plugin;
    private EconomyFuncs economyFuncs;
    private BananoWebSocket webSocket;
    private OnLeave listener;

    @BeforeEach
    void setUp()
    {
        plugin = mock(Plugin.class);
        economyFuncs = mock(EconomyFuncs.class);
        webSocket = mock(BananoWebSocket.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        listener = new OnLeave(plugin, economyFuncs, webSocket);
    }

    @Test
    void onLeave_unwatchesWalletAndUnloadsAccount()
    {
        Player player = mock(Player.class);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(economyFuncs.getWallet(player)).thenReturn("ban_wallet");

        listener.onLeaveServer(event);

        verify(webSocket).unwatchAccount("ban_wallet");
        verify(economyFuncs).unloadAccount(player);
    }

    @Test
    void onLeave_swallowsExceptions()
    {
        Player player = mock(Player.class);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(economyFuncs.getWallet(player)).thenThrow(new RuntimeException("boom"));

        // Cleanup failure must not propagate out of the event handler.
        listener.onLeaveServer(event);

        verify(webSocket, never()).unwatchAccount(anyString());
    }

    @Test
    void onLeave_unloadsAccountAsync_whenPluginEnabled()
    {
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        Player player = mock(Player.class);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(economyFuncs.getWallet(player)).thenReturn("ban_wallet");

        listener.onLeaveServer(event);

        // Nothing touches the DB on the event (main) thread.
        verify(economyFuncs, never()).unloadAccount(player);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(eq(plugin), task.capture());
        task.getValue().run();

        verify(economyFuncs).unloadAccount(player);
    }
}
