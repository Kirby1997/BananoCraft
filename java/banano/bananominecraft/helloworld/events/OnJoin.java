package banano.bananominecraft.helloworld.events;

import banano.bananominecraft.helloworld.DB;
import banano.bananominecraft.helloworld.RPC;
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


        accountCreate(player);
    }

    private void accountCreate(Player player){
        String UUID = player.getUniqueId().toString();
        try {

            if (!DB.accountExists(UUID)) {
                RPC rpc = new RPC();
                String wallet =  rpc.accountCreate();
                DB.storeAccount(player, wallet);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
