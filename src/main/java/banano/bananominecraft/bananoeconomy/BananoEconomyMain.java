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
import banano.bananominecraft.bananoeconomy.io.HttpTransport;
import banano.bananominecraft.bananoeconomy.io.HttpUrlConnectionTransport;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.io.VaultConnector;
import banano.bananominecraft.bananoeconomy.services.BankService;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;
import banano.bananominecraft.bananoeconomy.trackers.BukkitTaskTracker;
import banano.bananominecraft.bananoeconomy.validation.Validator;
import banano.bananominecraft.bananoeconomy.wallet.LightWalletSigner;
import banano.bananominecraft.bananoeconomy.wallet.WalletIndexBackfill;
import banano.bananominecraft.bananoeconomy.wallet.WalletSigner;
import banano.bananominecraft.bananoeconomy.wallet.crypto.CpuWorkGenerator;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Work;
import banano.bananominecraft.bananoeconomy.wallet.crypto.WorkGenerator;
import banano.bananominecraft.bananoeconomy.wallet.light.HttpLightRpc;
import banano.bananominecraft.bananoeconomy.wallet.light.LightRpc;
import banano.bananominecraft.bananoeconomy.wallet.light.LightWallet;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.logging.Level;

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

    /** Light mode only: the local signing wallets and their shared work generator (shut down in onDisable). */
    private LightWallet        lightPlayerWallet;
    private LightWallet        lightBankWallet;
    private WorkGenerator      workGenerator;

    /** Tracks every async task so they can all be cancelled in onDisable. */
    private final BukkitTaskTracker taskTracker = new BukkitTaskTracker();

    @Override
    public void onEnable()
    {
        getConfig().options().copyDefaults();
        saveDefaultConfig();

        // --- infrastructure (order matters: config before rpc before economy) ---
        this.db             = DBConnectorFactory.create(this);
        if (this.db == null)
        {
            // The factory has already logged why; never run the economy without its records.
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.configEngine   = new ConfigEngine(this);
        this.i18n           = new I18n(this.getClass().getClassLoader());
        this.messageGenerator = new MessageGenerator(this.i18n, this.configEngine);

        if (this.configEngine.isLightMode() && parseSeed(this.configEngine.getWalletSeed()) == null)
        {
            // Light mode signs with the seed itself; without it nothing can be signed, so refuse to
            // start the economy rather than limp along (and never fall back to a node wallet).
            getLogger().severe("walletMode is 'light' but the wallet seed is unavailable (missing, undecryptable"
                    + " or not 64 hex characters - see earlier errors). Economy features are NOT starting."
                    + " Restore walletSeed / secret.key and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.rpc            = createRpc();
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
        // Light mode derives and signs locally, so wallet setup does not need the endpoint to be up.
        boolean reachable = this.rpc.ping();
        if (!reachable && this.configEngine.isLightMode())
        {
            getLogger().warning("Cannot reach the Banano RPC endpoint at "
                    + redactNodeUrl(this.configEngine.getNodeAddress())
                    + " - sends and receives will fail until it is reachable.");
        }

        if (reachable || this.configEngine.isLightMode())
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

        startWalletIndexBackfill();

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

        if (this.lightPlayerWallet != null)
        {
            this.lightPlayerWallet.shutdown();
        }
        if (this.lightBankWallet != null)
        {
            this.lightBankWallet.shutdown();
        }
        if (this.workGenerator != null)
        {
            this.workGenerator.shutdown();
        }

        if (this.db != null)
        {
            this.db.close();
        }
    }

    // -------------------------------------------------------------------------
    // Private setup helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the RPC client with the wallet signer for the configured walletMode: the node's own
     * wallet ({@code node}, today's behaviour) or local signing ({@code light}).
     */
    private RPC createRpc()
    {
        HttpTransport http = new HttpUrlConnectionTransport();

        if (!this.configEngine.isLightMode())
        {
            return new RPC(this, this.configEngine, http);
        }

        getLogger().info("Light wallet mode: keys are derived and blocks signed locally; the seed is never sent"
                + " to the RPC endpoint.");

        LightRpc lightRpc = new HttpLightRpc(http, this.configEngine::getNodeAddress);
        this.workGenerator = new CpuWorkGenerator(this.configEngine.getWorkThreads());

        this.lightPlayerWallet = createLightWallet(this.configEngine.getWalletSeed(), "walletSeed", lightRpc);
        if (this.configEngine.isBankSeedConfigured())
        {
            this.lightBankWallet = createLightWallet(this.configEngine.getBankWalletSeed(), "bankWalletSeed", lightRpc);
        }

        LightWalletSigner.IndexLookup lookup = new LightWalletSigner.IndexLookup()
        {
            @Override
            public Integer playerIndex(String address)
            {
                return db.getPlayerWalletIndex(address);
            }

            @Override
            public Integer bankIndex(String address)
            {
                return db.getBankWalletIndex(address);
            }
        };

        WalletSigner signer = new LightWalletSigner(this.lightPlayerWallet, this.lightBankWallet, lookup, getLogger());
        return new RPC(this, this.configEngine, http, signer);
    }

    /** One local wallet for {@code seedHex}, or {@code null} (after logging) if the seed is unusable. */
    private LightWallet createLightWallet(String seedHex, String configKey, LightRpc lightRpc)
    {
        byte[] seed = parseSeed(seedHex);
        if (seed == null)
        {
            getLogger().severe(configKey + " is not a usable 64-character hex seed - its light wallet is disabled.");
            return null;
        }

        String representative = this.configEngine.getDefaultRepresentative();
        if (representative.isEmpty())
        {
            // The same seed's master account: a valid address, so new accounts always have a representative.
            representative = WalletIndexBackfill.addressDeriver(seed).apply(0);
            getLogger().warning("No defaultRepresentative (or representatives entry) configured - new "
                    + configKey + " accounts will use " + representative + " as representative.");
        }

        try
        {
            return new LightWallet(seed, "ban_", representative, lightRpc, this.workGenerator,
                    Work.BANANO_THRESHOLD, getLogger());
        }
        catch (IllegalArgumentException e)
        {
            getLogger().log(Level.SEVERE, "Could not create the light wallet for " + configKey + ": "
                    + e.getMessage(), e);
            return null;
        }
        finally
        {
            Arrays.fill(seed, (byte) 0); // LightWallet keeps its own copy
        }
    }

    /** Parses a 64-character hex seed into 32 bytes; {@code null} if it is missing or malformed. */
    static byte[] parseSeed(String seedHex)
    {
        if (seedHex == null || seedHex.length() != 64)
        {
            return null;
        }

        try
        {
            return HexFormat.of().parseHex(seedHex);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    /**
     * Matches records created by older versions (no stored walletIndex) to the seed(s), off the main
     * thread, in both wallet modes. Unmatched records are logged at SEVERE.
     */
    private void startWalletIndexBackfill()
    {
        final byte[] playerSeed = parseSeed(this.configEngine.getWalletSeed());
        final byte[] bankSeed = this.configEngine.isBankSeedConfigured()
                ? parseSeed(this.configEngine.getBankWalletSeed()) : null;
        final int scanLimit = this.configEngine.getWalletIndexScanLimit();

        if (playerSeed == null && bankSeed == null)
        {
            getLogger().warning("Wallet index backfill skipped: no usable wallet seed.");
            return;
        }

        BukkitTask task = getServer().getScheduler().runTaskAsynchronously(this, () ->
        {
            try
            {
                WalletIndexBackfill backfill = new WalletIndexBackfill(this.db, getLogger());

                if (playerSeed != null)
                {
                    backfill.backfillPlayers(WalletIndexBackfill.addressDeriver(playerSeed), scanLimit);
                }
                if (bankSeed != null)
                {
                    backfill.backfillBanks(WalletIndexBackfill.addressDeriver(bankSeed), scanLimit);
                }
            }
            catch (Exception e)
            {
                getLogger().log(Level.SEVERE, "Wallet index backfill failed.", e);
            }
            finally
            {
                if (playerSeed != null)
                {
                    Arrays.fill(playerSeed, (byte) 0);
                }
                if (bankSeed != null)
                {
                    Arrays.fill(bankSeed, (byte) 0);
                }
            }
        });
        this.taskTracker.track(task);
    }

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
