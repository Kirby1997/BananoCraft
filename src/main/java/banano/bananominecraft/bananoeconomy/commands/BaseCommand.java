package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class BaseCommand {

    protected void SendMessage(Player player, String message, ChatColor messageColour) {
        if(player != null) {
            player.sendMessage(messageColour + message);
        }
        else {
            System.out.println(message);
        }
    }

    protected PlayerRecord findPlayer(IDBConnector db, ConfigEngine configEngine, String targetPlayerName) {

        Player target = Bukkit.getPlayerExact(targetPlayerName);

        if(target != null) {

            return db.getPlayerRecord(target);

        }
        else if(configEngine.getEnableOfflinePayment()) {

            List<OfflinePlayer> matchingPlayers = Arrays.stream(Bukkit.getOfflinePlayers()).filter(x -> x.getName().equalsIgnoreCase(targetPlayerName)).toList();

            if(matchingPlayers.size() == 1) {

                return db.getOfflinePlayerRecord(matchingPlayers.stream().findFirst().get());

            }

        }

        return null;

    }

}
