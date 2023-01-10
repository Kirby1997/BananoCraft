package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class WithdrawTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> results = new ArrayList<>();

        if(args.length == 1) {

            results.add("all");

            if(args[0] == null
                    || args[0].length() == 0) {

                results.add("[amount]");

            }

        }
        else if(args.length == 2
                && (args[0] == null
                || args[0].length() == 0)) {

            results.add("ban_");

        }

        return StringUtil.copyPartialMatches(args[args.length - 1], results, new ArrayList<>());
    }
}