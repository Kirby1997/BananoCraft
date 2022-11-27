package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import com.mongodb.Block;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class OnJoin implements Listener {

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;
    private final ConfigEngine configEngine;

    Block<Document> printBlock = new Block<Document>() {
        @Override
        public void apply(final Document document) {
            System.out.println(document.toJson());
        }
    };

    public OnJoin(Plugin plugin, EconomyFuncs economyFuncs, IDBConnector db, ConfigEngine configEngine) {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.db = db;
        this.configEngine = configEngine;
    }

    @EventHandler
    public void onJoinServer(PlayerJoinEvent event){

        Player player = event.getPlayer();

        TextComponent welcomeMessage = new TextComponent("This server is running BananoEconomy!");
        welcomeMessage.setColor(ChatColor.YELLOW);
        welcomeMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://github.com/Kirby1997/BananoCraft"));
        welcomeMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder( "See the code!").create()));
        player.spigot().sendMessage(welcomeMessage);

        try {

            System.out.println("Creating account for player...");

            economyFuncs.accountCreate(player);

        }
        catch (Exception ex) {

            player.sendMessage(org.bukkit.ChatColor.RED + "There was an error configuring your BananoEconomy wallet!");

        }

        if(this.configEngine.getEnableOfflinePayment()) {

            // Check if the player has offline payments and display a link to run the command if they do.
            new BukkitRunnable() {
                @Override
                public void run() {

                    try {

                        double totalAmount = db.getOfflinePaymentsTotal(player);

                        if (totalAmount > 0) {

                            player.sendMessage(ChatColor.GOLD + ChatColor.BOLD.toString() + "You have received transactions totalling " + totalAmount + " Banano while you were offline!");

                            player.spigot().sendMessage(MessageGenerator.generateClickToViewOfflinePayments());

                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }
            }.runTaskAsynchronously(this.plugin);

        }

    }


}
