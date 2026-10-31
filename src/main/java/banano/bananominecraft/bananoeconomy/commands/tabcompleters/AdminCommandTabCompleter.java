package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.commands.AdminCommand;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class AdminCommandTabCompleter implements TabCompleter
{
    /** Default WebSocket URL shown as a hint for {@code /be websocket set}. */
    private static final String WS_DEFAULT_URL      = "wss://ws.banano.trade";

    /** Banano address prefix hint for {@code /be serverwallet withdraw}. */
    private static final String BAN_ADDRESS_PREFIX  = "ban_";

    private final IDBConnector db;
    private final ConfigEngine configEngine;

    public AdminCommandTabCompleter(ConfigEngine configEngine, IDBConnector db)
    {
        this.configEngine = configEngine;
        this.db = db;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> results = new ArrayList<>();

        if(args.length == 1)
        {
            results.add(AdminCommand.CMD_SETNODE);
            results.add(AdminCommand.CMD_FREEZE);
            results.add(AdminCommand.CMD_UNFREEZE);
            results.add(AdminCommand.CMD_EXPLORER);
            results.add(AdminCommand.CMD_SERVERWALLET);
            results.add(AdminCommand.CMD_OFFLINETRANSACTIONS);
            results.add(AdminCommand.CMD_WEBSOCKET);
        }
        else if(args.length >= 2
                  && args[0].equalsIgnoreCase(AdminCommand.CMD_FREEZE))
        {
            // Provide a list of unfrozen players
            List<PlayerRecord> unfrozenPlayers = this.db.getUnfrozenPlayers();

            for (PlayerRecord record : unfrozenPlayers)
            {
                results.add(record.getPlayerName());
            }
        }
        else if(args.length >= 2
                && args[0].equalsIgnoreCase(AdminCommand.CMD_UNFREEZE))
        {
            // Provide a list of frozen players
            List<PlayerRecord> frozenPlayers = this.db.getFrozenPlayers();

            for (PlayerRecord record : frozenPlayers)
            {
                results.add(record.getPlayerName());
            }
        }
        else if(args.length == 2)
        {
            if(args[0].equalsIgnoreCase(AdminCommand.CMD_EXPLORER))
            {
                results.add(AdminCommand.CMD_ACCOUNT);
                results.add(AdminCommand.CMD_BLOCK);
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_SERVERWALLET))
            {
                results.add(AdminCommand.CMD_TIP);
                results.add(AdminCommand.CMD_DEPOSIT);
                results.add(AdminCommand.CMD_WITHDRAW);
                results.add(AdminCommand.CMD_BALANCE);
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_SETNODE))
            {
                if(args[1] == null
                     || args[1].length() == 0)
                {
                    results.add("[http://[url/ip address]:[port]/]");
                    results.add("[https://[url/ip address]:[port]/]");
                }
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_OFFLINETRANSACTIONS))
            {
                results.add(AdminCommand.CMD_ENABLE);
                results.add(AdminCommand.CMD_DISABLE);
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_WEBSOCKET))
            {
                results.add(AdminCommand.CMD_RECONNECT);
                results.add(AdminCommand.CMD_SET);
                results.add(AdminCommand.CMD_STATUS);
            }
        }
        else if(args.length == 3)
        {
            if(args[0].equalsIgnoreCase(AdminCommand.CMD_EXPLORER))
            {
                results.add(AdminCommand.CMD_SET);
                results.add(AdminCommand.CMD_VIEW);
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_WEBSOCKET)
                    && args[1].equalsIgnoreCase(AdminCommand.CMD_SET))
            {
                results.add(WS_DEFAULT_URL);
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_SERVERWALLET))
            {
                if(args[1].equalsIgnoreCase(AdminCommand.CMD_TIP)
                     || args[1].equalsIgnoreCase(AdminCommand.CMD_WITHDRAW))
                {
                    results.add(AdminCommand.ARG_ALL);

                    // Amount
                    if(args[2] == null
                            || args[2].length() == 0)
                    {
                        results.add("[amount]");
                    }
                }
            }
        }
        else if(args.length == 4)
        {
            if(args[0].equalsIgnoreCase(AdminCommand.CMD_EXPLORER)
                && args[2].equalsIgnoreCase(AdminCommand.CMD_SET))
            {
                if(args[3] == null
                        || args[3].length() == 0)
                {
                    results.add("[http://[url/ip address]:[port]/]");
                    results.add("[https://[url/ip address]:[port]/]");
                }
            }
            else if(args[0].equalsIgnoreCase(AdminCommand.CMD_SERVERWALLET))
            {
                if(args[1].equalsIgnoreCase(AdminCommand.CMD_TIP))
                {
                    // List of players
                    for (Player player : Bukkit.getOnlinePlayers())
                    {
                        results.add(player.getName());
                    }

                    if(this.configEngine.getEnableOfflinePayment())
                    {
                        for(OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers())
                        {
                            results.add(offlinePlayer.getName());
                        }
                    }
                }
                else if(args[1].equalsIgnoreCase(AdminCommand.CMD_WITHDRAW))
                {
                    if(args[3] == null
                            || args[3].length() == 0)
                    {
                        results.add(BAN_ADDRESS_PREFIX);
                    }
                }
            }
        }

        return StringUtil.copyPartialMatches(args[args.length - 1], results, new ArrayList<>());
    }
}
