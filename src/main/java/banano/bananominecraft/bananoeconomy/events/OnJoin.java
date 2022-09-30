package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
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

public class OnJoin implements Listener {

    private final EconomyFuncs economyFuncs;

    Block<Document> printBlock = new Block<Document>() {
        @Override
        public void apply(final Document document) {
            System.out.println(document.toJson());
        }
    };

    public OnJoin(EconomyFuncs economyFuncs) {
        this.economyFuncs = economyFuncs;
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
    }


}
