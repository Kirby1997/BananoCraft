package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.commands.*;
import banano.bananominecraft.bananoeconomy.commands.tabcompleters.*;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.db.JsonDBConnector;
import banano.bananominecraft.bananoeconomy.db.MongoDBConnector;
import banano.bananominecraft.bananoeconomy.db.MysqlDBConnector;
import banano.bananominecraft.bananoeconomy.events.OnJoin;
import banano.bananominecraft.bananoeconomy.events.OnLeave;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;



import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class Main extends JavaPlugin implements Listener {

    public Economy economy = null;
    public Logger log = Bukkit.getLogger();
    private HashMap<UUID, PermissionAttachment> playerPermissions;

    private IDBConnector db;
    private EconomyFuncs economyFuncs;

    @Override
    public void onEnable() {

        // Plugin startup logic
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        FileConfiguration configuration = this.getConfig();

        if(configuration.contains("mongoURI")
                && configuration.getString("mongoURI") != null
                && configuration.getString("mongoURI").length() > 0) {

            System.out.println(configuration.getString("mongoURI"));
            System.out.println("Initialising MongoDB connection...");
            this.db = new MongoDBConnector(this);

        }
        else if(configuration.contains("mysqlServerName")
                && configuration.getString("mysqlServerName") != null
                && configuration.getString("mysqlServerName").length() > 0) {

            System.out.println("Initialising MySQL connection...");
            this.db = new MysqlDBConnector(this);

        }
        else {

            System.out.println("Initialising Json connection...");
            this.db = new JsonDBConnector(this);

        }

        this.economyFuncs = new EconomyFuncs(this.db);

        System.out.println("STARTED");
        getServer().getPluginManager().registerEvents(new OnJoin(this.economyFuncs), this);
        getServer().getPluginManager().registerEvents(new OnLeave(this.economyFuncs), this);

        getCommand("deposit").setExecutor(new Deposit(this, this.economyFuncs));
        getCommand("nodeinfo").setExecutor(new NodeInfo(this));
        getCommand("tip").setExecutor(new Tip(this, this.economyFuncs));
        getCommand("withdraw").setExecutor(new Withdraw(this, this.economyFuncs));
        getCommand("balance").setExecutor(new Balance(this, this.economyFuncs));
        getCommand("freeze").setExecutor(new Freeze(this.economyFuncs));
        getCommand("unfreeze").setExecutor(new UnFreeze(this.economyFuncs));

        getCommand("freeze").setTabCompleter(new FreezeTabCompleter());
        getCommand("unfreeze").setTabCompleter(new UnFreezeTabCompleter());
        getCommand("tip").setTabCompleter(new TipTabCompleter());
        getCommand("withdraw").setTabCompleter(new WithdrawTabCompleter());
        getCommand("deposit").setTabCompleter(new DepositTabCompleter());

        System.out.println("registered commands");

        setupEconomy();
        System.out.println("economy setup");

        setupWallet();
        System.out.println("wallet setup");

        Bukkit.getLogger().setLevel(Level.SEVERE);

    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return super.onCommand(sender, command, label, args);
    }

    public void onDisable() {
        // Kill off any incomplete threaded tasks
        Bukkit.getServer().getScheduler().cancelTasks(this);

        this.db.close();

    }

    private boolean setupEconomy()
    {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null) {
            Bukkit.getServer().getServicesManager().register(Economy.class, new VaultConnector(this.economyFuncs), this, ServicePriority.Highest);
        }
        System.out.println("return economy setup");
        return (economy != null);
    }

    private void setupWallet(){
        System.out.println(RPC.wallet_exists());
        if(!RPC.wallet_exists()){
            System.out.println("MASTER WALLET DOES NOT EXIST -SETTING UP WALLET");
            RPC.walletCreate();
            String masterWallet = RPC.accountCreate(0);
            System.out.println("MASTER WALLET: " + masterWallet);
            Main.getPlugin(Main.class).getConfig().set("masterWallet", masterWallet);
            Main.getPlugin(Main.class).saveConfig();

        }
        else {
            System.out.println("Master wallet exists.");
        }
    }
}
