package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class RepresentativeTabCompleter implements TabCompleter
{
    private static final String ARG_SET            = "set";
    private static final String ARG_GET            = "get";
    private static final String ARG_RANDOM         = "random";
    private static final String BAN_ADDRESS_PREFIX = "ban_";

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> results = new ArrayList<>();

        if (args.length == 1)
        {
            results.add(ARG_SET);
            results.add(ARG_GET);
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase(ARG_SET))
        {
            results.add(ARG_RANDOM);
            results.add(BAN_ADDRESS_PREFIX);
        }

        return StringUtil.copyPartialMatches(args[args.length - 1], results, new ArrayList<>());
    }
}
