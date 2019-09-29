package banano.bananominecraft.bananoeconomy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

public class VaultConnector implements Economy {
    @Override
    public boolean isEnabled() {
        return Main.getPlugin(Main.class).isEnabled();
    }

    @Override
    public String getName() {
        return "Banano.cc";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return String.valueOf(amount) + "Bans";
    }

    @Override
    public String currencyNamePlural() {
        return "Bans";
    }

    @Override
    public String currencyNameSingular() {
        return "Ban";
    }

    @Override
    public boolean hasAccount(String playerName) {

        return DB.accountExists(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        String playerName = player.getName();
        return DB.accountExists(playerName);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return DB.accountExists(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        String playerName = player.getName();
        return DB.accountExists(playerName);
    }

    @Override
    public double getBalance(String playerName) {
        System.out.println("GET BALANCE???");
        Player player = Bukkit.getServer().getPlayer(playerName);
        Double balance = EconomyFuncs.getBalance(player);
        System.out.println(balance);
        return balance;
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer) {
        System.out.println("OFFLINE PLAYER???");


        if (offlinePlayer.hasPlayedBefore()) {
            String UUID = offlinePlayer.getUniqueId().toString();
            Player player = Bukkit.getPlayer(UUID);
            return EconomyFuncs.getBalance(player);
        }

        return 0;
    }

    @Override
    public double getBalance(String playerName, String world) {
        System.out.println("WOOOOOOORLD");
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer, String world) {

        return getBalance(offlinePlayer);
    }

    @Override
    public boolean has(String playerName, double amount) {
        Player player = Bukkit.getServer().getPlayer(playerName);
        return EconomyFuncs.getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, double amount) {
        if (offlinePlayer.hasPlayedBefore()) {
            String UUID = offlinePlayer.getUniqueId().toString();
            Player player = Bukkit.getPlayer(UUID);
            return EconomyFuncs.getBalance(player) >= amount;
        }
        return false;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {

        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, String worldName, double amount) {
        return has(offlinePlayer, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        System.out.println("WITHDRAWING " + amount + " to " + playerName);
        Player player = Bukkit.getServer().getPlayer(playerName);
        return new EconomyResponse(amount, EconomyFuncs.getBalance(player) - amount,EconomyFuncs.removeBalanceFP(player, amount) ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {

        String playerName = player.getName();
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {

        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        String playerName = player.getName();
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        System.out.println("DEPOSITING " + amount + " to " + playerName);
        String masterWallet = RPC.getMasterWallet();
        System.out.println(masterWallet);
        Player player = Bukkit.getServer().getPlayer(playerName);
        return new EconomyResponse(amount, RPC.getBalance(masterWallet) - amount,EconomyFuncs.addBalanceTP(player, amount) ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        String playerName = player.getName();
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {

        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        String playerName = player.getName();
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return null;
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return null;
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return null;
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return null;
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return null;
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return null;
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return null;
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return null;
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public List<String> getBanks() {
        return null;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {

        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return false;
    }
}
