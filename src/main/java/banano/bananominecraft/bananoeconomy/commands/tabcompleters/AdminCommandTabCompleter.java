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
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminCommandTabCompleter implements TabCompleter
{
    /** Default WebSocket URL shown as a hint for {@code /be websocket set}. */
    private static final String WS_DEFAULT_URL      = "wss://ws.banano.trade";

    /** Banano address prefix hint for {@code /be serverwallet withdraw}. */
    private static final String BAN_ADDRESS_PREFIX  = "ban_";

    /** How long cached frozen/unfrozen player names are served before a refresh is triggered. */
    private static final long NAME_CACHE_TTL_MS     = 30_000;

    private final IDBConnector db;
    private final ConfigEngine configEngine;
    private final Executor refreshExecutor;

    // getFrozenPlayers/getUnfrozenPlayers scan the whole users table, which is far too slow to
    // run on the main thread on every Tab keypress. Suggestions are served from these cached
    // lists, refreshed off-thread when stale; a stale (or initially empty) list may be served
    // while the refresh runs.
    private final AtomicReference<List<String>> frozenNames   = new AtomicReference<>(List.of());
    private final AtomicReference<List<String>> unfrozenNames = new AtomicReference<>(List.of());
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile long lastRefreshMillis = 0; // 0 = never refreshed

    public AdminCommandTabCompleter(ConfigEngine configEngine, IDBConnector db)
    {
        this(configEngine, db, ForkJoinPool.commonPool());
    }

    /** Executor-injecting constructor — pass {@code Runnable::run} in tests for a synchronous refresh. */
    AdminCommandTabCompleter(ConfigEngine configEngine, IDBConnector db, Executor refreshExecutor)
    {
        this.configEngine = configEngine;
        this.db = db;
        this.refreshExecutor = refreshExecutor;
    }

    /** Triggers an asynchronous refresh of the cached player-name lists if they are stale. */
    private void refreshNamesIfStale()
    {
        long last = this.lastRefreshMillis;

        if (last != 0 && System.currentTimeMillis() - last < NAME_CACHE_TTL_MS)
        {
            return;
        }

        if (!this.refreshing.compareAndSet(false, true))
        {
            return; // a refresh is already running
        }

        try
        {
            this.refreshExecutor.execute(() ->
            {
                try
                {
                    this.frozenNames.set(namesOf(this.db.getFrozenPlayers()));
                    this.unfrozenNames.set(namesOf(this.db.getUnfrozenPlayers()));
                }
                catch (Exception e)
                {
                    // Keep serving the previous lists; retry after the TTL rather than hammering a down DB.
                    Logger.getLogger(AdminCommandTabCompleter.class.getName())
                            .log(Level.WARNING, "Failed to refresh admin tab-completion player names.", e);
                }
                finally
                {
                    this.lastRefreshMillis = System.currentTimeMillis();
                    this.refreshing.set(false);
                }
            });
        }
        catch (RejectedExecutionException e)
        {
            this.refreshing.set(false);
        }
    }

    private static List<String> namesOf(List<PlayerRecord> records)
    {
        List<String> names = new ArrayList<>();

        if (records != null)
        {
            for (PlayerRecord record : records)
            {
                if (record != null && record.getPlayerName() != null)
                {
                    names.add(record.getPlayerName());
                }
            }
        }

        return List.copyOf(names);
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
            // Provide a list of unfrozen players (cached; see refreshNamesIfStale)
            refreshNamesIfStale();
            results.addAll(this.unfrozenNames.get());
        }
        else if(args.length >= 2
                && args[0].equalsIgnoreCase(AdminCommand.CMD_UNFREEZE))
        {
            // Provide a list of frozen players (cached; see refreshNamesIfStale)
            refreshNamesIfStale();
            results.addAll(this.frozenNames.get());
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
                            if(offlinePlayer.getName() != null)
                            {
                                results.add(offlinePlayer.getName());
                            }
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
