package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.RPC;
import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import net.md_5.bungee.api.chat.*;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class AdminCommand extends BaseCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ConfigEngine configEngine;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;

    public AdminCommand(Plugin plugin, ConfigEngine configEngine, EconomyFuncs economyFuncs, IDBConnector db) {
        this.plugin = plugin;
        this.configEngine = configEngine;
        this.economyFuncs = economyFuncs;
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Player player = null;

        if(sender instanceof Player) {

            player = (Player) sender;

            if (player != null
                    && !player.hasPermission("BananoEconomy.admin")
                    && !player.isOp()) {

                SendMessage(player, "You do not have permission to use this command!", ChatColor.RED);

                return false;

            }

        }

        if(args.length == 0) {

            SendMessage(player, "You must specify arguments to use this command!", ChatColor.RED);

            return false;

        }

        if(args[0].equalsIgnoreCase("setnode")) {

            setNode(player, args);

        }
        else if(args[0].equalsIgnoreCase("freeze")) {

            freezePlayer(player, args);

        }
        else if(args[0].equalsIgnoreCase("unfreeze")) {

            unfreezePlayer(player, args);

        }
        else if(args[0].equalsIgnoreCase("explorer")) {

            if(args.length >= 3) {

                if(args[2].equalsIgnoreCase("view")) {

                    viewExplorer(player, args);

                }
                else if(args[2].equalsIgnoreCase("set")) {

                    setExplorer(player, args);

                }
                else {

                    SendMessage(player, "Invalid argument: " + args[2], ChatColor.RED);

                }

            }
            else {

                SendMessage(player, "Invalid arguments!", ChatColor.RED);

            }

        }
        else if(args[0].equalsIgnoreCase("serverwallet")) {

            if(args[1].equalsIgnoreCase("tip")) {

                tipFromServerWallet(player, args);

            }
            else if(args[1].equalsIgnoreCase("deposit")) {

                showServerWalletDeposit(player);

            }
            else if(args[1].equalsIgnoreCase("withdraw")) {

                withdrawFromServerWallet(player, args);

            }
            else if(args[1].equalsIgnoreCase("balance")) {

                showServerWalletBalance(player);

            }
            else {

                SendMessage(player, "Invalid argument: " + args[1], ChatColor.RED);

            }

        }
        else if(args[0].equalsIgnoreCase("offlinetransactions")
                 && args.length == 2
                 && (args[1].equalsIgnoreCase("enable")
                     || args[1].equalsIgnoreCase("disable"))) {

            this.configEngine.setEnableOfflinePayment(args[1].equalsIgnoreCase("enable"));
            this.configEngine.save();

            SendMessage(player, "Offline transactions are now " + args[1].toLowerCase(Locale.ROOT) + "d.", ChatColor.GREEN);

        }
        else {

            SendMessage(player, "Unknown command arguments!", ChatColor.RED);

        }

        return true;

    }

    private void withdrawFromServerWallet(Player sender, String[] args) {

        // /be serverwallet withdraw [{amount}|all] [ban_destinationaddress]

        new BukkitRunnable() {
            @Override
            public void run() {

                String wallet = configEngine.getMasterWallet();

                try {

                    double amount;

                    if(args[2].equalsIgnoreCase("all")) {

                        amount = RPC.getBalance(wallet);

                    }
                    else {

                        amount = Double.parseDouble(args[2]);

                    }

                    if (amount <= 0) {
                        SendMessage(sender, "Amount has to be greater than 0", ChatColor.RED);
                        return;
                    }

                    String amountStr = Double.toString(amount);

                    // We can keep args.length to 2 by injecting JSON without spaces
                    if (args.length == 4) {

                        final String withdrawAddr = args[3];
                        final String blockHash;

                        try {
                            blockHash = RPC.sendTransaction(wallet, withdrawAddr, amount);
                        } catch (final TransactionError error) {
                            SendMessage(sender, String.format("/withdraw %f %s failed with: %s", amount, withdrawAddr, error.getUserError()), ChatColor.RED);
                            return;
                        }

                        SendMessage(sender, blockHash, ChatColor.YELLOW);

                        try{
                            final URL blockURL = new URL ( configEngine.getExplorerBlock() + blockHash);

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
                        catch (Exception e){
                            System.out.println(e);
                        }
                    } else {
                        throw new Exception();
                    }
                } catch (Exception e) {
                    SendMessage(sender, "Wrong formatting. /be serverwallet withdraw [{amount}|all] [ban_destinationaddress]", ChatColor.RED);
                }
            }
        }.runTaskAsynchronously(plugin);

    }

    private void tipFromServerWallet(Player sender, String[] args) {

        // /be serverwallet tip [{amount}|all] [player]

        if(args.length >= 4) {

            final String sAmount = args[2];
            final double amount;

            try {

                if (sAmount.equalsIgnoreCase("all")) {

                    amount = RPC.getBalance(this.configEngine.getMasterWallet());

                } else {

                    amount = Double.parseDouble(sAmount);

                }

                if (amount <= 0) {

                    SendMessage(sender, String.format("Amount ('%s') has to be greater than 0", sAmount), ChatColor.RED);
                    return;

                }

            } catch (final Exception e) {

                SendMessage(sender, String.format("Amount ('%s') is not a number greater than 0", sAmount), ChatColor.RED);
                return;

            }

            final String targetPlayerName = args[3];
            final PlayerRecord target = findPlayer(this.db, this.configEngine, targetPlayerName);

            if (target == null) {

                SendMessage(sender, "Player needs to be online for you to tip them, or a unique player could not be identified by name,", ChatColor.RED);
                return;

            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

                if (target.isFrozen()) {

                    SendMessage(sender, "You cannot tip " + targetPlayerName + " because their wallet is frozen!", ChatColor.RED);
                    return;

                }

                SendMessage(sender, "Tipping " + target.getPlayerName() + " with " + amount + " bans.", ChatColor.YELLOW);

                final String sWallet = this.configEngine.getMasterWallet();
                final String tWallet = target.getWallet();
                final String blockHash;
                try {
                    blockHash = RPC.sendTransaction(sWallet, tWallet, amount);
                } catch (final TransactionError error) {
                    SendMessage(sender, String.format("Tip of %s to %s failed with: %s", sAmount, targetPlayerName, error.getUserError()), ChatColor.RED);
                    return;
                }

                try {

                    String messageBuild = "";

                    if(args.length > 4) {

                        for(int index = 4; index < args.length; index++) {

                            messageBuild += " " + args[index];

                        }

                    }

                    final String message = messageBuild.trim();
                    final URL blockURL = new URL(new URL(this.configEngine.getExplorerBlock()) + blockHash);
                    final TextComponent blocklink = new TextComponent("Click me to view the transaction in the block explorer");

                    blocklink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
                    blocklink.setUnderlined(true);

                    sender.spigot().sendMessage(MessageGenerator.generateTipSenderMessage(target.getPlayerName(), amount, blockHash, message));
                    sender.spigot().sendMessage(MessageGenerator.generateBlockExplorerLink(this.configEngine, blockHash));

                    Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target.getPlayerUUID()));

                    if (targetPlayer != null
                            && targetPlayer.isOnline()) {

                        targetPlayer.spigot().sendMessage(MessageGenerator.generateTipReceiverMessage(Bukkit.getName(), amount, blockHash, message));
                        targetPlayer.spigot().sendMessage(blocklink);

                    } else {

                        // Generate an offline transaction record to tell them when they next log in
                        OfflinePaymentRecord paymentRecord = new OfflinePaymentRecord(UUID.fromString(target.getPlayerUUID()), Bukkit.getName(), amount, blockHash, LocalDateTime.now(), message);

                        // Now save the payment record
                        this.db.saveOfflinePayment(paymentRecord);

                    }

                } catch (Exception e) {
                    System.out.println(e);
                }

            });

        }
        else {

            SendMessage(sender, "Invalid number of arguments! Example call is '/be serverwallet tip [{amount}|all] [player]'", ChatColor.RED);

        }

    }

    private void showServerWalletBalance(Player sender) {

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try {

                final Double balance = RPC.getBalance(RPC.getMasterWallet());
                final DecimalFormat df = new DecimalFormat("#.##");

                SendMessage(sender, "The server's current balance is: " + df.format(balance) + " bans", ChatColor.YELLOW);

            }
            catch (Exception ex) {

                SendMessage(sender, "An error occurred retrieving the server's balance! Please try again in a moment.", ChatColor.RED);

            }

        });

    }

    private void showServerWalletDeposit(Player sender) {

        String wallet = RPC.getMasterWallet();

        try{

            TextComponent clickableWallet = new TextComponent(wallet);
            clickableWallet.setClickEvent(new ClickEvent( ClickEvent.Action.COPY_TO_CLIPBOARD, wallet));

            TextComponent walletHoverText = new TextComponent("Click here to copy this wallet address to the clipboard");
            walletHoverText.setColor(net.md_5.bungee.api.ChatColor.WHITE);
            clickableWallet.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[] { walletHoverText}));

            if(sender != null) {

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
            else {

                SendMessage(sender, "The server wallet address is " + wallet + ". Copy this url to a browser to view in the account explorer: ", ChatColor.GREEN);
                SendMessage(sender, this.configEngine.getExplorerAccount() + wallet, ChatColor.GREEN);

            }

        }
        catch (Exception e){

            SendMessage(sender, "An error occurred while processing your request!", ChatColor.RED);
            System.out.println(e);

        }

    }

    private void viewExplorer(Player sender, String[] args) {

        // /be explorer [account|block] set [http://[url/ip address]:[port]/]
        // /be explorer [account|block] view

        if(args.length >= 3) {

            String explorerType = "block";
            String explorerAddress = "";

            if(args[1].equalsIgnoreCase("account")) {

                explorerType = "account";
                explorerAddress = this.configEngine.getExplorerAccount();

            }
            else if(args[1].equalsIgnoreCase("block")) {

                explorerType = "block";
                explorerAddress = this.configEngine.getExplorerBlock();

            }
            else {

                SendMessage(sender, "Invalid argument: " + args[1], ChatColor.RED);

                return;

            }

            if(sender != null) {

                TextComponent message = new TextComponent("The " + explorerType + " address is: " + explorerAddress);
                message.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, explorerAddress));
                message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[] { new TextComponent("Click here to copy the address to the clipboard.") }));

                sender.spigot().sendMessage(message);

            }
            else {

                SendMessage(sender, "The " + explorerType + " address is: " + explorerAddress, ChatColor.AQUA);

            }

        }
        else {

            SendMessage(sender, "Invalid number of arguments! Example call is '/be explorer [account|block] view'", ChatColor.RED);

        }

    }

    private void setExplorer(Player sender, String[] args) {

        // /be explorer [account|block] set [http://[url/ip address]:[port]/]
        // /be explorer [account|block] view

        if(args.length >= 4) {

            String newAddress = args[3];

            if(args[1].equalsIgnoreCase("account")) {

                this.configEngine.setExplorerAccount(newAddress);

            }
            else if(args[1].equalsIgnoreCase("block")) {

                this.configEngine.setExplorerBlock(newAddress);

            }
            else {

                SendMessage(sender, "Invalid argument: " + args[1], ChatColor.RED);

                return;

            }

            this.configEngine.save();

            SendMessage(sender, "The " + args[1] + " address has been changed to " + newAddress, ChatColor.GREEN);

        }
        else {

            SendMessage(sender, "Invalid number of arguments! Example call is '/be explorer [account|block] set [new url]'", ChatColor.RED);

        }

    }

    private void freezePlayer(Player sender, String[] args) {

        // /be [freeze|unfreeze] [player1] [player2] ...

        if (args.length <= 1) {

            SendMessage(sender, "You must specify one or more players to freeze!", ChatColor.RED);

            return;

        }

        for (int index = 1; index < args.length; index++) {

            final String playerName = args[index];
            Optional<OfflinePlayer> p = Arrays.stream(Bukkit.getOfflinePlayers()).filter(x -> x.getName().equalsIgnoreCase(playerName)).findFirst();

            if (p == null
                  || !p.isPresent()){

                boolean ret = economyFuncs.freezePlayer(playerName);

                if (!ret) {

                    SendMessage(sender, String.format("%s could not be found!", playerName), ChatColor.RED);

                }
                else {

                    SendMessage(sender, String.format("%s account has been frozen", playerName), ChatColor.GREEN);

                }

            }
            else {

                economyFuncs.freezePlayer(p.get());

                SendMessage(sender, String.format("%s account has been frozen", playerName), ChatColor.GREEN);

            }

        }

    }

    private void unfreezePlayer(Player sender, String[] args) {

        // /be [freeze|unfreeze] [player1] [player2] ...

        if (args.length <= 1) {

            SendMessage(sender, "You must specify one or more players to unfreeze!", ChatColor.RED);

            return;

        }

        for (int index = 1; index < args.length; index++) {

            final String playerName = args[index];
            Optional<OfflinePlayer> p = Arrays.stream(Bukkit.getOfflinePlayers()).filter(x -> x.getName().equalsIgnoreCase(playerName)).findFirst();

            if (p == null
                    || !p.isPresent()){

                boolean ret = economyFuncs.unfreezePlayer(playerName);

                if (!ret) {

                    SendMessage(sender, String.format("%s could not be found!", playerName), ChatColor.RED);

                }
                else {

                    SendMessage(sender, String.format("%s account has been unfrozen", playerName), ChatColor.GREEN);

                }

            }
            else {

                economyFuncs.unfreezePlayer(p.get());

                SendMessage(sender, String.format("%s account has been unfrozen", playerName), ChatColor.GREEN);

            }

        }

    }

    private void setNode(Player sender, String[] args) {

        // /be setnode [http://[url/ip address]:[port]/]

        // Did they call things correctly?
        if (args.length != 2) {

            SendMessage(sender, "You need to provide the new node address in the format 'http://[url/ip address][:port]' with no spaces.", ChatColor.RED);
            return;

        }

        String newNodeAddress = args[1];

        configEngine.setNodeAddress(newNodeAddress);
        configEngine.save();

        SendMessage(sender, "The node address has been set to '" + newNodeAddress + "'.", ChatColor.GREEN);

        String masterWallet = this.configEngine.getMasterWallet();

        if(!RPC.wallet_exists()){

            SendMessage(sender, "The master wallet does not exist... attempting to configure the master wallet.", ChatColor.AQUA);

            RPC.walletCreate();

            masterWallet = RPC.accountCreate(0);

            this.configEngine.setMasterWallet(masterWallet);
            this.configEngine.save();

        }

        if(sender != null) {

            sender.spigot().sendMessage(MessageGenerator.generateClickableAddressMessage(this.configEngine, "The master wallet has been set to: ", masterWallet));

        }
        else {

            SendMessage(sender, "The master wallet address is " + masterWallet, ChatColor.AQUA);

        }

    }

}