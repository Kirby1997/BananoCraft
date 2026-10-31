package banano.bananominecraft.bananoeconomy;

import banano.bananominecraft.bananoeconomy.api.BananoWalletService;
import banano.bananominecraft.bananoeconomy.api.BananoWalletServiceImpl;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.commands.*;
import banano.bananominecraft.bananoeconomy.commands.tabcompleters.*;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.DBConnectorFactory;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.events.OnJoin;
import banano.bananominecraft.bananoeconomy.events.OnLeave;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.io.VaultConnector;
import banano.bananominecraft.bananoeconomy.services.BankService;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;
import banano.bananominecraft.bananoeconomy.trackers.BukkitTaskTracker;
import banano.bananominecraft.bananoeconomy.validation.Validator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BananoEconomyMain extends JavaPlugin
{
    private IDBConnector       db;
    private ConfigEngine       configEngine;
    private I18n               i18n;
    private MessageGenerator   messageGenerator;
    private RPC                rpc;
    private EconomyFuncs       economyFuncs;
    private BankService        bankService;
    private RepresentativeService representativeService;
    private BananoWebSocket    webSocket;

    /** Tracks every async task so they can all be cancelled in onDisable. */
    private final BukkitTaskTracker taskTracker = new BukkitTaskTracker();

    @Override
    public void onEnable()
    {
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // --- infrastructure (order matters: config before rpc before economy) ---
        this.db             = DBConnectorFactory.create(this);
        this.configEngine   = new ConfigEngine(this);
        this.i18n           = new I18n(this.getClass().getClassLoader());
        this.messageGenerator = new MessageGenerator(this.i18n, this.configEngine);
        this.rpc            = new RPC(this, this.configEngine);
        this.economyFuncs   = new EconomyFuncs(this, this.db, this.rpc, this.configEngine);
        this.bankService    = new BankService(this, this.configEngine, this.rpc, this.db);
        this.representativeService = new RepresentativeService(this.rpc, this.configEngine);
        this.webSocket      = new BananoWebSocket(this, this.rpc, this.configEngine, this.messageGenerator);

        // --- event listeners ---
        getServer().getPluginManager().registerEvents(
                new OnJoin(this, this.economyFuncs, this.db, this.configEngine,
                           this.webSocket, this.taskTracker, this.i18n, this.messageGenerator), this);
        getServer().getPluginManager().registerEvents(
                new OnLeave(this, this.economyFuncs, this.webSocket), this);

        // --- commands ---
        getCommand("deposit").setExecutor(
                new DepositCommand(this, this.economyFuncs, this.configEngine, this.rpc, this.i18n));
        getCommand("nodeinfo").setExecutor(
                new NodeInfoCommand(this, this.rpc, this.taskTracker));
        getCommand("tip").setExecutor(
                new TipCommand(this, this.economyFuncs, this.configEngine, this.db, this.rpc,
                               this.taskTracker, this.i18n, this.messageGenerator));
        getCommand("withdraw").setExecutor(
                new WithdrawCommand(this, this.economyFuncs, this.rpc, this.configEngine,
                                    this.taskTracker, this.i18n));
        getCommand("balance").setExecutor(
                new BalanceCommand(this, this.economyFuncs, this.taskTracker, this.i18n));
        getCommand("showofflinetips").setExecutor(
                new ShowOfflineTransactionsCommand(this, this.economyFuncs, this.db, this.configEngine,
                                                   this.taskTracker, this.i18n, this.messageGenerator));
        getCommand("history").setExecutor(
                new TransactionHistoryCommand(this, this.economyFuncs, this.taskTracker,
                                              this.configEngine, this.i18n, this.messageGenerator));
        getCommand("bc").setExecutor(
                new AdminCommand(this, this.configEngine, this.economyFuncs, this.db, this.rpc,
                                 this.webSocket, this.taskTracker, this.messageGenerator));
        getCommand("representative").setExecutor(
                new RepresentativeCommand(this, this.economyFuncs, this.rpc, this.configEngine,
                                          this.taskTracker, this.i18n));
        getCommand("receive").setExecutor(
                new ReceiveCommand(this, this.economyFuncs, this.rpc, this.configEngine,
                                   this.taskTracker, this.i18n));

        // --- tab completers ---
        getCommand("tip").setTabCompleter(new TipTabCompleter(this.configEngine));
        getCommand("withdraw").setTabCompleter(new WithdrawTabCompleter());
        getCommand("deposit").setTabCompleter(new DepositTabCompleter());
        getCommand("history").setTabCompleter(new TransactionHistoryTabCompleter(this.configEngine));
        getCommand("bc").setTabCompleter(new AdminCommandTabCompleter(this.configEngine, this.db));
        getCommand("representative").setTabCompleter(new RepresentativeTabCompleter());

        getLogger().info("Commands registered.");

        setupVault();
        getLogger().info("Economy setup complete.");

        setupBananoWalletService();
        getLogger().info("BananoWalletService registered.");

        // Check the node is reachable before touching wallets: an unreachable node must never be
        // mistaken for a missing wallet (which would trigger wallet creation / config rewrites).
        if (this.rpc.ping())
        {
            setupWallet();
            getLogger().info("Wallet setup complete.");

            setupBankWallet();
            getLogger().info("Bank wallet setup complete.");
        }
        else
        {
            getLogger().severe("Cannot reach the Banano node at " + redactNodeUrl(this.configEngine.getNodeAddress())
                    + " — skipping wallet setup. Economy features will fail until the node is reachable"
                    + " and the server is restarted.");
        }

        this.webSocket.connect();
        getLogger().info("WebSocket connecting to " + this.configEngine.getWebsocketUrl());
    }

    @Override
    public void onDisable()
    {
        if (this.webSocket != null)
        {
            this.webSocket.shutdown();
        }

        // Cancel every tracked async task, then cancel any that slipped through
        // (e.g. the JsonDBConnector repeating save task).
        taskTracker.cancelAll();
        Bukkit.getServer().getScheduler().cancelTasks(this);

        if (this.db != null)
        {
            this.db.close();
        }
    }

    // -------------------------------------------------------------------------
    // Private setup helpers
    // -------------------------------------------------------------------------

    private void setupVault()
    {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") != null)
        {
            Bukkit.getServer().getServicesManager().register(
                    Economy.class,
                    new VaultConnector(this, this.economyFuncs, this.rpc, this.bankService),
                    this,
                    ServicePriority.Highest);
        }
    }

    private void setupBananoWalletService()
    {
        Bukkit.getServer().getServicesManager().register(
                BananoWalletService.class,
                new BananoWalletServiceImpl(this.db, this.rpc, this.representativeService),
                this,
                ServicePriority.Normal);
    }

    private void setupBankWallet()
    {
        if (!this.configEngine.isBankSeedConfigured())
        {
            getLogger().info("bankWalletSeed not configured — bank accounts disabled.");
            return;
        }

        RPC.WalletStatus status = this.rpc.bankWalletStatus();

        if (status == RPC.WalletStatus.UNREACHABLE)
        {
            getLogger().severe("Could not verify the bank wallet on the node — not creating one."
                    + " Bank accounts will not work until this is resolved and the server restarted.");
            return;
        }

        if (status == RPC.WalletStatus.MISSING)
        {
            getLogger().warning("Bank wallet not found on node — creating now.");
            if (!this.rpc.bankWalletCreate())
            {
                getLogger().severe("Bank wallet creation failed — bank accounts will not work.");
                return;
            }
        }
        else
        {
            getLogger().info("Bank wallet verified.");
        }

        this.bankService.preloadBankBalancesAsync(this.taskTracker);
    }

    private void setupWallet()
    {
        RPC.WalletStatus status = this.rpc.walletStatus();

        if (status == RPC.WalletStatus.UNREACHABLE)
        {
            getLogger().severe("Could not verify the master wallet on the node — not creating one."
                    + " Economy features will fail until this is resolved and the server restarted.");
            return;
        }

        if (status == RPC.WalletStatus.MISSING)
        {
            getLogger().warning("Master wallet not found — creating wallet now.");
            if (!this.rpc.walletCreate()) // persists walletID via ConfigEngine.save()
            {
                getLogger().severe("Master wallet creation failed — economy features will not work.");
                return;
            }
        }
        else if (Validator.validateAddress(this.configEngine.getMasterWallet()))
        {
            getLogger().info("Master wallet verified.");
            return;
        }
        else
        {
            // Wallet exists but no valid master account was saved (e.g. an earlier
            // account_create failed) — index 0 is deterministic, so derive it again.
            getLogger().warning("Master wallet exists but no valid master account is configured — deriving it now.");
        }

        String masterWallet = this.rpc.accountCreate(0);

        if (RPC.ACCOUNT_CREATION_FAILED.equals(masterWallet) || !Validator.validateAddress(masterWallet))
        {
            getLogger().severe("Master account creation failed (got '" + masterWallet
                    + "') — not saving it to config. Economy features will not work.");
            return;
        }

        getLogger().info("Master wallet created: " + masterWallet);

        // Persist through ConfigEngine so the value is consistent with all
        // other config writes and is included in the next save() call.
        this.configEngine.setMasterWallet(masterWallet);
        this.configEngine.save();
    }

    /** Strips any {@code user:password@} part from a node URL so it is safe to log. */
    static String redactNodeUrl(String url)
    {
        if (url == null)
        {
            return "<not configured>";
        }
        return url.replaceFirst("(?<=://)[^/@]*@", "");
    }
}
