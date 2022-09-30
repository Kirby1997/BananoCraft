package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class OnLeave implements Listener {

    private final EconomyFuncs economyFuncs;

    public OnLeave(EconomyFuncs economyFuncs) {
        this.economyFuncs = economyFuncs;
    }

    @EventHandler
    public void onLeaveServer(PlayerQuitEvent event){

        try {
            this.economyFuncs.unloadAccount(event.getPlayer());

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
