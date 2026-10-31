package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.TipService;
import banano.bananominecraft.bananoeconomy.services.WithdrawService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import net.md_5.bungee.api.chat.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class AdminCommand extends BaseCommand implements CommandExecutor
{
    // -------------------------------------------------------------------------
    // Permission node
    // -------------------------------------------------------------------------
    public static final String PERMISSION_ADMIN          = "BananoEconomy.admin";

    // -------------------------------------------------------------------------
    // Top-level subcommand arguments
    // -------------------------------------------------------------------------
    public static final String CMD_SETNODE               = "setnode";
    public static final String CMD_FREEZE                = "freeze";
    public static final String CMD_UNFREEZE              = "unfreeze";
    public static final String CMD_EXPLORER              = "explorer";
    public static final String CMD_SERVERWALLET          = "serverwallet";
    public static final String CMD_OFFLINETRANSACTIONS   = "offlinetransactions";
    public static final String CMD_WEBSOCKET             = "websocket";

    // -------------------------------------------------------------------------
    // explorer sub-arguments
    // -------------------------------------------------------------------------
    public static final String CMD_ACCOUNT               = "account";
    public static final String CMD_BLOCK                 = "block";
    public static final String CMD_VIEW                  = "view";
    public static final String CMD_SET                   = "set";

    // -------------------------------------------------------------------------
    // serverwallet sub-arguments
    // -------------------------------------------------------------------------
    public static final String CMD_TIP                   = "tip";
    public static final String CMD_DEPOSIT               = "deposit";
    public static final String CMD_WITHDRAW              = "withdraw";
    public static final String CMD_BALANCE               = "balance";

    // -------------------------------------------------------------------------
    // offlinetransactions sub-arguments
    // -------------------------------------------------------------------------
    public static final String CMD_ENABLE                = "enable";
    public static final String CMD_DISABLE               = "disable";

    // -------------------------------------------------------------------------
    // websocket sub-arguments
    // -------------------------------------------------------------------------
    public static final String CMD_RECONNECT             = "reconnect";
    public static final String CMD_STATUS                = "status";

    // -------------------------------------------------------------------------
    // Common argument values
    // -------------------------------------------------------------------------
    public static final String ARG_ALL                   = "all";

    // -------------------------------------------------------------------------
    // WebSocket URL scheme prefixes
    // -------------------------------------------------------------------------
    private static final String WS_SCHEME_SECURE         = "wss://";
    private static final String WS_SCHEME                = "ws://";

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------
    private final Plugin plugin;
    private final ConfigEngine configEngine;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;
    private final RPC rpc;
    private final BananoWebSocket webSocket;
    private final TaskTracker taskTracker;
    private final MessageGenerator messageGenerator;
    private final TipService tipService;
    private final WithdrawService withdrawService;

    public AdminCommand(Plugin plugin, ConfigEngine configEngine, EconomyFuncs economyFuncs,
                        IDBConnector db, RPC rpc, BananoWebSocket webSocket, TaskTracker taskTracker,
                        MessageGenerator messageGenerator)
    {
        super(plugin.getLogger());

        this.plugin = plugin;
        this.configEngine = configEngine;
        this.economyFuncs = economyFuncs;
        this.db = db;
        this.rpc = rpc;
        this.webSocket = webSocket;
        this.taskTracker = taskTracker;
        this.messageGenerator = messageGenerator;
        this.tipService = new TipService(db, rpc);
        this.withdrawService = new WithdrawService(rpc);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        Player player = null;

        if (sender instanceof Player)
        {
            player = (Player) sender;

            if (player != null
                    && !player.hasPermission(PERMISSION_ADMIN)
                    && !player.isOp())
            {
                SendMessage(player, "You do not have permission to use this command!", ChatColor.RED);
                return false;
            }
        }

        if (args.length == 0)
        {
            SendMessage(player, "You must specify arguments to use this command!", ChatColor.RED);
            return false;
        }

        if (args[0].equalsIgnoreCase(CMD_SETNODE))
        {
            setNode(player, args);
        }
        else if (args[0].equalsIgnoreCase(CMD_FREEZE))
        {
            freezePlayer(player, args);
        }
        else if (args[0].equalsIgnoreCase(CMD_UNFREEZE))
        {
            unfreezePlayer(player, args);
        }
        else if (args[0].equalsIgnoreCase(CMD_EXPLORER))
        {
            if (args.length >= 3)
            {
                if (args[2].equalsIgnoreCase(CMD_VIEW))
                {
                    viewExplorer(player, args);
                }
                else if (args[2].equalsIgnoreCase(CMD_SET))
                {
                    setExplorer(player, args);
                }
                else
                {
                    SendMessage(player, "Invalid argument: " + args[2], ChatColor.RED);
                }
            }
            else
            {
                SendMessage(player, "Invalid arguments!", ChatColor.RED);
            }
        }
        else if (args[0].equalsIgnoreCase(CMD_SERVERWALLET))
        {
            if (args.length < 2)
            {
                SendMessage(player, "Usage: /be serverwallet [tip|deposit|withdraw|balance]", ChatColor.RED);
                return false;
            }

            if (args[1].equalsIgnoreCase(CMD_TIP))
            {
                tipFromServerWallet(player, args);
            }
            else if (args[1].equalsIgnoreCase(CMD_DEPOSIT))
            {
                showServerWalletDeposit(player);
            }
            else if (args[1].equalsIgnoreCase(CMD_WITHDRAW))
            {
                withdrawFromServerWallet(player, args);
            }
            else if (args[1].equalsIgnoreCase(CMD_BALANCE))
            {
                showServerWalletBalance(player);
            }
            else
            {
                SendMessage(player, "Invalid argument: " + args[1], ChatColor.RED);
            }
        }
        else if (args[0].equalsIgnoreCase(CMD_OFFLINETRANSACTIONS)
                 && args.length == 2
                 && (args[1].equalsIgnoreCase(CMD_ENABLE)
                     || args[1].equalsIgnoreCase(CMD_DISABLE)))
        {
            this.configEngine.setEnableOfflinePayment(args[1].equalsIgnoreCase(CMD_ENABLE));
            this.configEngine.save();
            SendMessage(player, "Offline transactions are now " + args[1].toLowerCase(Locale.ROOT) + "d.", ChatColor.GREEN);
        }
        else if (args[0].equalsIgnoreCase(CMD_WEBSOCKET))
        {
            if (args.length < 2)
            {
                SendMessage(player, "Usage: /be websocket [reconnect|set|status]", ChatColor.RED);
                return false;
            }

            if (args[1].equalsIgnoreCase(CMD_RECONNECT))
            {
                reconnectWebSocket(player);
            }
            else if (args[1].equalsIgnoreCase(CMD_SET))
            {
                setWebSocketUrl(player, args);
            }
            else if (args[1].equalsIgnoreCase(CMD_STATUS))
            {
                SendMessage(player, "WebSocket URL: " + webSocket.getCurrentUrl(), ChatColor.AQUA);
            }
            else
            {
                SendMessage(player, "Invalid argument: " + args[1] + ". Usage: /be websocket [reconnect|set|status]", ChatColor.RED);
            }
        }
        else
        {
            SendMessage(player, "Unknown command arguments!", ChatColor.RED);
        }

        return true;
    }

    private void withdrawFromServerWallet(Player sender, String[] args)
    {
        // /be serverwallet withdraw [{amount}|all] [ban_destinationaddress]

        BukkitTask withdrawTask = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                String wallet = configEngine.getMasterWallet();

                try
                {
                    double amount = withdrawService.resolveAmount(wallet, args[2]);

                    if (amount <= 0)
                    {
                        SendMessage(sender, "Amount has to be greater than 0", ChatColor.RED);
                        return;
                    }

                    String amountStr = Double.toString(amount);

                    if (args.length == 4)
                    {
                        final String withdrawAddr = args[3];

                        WithdrawService.WithdrawResult result =
                                withdrawService.withdraw(wallet, withdrawAddr, amount);

                        if (!result.success())
                        {
                            SendMessage(sender, String.format("/withdraw %f %s failed with: %s", amount, withdrawAddr, result.userError()), ChatColor.RED);
                            return;
                        }

                        final String blockHash = result.blockHash();
                        SendMessage(sender, blockHash, ChatColor.YELLOW);

                        try
                        {
                            final URL blockURL = new URL(configEngine.getExplorerBlock() + blockHash);

                            sender.spigot().sendMessage((new ComponentBuilder("You have sent ")
                                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                                    .append(amountStr)
                                    .color(net.md_5.bungee.api.ChatColor.WHITE)
                                    .bold(true)
                                    .append(" to ")
                                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                                    .append(withdrawAddr)
                                    .color(net.md_5.bungee.api.ChatColor.WHITE)
                                    .bold(true)
                                    .append(" with block ID : ")
                                    .append(blockHash)
                                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                                    .bold(true)
                                    .create()));

                            TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");
                            blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                            blocklink.setUnderlined(true);
                            sender.spigot().sendMessage(blocklink);
                        }
                        catch (Exception e)
                        {
                            logger.log(Level.WARNING, "Failed to build block explorer link.", e);
                        }
                    }
                    else
                    {
                        throw new Exception();
                    }
                }
                catch (Exception e)
                {
                    SendMessage(sender, "Usage: /be serverwallet withdraw [amount|all] [ban_address]", ChatColor.RED);
                }
            }
        }.runTaskAsynchronously(plugin);

        taskTracker.track(withdrawTask);
    }

    private void tipFromServerWallet(Player sender, String[] args)
    {
        // /be serverwallet tip [{amount}|all] [player]

        if (args.length >= 4)
        {
            final String sAmount = args[2];
            final double amount;

            try
            {
                if (sAmount.equalsIgnoreCase(ARG_ALL))
                {
                    amount = rpc.getBalance(this.configEngine.getMasterWallet());
                }
                else
                {
                    amount = Double.parseDouble(sAmount);
                }

                if (amount <= 0)
                {
                    SendMessage(sender, String.format("Amount ('%s') has to be greater than 0", sAmount), ChatColor.RED);
                    return;
                }
            }
            catch (final Exception e)
            {
                SendMessage(sender, String.format("Amount ('%s') is not a number greater than 0", sAmount), ChatColor.RED);
                return;
            }

            final String targetPlayerName = args[3];
            final PlayerRecord target = findPlayer(this.db, this.configEngine, targetPlayerName);

            if (target == null)
            {
                SendMessage(sender, "Player needs to be online for you to tip them, or a unique player could not be identified by name,", ChatColor.RED);
                return;
            }

            BukkitTask tipTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                if (target.isFrozen())
                {
                    SendMessage(sender, "You cannot tip " + targetPlayerName + " because their wallet is frozen!", ChatColor.RED);
                    return;
                }

                SendMessage(sender, "Tipping " + target.getPlayerName() + " with " + amount + " bans.", ChatColor.YELLOW);

                final String sWallet = this.configEngine.getMasterWallet();
                final String message = args.length > 4
                        ? String.join(" ", Arrays.copyOfRange(args, 4, args.length))
                        : "";

                final Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target.getPlayerUUID()));
                final boolean recipientOnline = targetPlayer != null && targetPlayer.isOnline();

                final TipService.TransferResult result = tipService.transfer(
                        sWallet, target.getWallet(), UUID.fromString(target.getPlayerUUID()),
                        amount, message, Bukkit.getName(), recipientOnline);

                if (result.status() == TipService.TransferResult.Status.FAILED)
                {
                    SendMessage(sender, String.format("Tip of %s to %s failed with: %s", sAmount, targetPlayerName, result.userError()), ChatColor.RED);
                    return;
                }

                try
                {
                    final String blockHash = result.blockHash();
                    final URL blockURL = new URL(new URL(this.configEngine.getExplorerBlock()) + blockHash);
                    final TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");

                    blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                    blocklink.setUnderlined(true);

                    sender.spigot().sendMessage(messageGenerator.generateTipSenderMessage(Locale.ENGLISH, target.getPlayerName(), amount, blockHash, message));
                    sender.spigot().sendMessage(messageGenerator.generateBlockExplorerLink(Locale.ENGLISH, blockHash));

                    // SENT_OFFLINE: the service already saved the offline payment record.
                    if (result.status() == TipService.TransferResult.Status.SENT_ONLINE)
                    {
                        targetPlayer.spigot().sendMessage(messageGenerator.generateTipReceiverMessage(Locale.ENGLISH, Bukkit.getName(), amount, blockHash, message));
                        targetPlayer.spigot().sendMessage(blocklink);
                    }
                }
                catch (Exception e)
                {
                    logger.log(Level.WARNING, "Error during serverwallet tip.", e);
                }
            });

            taskTracker.track(tipTask);
        }
        else
        {
            SendMessage(sender, "Invalid number of arguments! Example call is '/be serverwallet tip [{amount}|all] [player]'", ChatColor.RED);
        }
    }

    private void showServerWalletBalance(Player sender)
    {
        BukkitTask balanceTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            if (!plugin.isEnabled())
            {
                return;
            }

            try
            {
                final Double balance = rpc.getBalance(rpc.getMasterWallet());
                final DecimalFormat df = new DecimalFormat("#.##");

                SendMessage(sender, "The server's current balance is: " + df.format(balance) + " bans", ChatColor.YELLOW);
            }
            catch (Exception ex)
            {
                SendMessage(sender, "An error occurred retrieving the server's balance! Please try again in a moment.", ChatColor.RED);
            }
        });

        taskTracker.track(balanceTask);
    }

    private void showServerWalletDeposit(Player sender)
    {
        String wallet = rpc.getMasterWallet();

        try
        {
            TextComponent clickableWallet = new TextComponent(wallet);
            clickableWallet.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, wallet));

            TextComponent walletHoverText = new TextComponent("Click here to copy this wallet address to the clipboard");
            walletHoverText.setColor(net.md_5.bungee.api.ChatColor.WHITE);
            clickableWallet.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[] { walletHoverText }));

            if (sender != null)
            {
                URL walletURL = new URL(this.configEngine.getExplorerAccount() + wallet);
                sender.spigot().sendMessage((new ComponentBuilder("Deposit bans to the server wallet address: ")
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append(clickableWallet)
                        .color(net.md_5.bungee.api.ChatColor.WHITE)
                        .bold(true).create()));

                TextComponent addrlink = new TextComponent("Click me to view the server wallet account in the block explorer");
                addrlink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, walletURL.toString()));
                addrlink.setUnderlined(true);
                sender.spigot().sendMessage(addrlink);
            }
            else
            {
                SendMessage(sender, "The server wallet address is " + wallet + ". Copy this url to a browser to view in the account explorer: ", ChatColor.GREEN);
                SendMessage(sender, this.configEngine.getExplorerAccount() + wallet, ChatColor.GREEN);
            }
        }
        catch (Exception e)
        {
            SendMessage(sender, "An error occurred while processing your request!", ChatColor.RED);
            logger.log(Level.WARNING, "AdminCommand error.", e);
        }
    }

    private void viewExplorer(Player sender, String[] args)
    {
        // /be explorer [account|block] set [http://[url/ip address]:[port]/]
        // /be explorer [account|block] view
        if (args.length >= 3)
        {
            String explorerType    = CMD_BLOCK;
            String explorerAddress = "";

            if (args[1].equalsIgnoreCase(CMD_ACCOUNT))
            {
                explorerType    = CMD_ACCOUNT;
                explorerAddress = this.configEngine.getExplorerAccount();
            }
            else if (args[1].equalsIgnoreCase(CMD_BLOCK))
            {
                explorerType    = CMD_BLOCK;
                explorerAddress = this.configEngine.getExplorerBlock();
            }
            else
            {
                SendMessage(sender, "Invalid argument: " + args[1], ChatColor.RED);
                return;
            }

            if (sender != null)
            {
                TextComponent message = new TextComponent("The " + explorerType + " address is: " + explorerAddress);
                message.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, explorerAddress));
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[] { new TextComponent("Click here to copy the address to the clipboard.") }));

                sender.spigot().sendMessage(message);
            }
            else
            {
                SendMessage(sender, "The " + explorerType + " address is: " + explorerAddress, ChatColor.AQUA);
            }
        }
        else
        {
            SendMessage(sender, "Invalid number of arguments! Example call is '/be explorer [account|block] view'", ChatColor.RED);
        }
    }

    private void setExplorer(Player sender, String[] args)
    {
        // /be explorer [account|block] set [http://[url/ip address]:[port]/]

        if (args.length >= 4)
        {
            String newAddress = args[3];

            if (args[1].equalsIgnoreCase(CMD_ACCOUNT))
            {
                this.configEngine.setExplorerAccount(newAddress);
            }
            else if (args[1].equalsIgnoreCase(CMD_BLOCK))
            {
                this.configEngine.setExplorerBlock(newAddress);
            }
            else
            {
                SendMessage(sender, "Invalid argument: " + args[1], ChatColor.RED);
                return;
            }

            this.configEngine.save();
            SendMessage(sender, "The " + args[1] + " address has been changed to " + newAddress, ChatColor.GREEN);
        }
        else
        {
            SendMessage(sender, "Invalid number of arguments! Example call is '/be explorer [account|block] set [new url]'", ChatColor.RED);
        }
    }

    private void freezePlayer(Player sender, String[] args)
    {
        // /be [freeze|unfreeze] [player1] [player2] ...

        if (args.length <= 1)
        {
            SendMessage(sender, "You must specify one or more players to freeze!", ChatColor.RED);
            return;
        }

        for (int index = 1; index < args.length; index++)
        {
            final String playerName = args[index];
            Optional<OfflinePlayer> p = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(x -> x.getName().equalsIgnoreCase(playerName))
                    .findFirst();

            if (p == null || !p.isPresent())
            {
                boolean ret = economyFuncs.freezePlayer(playerName);

                if (!ret)
                {
                    SendMessage(sender, String.format("%s could not be found!", playerName), ChatColor.RED);
                }
                else
                {
                    SendMessage(sender, String.format("%s account has been frozen", playerName), ChatColor.GREEN);
                }
            }
            else
            {
                economyFuncs.freezePlayer(p.get());
                SendMessage(sender, String.format("%s account has been frozen", playerName), ChatColor.GREEN);
            }
        }
    }

    private void unfreezePlayer(Player sender, String[] args)
    {
        // /be [freeze|unfreeze] [player1] [player2] ...

        if (args.length <= 1)
        {
            SendMessage(sender, "You must specify one or more players to unfreeze!", ChatColor.RED);
            return;
        }

        for (int index = 1; index < args.length; index++)
        {
            final String playerName = args[index];
            Optional<OfflinePlayer> p = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(x -> x.getName().equalsIgnoreCase(playerName))
                    .findFirst();

            if (p == null || !p.isPresent())
            {
                boolean ret = economyFuncs.unfreezePlayer(playerName);

                if (!ret)
                {
                    SendMessage(sender, String.format("%s could not be found!", playerName), ChatColor.RED);
                }
                else
                {
                    SendMessage(sender, String.format("%s account has been unfrozen", playerName), ChatColor.GREEN);
                }
            }
            else
            {
                economyFuncs.unfreezePlayer(p.get());
                SendMessage(sender, String.format("%s account has been unfrozen", playerName), ChatColor.GREEN);
            }
        }
    }

    private void reconnectWebSocket(Player sender)
    {
        // Run async so the close+connect cycle doesn't block the main thread
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            if (!plugin.isEnabled())
            {
                return;
            }

            try
            {
                webSocket.reconnect();
                SendMessage(sender, "WebSocket reconnecting to " + webSocket.getCurrentUrl(), ChatColor.GREEN);
            }
            catch (Exception e)
            {
                SendMessage(sender, "WebSocket reconnect failed: " + e.getMessage(), ChatColor.RED);
                logger.log(Level.WARNING, "WebSocket admin reconnect failed.", e);
            }
        });
        taskTracker.track(task);
    }

    private void setWebSocketUrl(Player sender, String[] args)
    {
        // /be websocket set [wss://url]

        if (args.length < 3)
        {
            SendMessage(sender, "Usage: /be websocket set [wss://url]", ChatColor.RED);
            return;
        }

        String newUrl = args[2];

        if (!newUrl.startsWith(WS_SCHEME_SECURE) && !newUrl.startsWith(WS_SCHEME))
        {
            SendMessage(sender, "URL must start with 'wss://' or 'ws://'", ChatColor.RED);
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            if (!plugin.isEnabled())
            {
                return;
            }

            try
            {
                webSocket.setUrl(newUrl);
                SendMessage(sender, "WebSocket URL set to '" + newUrl + "' and reconnecting.", ChatColor.GREEN);
            }
            catch (Exception e)
            {
                SendMessage(sender, "Failed to switch WebSocket URL: " + e.getMessage(), ChatColor.RED);
                logger.log(Level.WARNING, "WebSocket URL switch failed.", e);
            }
        });
        taskTracker.track(task);
    }

    private void setNode(Player sender, String[] args)
    {
        // /be setnode [http://[url/ip address]:[port]/]

        if (args.length != 2)
        {
            SendMessage(sender, "You need to provide the new node address in the format 'http://[url/ip address][:port]' with no spaces.", ChatColor.RED);
            return;
        }

        String newNodeAddress = args[1];

        configEngine.setNodeAddress(newNodeAddress);
        configEngine.save();

        SendMessage(sender, "The node address has been set to '" + newNodeAddress + "'.", ChatColor.GREEN);

        String masterWallet = this.configEngine.getMasterWallet();

        if (!rpc.wallet_exists())
        {
            SendMessage(sender, "The master wallet does not exist... attempting to configure the master wallet.", ChatColor.AQUA);

            rpc.walletCreate();

            masterWallet = rpc.accountCreate(0);

            this.configEngine.setMasterWallet(masterWallet);
            this.configEngine.save();
        }

        if (sender != null)
        {
            sender.spigot().sendMessage(messageGenerator.generateClickableAddressMessage(
                    "The master wallet has been set to: ", masterWallet));
        }
        else
        {
            SendMessage(sender, "The master wallet address is " + masterWallet, ChatColor.AQUA);
        }
    }
}
