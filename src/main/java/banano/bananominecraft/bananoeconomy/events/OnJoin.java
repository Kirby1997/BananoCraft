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
        TextComponent welcomeMessage = new TextComponent("Welcome to BananoCraft, ");
        welcomeMessage.setColor(ChatColor.YELLOW);
        welcomeMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://banano.cc"));
        welcomeMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder( "Visit the Banano website!").create()));
        TextComponent nameMessage = new TextComponent(player.getDisplayName());
        nameMessage.setColor(ChatColor.AQUA);
        welcomeMessage.addExtra(nameMessage);
        player.spigot().sendMessage(welcomeMessage);

        player.sendMessage("/balance to see balance");
        player.sendMessage("/deposit to see your address");
        player.sendMessage("/withdraw <amount> <address> to withdraw your bans");
        player.sendMessage("/tip [amount] [playername]");
        System.out.println("create account for player");
        EconomyFuncs.accountCreate(player);

    }


}
