package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class DepositTabCompleter implements TabCompleter
{
    private static final String ARG_SERVER = "server";

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> results = new ArrayList<>();

        results.add(ARG_SERVER);

        return StringUtil.copyPartialMatches(args[args.length - 1], results, new ArrayList<>());
    }
}