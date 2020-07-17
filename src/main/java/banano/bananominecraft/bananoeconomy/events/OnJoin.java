package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.DB;
import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.Main;
import com.mongodb.Block;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class OnJoin implements Listener {


    Block<Document> printBlock = new Block<Document>() {
        @Override
        public void apply(final Document document) {
            System.out.println(document.toJson());
        }
    };
    @EventHandler
    public void onJoinServer(PlayerJoinEvent event){

        Player player = event.getPlayer();
        TextComponent welcomeMessage = new TextComponent("This server is running BananoEconomy!");
        welcomeMessage.setColor(ChatColor.YELLOW);
        welcomeMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://banano.cc"));
        welcomeMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder( "Visit the Banano website!").create()));
        player.spigot().sendMessage(welcomeMessage);

        System.out.println("create account for player");
        EconomyFuncs.accountCreate(player);

    }


}
