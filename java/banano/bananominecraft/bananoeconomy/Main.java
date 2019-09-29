package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.commands.*;
import banano.bananominecraft.bananoeconomy.events.OnJoin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;


public final class Main extends JavaPlugin implements Listener {
    private static Main instance;
    public static Economy economy = null;

    private Plugin plugin;


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
            RPC.sendPost(payload);}
        catch (Exception e){
            e.printStackTrace();
        }

        getCommand("deposit").setExecutor(new deposit());
        getCommand("nodeinfo").setExecutor(new nodeinfo());
        getCommand("tip").setExecutor(new tip());
        getCommand("withdraw").setExecutor(new withdraw());
        getCommand("balance").setExecutor(new balance());

        setupEconomy();
        setupWallet();

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        return super.onCommand(sender, command, label, args);
    }


    public void onDisable() {
        DB.getMongoClient().close();

    }

    private boolean setupEconomy()
    {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null) {
            Bukkit.getServer().getServicesManager().register(Economy.class, new VaultConnector(), this, ServicePriority.Highest);
        }
        return (economy != null);
    }

    private void setupWallet(){
        System.out.println("SETTING UP WALLET");
        if(!RPC.wallet_exists()){
            RPC.walletCreate();
            String masterWallet = RPC.accountCreate(0);
            System.out.println("MASTER WALLET: " + masterWallet);
            Main.getPlugin(Main.class).getConfig().set("masterWallet", masterWallet);
            Main.getPlugin(Main.class).saveConfig();
            System.out.println("SAVED");
        }
        else {
            System.out.println("Wallet exits??");
        }
    }
}
