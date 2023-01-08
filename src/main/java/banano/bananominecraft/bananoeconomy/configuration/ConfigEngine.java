package banano.bananominecraft.bananoeconomy.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class ConfigEngine {

    private final Plugin plugin;

    private String nodeAddress = "";
    private String explorerAccount = "https://creeper.banano.cc/explorer/account/";
    private String explorerBlock = "https://creeper.banano.cc/explorer/block/";

    private String masterWallet = "";

    private boolean enableOfflinePayment = false;

    public ConfigEngine(Plugin plugin) {
        this.plugin = plugin;

        initialiseConfig(plugin.getConfig());
    }

    public void reload() {
        this.plugin.reloadConfig();
        initialiseConfig(this.plugin.getConfig());
    }

    private void initialiseConfig(FileConfiguration configuration) {

        if(configuration != null) {

            this.nodeAddress = configuration.getString("IP");
            System.out.println("Node address identified: " + this.nodeAddress);

            this.explorerAccount = configuration.getString("exploreaccount");
            System.out.println("Account Explorer URL identified: " + this.explorerAccount);

            this.explorerBlock = configuration.getString("exploreblock");
            System.out.println("Block Explorer URL identified: " + this.explorerBlock);

            if(configuration.contains("masterWallet")) {

                this.masterWallet = configuration.getString("masterWallet");
                System.out.println("Master wallet address identified: " + this.masterWallet);

            }
            else {

                System.out.println("Master wallet address not specified.");

            }

            this.enableOfflinePayment = configuration.getBoolean("allowofflinepayment", false);
            System.out.println("Offline Payment Allowed: " + (this.enableOfflinePayment ? "ENABLED" : "DISABLED"));


        }

    }

    public boolean save() {

        FileConfiguration config = this.plugin.getConfig();

        config.set("IP", this.nodeAddress);
        config.set("exploreaccount", this.explorerAccount);
        config.set("exploreblock", this.explorerBlock);
        config.set("allowofflinepayment", this.enableOfflinePayment);

        this.plugin.saveConfig();
        this.plugin.reloadConfig();

        return true;

    }

    public String getNodeAddress() {
        return this.nodeAddress;
    }

    public void setNodeAddress(String newAddress) {
        this.nodeAddress = newAddress;
        this.plugin.getConfig().set("IP", newAddress);
    }

    public String getExplorerAccount() {
        return this.explorerAccount;
    }

    public void setExplorerAccount(String newAddress) {
        this.explorerAccount = newAddress;
        this.plugin.getConfig().set("exploreaccount", newAddress);
    }

    public String getExplorerBlock() {
        return this.explorerBlock;
    }

    public void setExplorerBlock(String newAddress) {
        this.explorerBlock = newAddress;
        this.plugin.getConfig().set("exploreblock", newAddress);
    }

    public boolean getEnableOfflinePayment() {
        return this.enableOfflinePayment;
    }

    public void setEnableOfflinePayment(boolean value) {
        this.enableOfflinePayment = value;
        this.plugin.getConfig().set("allowofflinepayment", value);
    }

    public String getMasterWallet() {
        return this.masterWallet;
    }

    public void setMasterWallet(String walletAddress) {
        this.masterWallet = walletAddress;
    }

}
