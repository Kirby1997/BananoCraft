package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import banano.bananominecraft.bananoeconomy.commands.TransactionHistoryCommand;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class TransactionHistoryTabCompleter implements TabCompleter
{
    private final ConfigEngine configEngine;

    public TransactionHistoryTabCompleter(ConfigEngine configEngine)
    {
        this.configEngine = configEngine;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> results = new ArrayList<>();

        if(args.length == 1)
        {
            int maxRecords = this.configEngine.getMaximumTransactionHistoryCount();

            results.add(TransactionHistoryCommand.ARG_ALL);

            if(args[0] == null
                    || args[0].length() == 0)
            {
                results.add("[Last X Records (MIN:" + ConfigEngine.MIN_HISTORY_TRANSACTIONS + ", MAX:" + maxRecords + "]");
            }
        }

        return StringUtil.copyPartialMatches(args[args.length - 1], results, new ArrayList<>());
    }
}