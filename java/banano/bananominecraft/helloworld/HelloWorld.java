package banano.bananominecraft.helloworld;

import banano.bananominecraft.helloworld.commands.*;
import banano.bananominecraft.helloworld.events.OnJoin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;


public final class HelloWorld extends JavaPlugin implements Listener {
    private static HelloWorld instance;

    RPC rpc = new RPC();
    DB db = new DB();


    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        getConfig().options().copyDefaults();
        saveDefaultConfig();
        System.out.println("STARTED");
        getServer().getPluginManager().registerEvents(new OnJoin(), this);

        try{
            String payload = "{\"action\": \"block_count\"}";
            rpc.sendPost(payload);}
        catch (Exception e){
            e.printStackTrace();
        }

        getCommand("poop").setExecutor(new poop());
        getCommand("deposit").setExecutor(new deposit());
        getCommand("nodeinfo").setExecutor(new nodeinfo());
        getCommand("tip").setExecutor(new tip());
        getCommand("withdraw").setExecutor(new withdraw());
        getCommand("balance").setExecutor(new balance());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        return super.onCommand(sender, command, label, args);
    }

    public static HelloWorld getPlugin(){
        return instance;
    }




    public void onDisable() {
        DB.getMongoClient().close();

    }
}
