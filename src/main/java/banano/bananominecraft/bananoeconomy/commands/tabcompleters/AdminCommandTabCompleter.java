package banano.bananominecraft.bananoeconomy.commands.tabcompleters;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
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

public class AdminCommandTabCompleter implements TabCompleter {

    private final IDBConnector db;
    private final ConfigEngine configEngine;

    public AdminCommandTabCompleter(ConfigEngine configEngine, IDBConnector db) {
        this.configEngine = configEngine;
        this.db = db;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> results = new ArrayList<>();

        if(args.length == 1) {

            results.add("setnode");
            results.add("freeze");
            results.add("unfreeze");
            results.add("explorer");
            results.add("serverwallet");
            results.add("offlinetransactions");

        }
        else if(args.length >= 2
                  && args[0].equalsIgnoreCase("freeze")) {

            // Provide a list of unfrozen players
            List<PlayerRecord> frozenPlayers = this.db.getUnfrozenPlayers();

            for (PlayerRecord record : frozenPlayers) {

                results.add(record.getPlayerName());

            }

        }
        else if(args.length >= 2
                && args[0].equalsIgnoreCase("unfreeze")) {

            // Provide a list of frozen players
            List<PlayerRecord> frozenPlayers = this.db.getFrozenPlayers();

            for (PlayerRecord record : frozenPlayers) {

                results.add(record.getPlayerName());

            }

        }
        else if(args.length == 2) {

            if(args[0].equalsIgnoreCase("explorer")) {

                results.add("account");
                results.add("block");

            }
            else if(args[0].equalsIgnoreCase("serverwallet")) {

                results.add("tip");
                results.add("deposit");
                results.add("withdraw");
                results.add("balance");

            }
            else if(args[0].equalsIgnoreCase("setnode")) {

                if(args[1] == null
                     || args[1].length() == 0) {

                    results.add("[http://[url/ip address]:[port]/]");
                    results.add("[https://[url/ip address]:[port]/]");

                }

            }
            else if(args[0].equalsIgnoreCase("offlinetransactions")) {

                results.add("enable");
                results.add("disable");

            }

        }
        else if(args.length == 3) {

            if(args[0].equalsIgnoreCase("explorer")) {

                results.add("set");
                results.add("view");

            }
            else if(args[0].equalsIgnoreCase("serverwallet")) {

                if(args[1].equalsIgnoreCase("tip")
                     || args[1].equalsIgnoreCase("withdraw")) {

                    results.add("all");

                    // Amount
                    if(args[2] == null
                            || args[2].length() == 0) {

                        results.add("[amount]");

                    }

                }

            }

        }
        else if(args.length == 4) {

            if(args[0].equalsIgnoreCase("explorer")
                && args[2].equalsIgnoreCase("set")) {

                if(args[3] == null
                        || args[3].length() == 0) {

                    results.add("[http://[url/ip address]:[port]/]");
                    results.add("[https://[url/ip address]:[port]/]");

                }

            }
            else if(args[0].equalsIgnoreCase("serverwallet")) {

                if(args[1].equalsIgnoreCase("tip")) {

                    // List of players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        results.add(player.getName());
                    }

                    if(this.configEngine.getEnableOfflinePayment()) {

                        for(OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                            results.add(offlinePlayer.getName());
                        }

                    }

                }
                else if(args[1].equalsIgnoreCase("withdraw")) {

                    if(args[3] == null
                            || args[3].length() == 0) {

                        results.add("ban_");

                    }

                }

            }

        }


        return StringUtil.copyPartialMatches(args[args.length - 1], results, new ArrayList<>());

    }
}