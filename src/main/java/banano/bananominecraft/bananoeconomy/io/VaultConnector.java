package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.TransferOutcome;
import banano.bananominecraft.bananoeconomy.services.BankService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;

public class VaultConnector implements Economy
{
    /**
     * Message on a SUCCESS response whose transfer outcome is unknown (light mode: published, answer
     * lost). Reporting a failure would make a shop retry or refund and could move the funds twice.
     */
    static final String UNKNOWN_OUTCOME_MESSAGE = TransferOutcome.UNKNOWN_NOTE;

    private static final EconomyResponse NOT_ONLINE =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player is not online.");

    private static final EconomyResponse NO_WALLET =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player has no wallet.");

    private static final EconomyResponse BANKS_DISABLED =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank accounts are not enabled.");

    private static final EconomyResponse BANK_NOT_FOUND =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank not found.");

    private static final EconomyResponse BANK_DELETION_UNSUPPORTED =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Bank deletion is not supported.");

    private static final EconomyResponse INVALID_AMOUNT =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Amount must be a positive, finite number.");

    private static final EconomyResponse INVALID_PLAYER_AMOUNT =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE,
                    "Amount must be a finite, non-negative number.");

    private static final EconomyResponse ACCOUNT_FROZEN =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account is frozen.");

    private final Plugin       plugin;
    private final EconomyFuncs economyFuncs;
    private final RPC          rpc;
    private final BankService  bankService;

    public VaultConnector(Plugin plugin, EconomyFuncs economyFuncs, RPC rpc, BankService bankService)
    {
        this.plugin       = plugin;
        this.economyFuncs = economyFuncs;
        this.rpc          = rpc;
        this.bankService  = bankService;

        // Bank transfers move funds in and out of the master wallet too, so they must serialize
        // on the same lock as the Vault player deposit/withdraw path.
        this.bankService.shareMasterWalletLock(economyFuncs.getMasterWalletLock());
    }

    @Override
    public boolean isEnabled()
    {
        return plugin.isEnabled();
    }

    @Override
    public String getName()
    {
        return "Banano.cc";
    }

    @Override
    public boolean hasBankSupport()
    {
        return bankService.isBankingEnabled();
    }

    @Override
    public int fractionalDigits()
    {
        return 2;
    }

    @Override
    public String format(double amount)
    {
        return String.format("%.2f Bans", amount);
    }

    @Override
    public String currencyNamePlural()
    {
        return "Bans";
    }

    @Override
    public String currencyNameSingular()
    {
        return "Ban";
    }

    // -------------------------------------------------------------------------
    // hasAccount
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public boolean hasAccount(String playerName)
    {
        Player player = Bukkit.getServer().getPlayerExact(playerName);
        return player != null && economyFuncs.hasWallet(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player)
    {
        return economyFuncs.hasWallet(player);
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName)
    {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName)
    {
        return hasAccount(player);
    }

    // -------------------------------------------------------------------------
    // getBalance
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public double getBalance(String playerName)
    {
        Player player = Bukkit.getServer().getPlayerExact(playerName);
        return player != null ? economyFuncs.getBalance(player) : 0;
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer)
    {
        return economyFuncs.hasWallet(offlinePlayer) ? economyFuncs.getBalance(offlinePlayer) : 0;
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world)
    {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer offlinePlayer, String world)
    {
        return getBalance(offlinePlayer);
    }

    // -------------------------------------------------------------------------
    // has
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public boolean has(String playerName, double amount)
    {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, double amount)
    {
        return economyFuncs.hasWallet(offlinePlayer) && economyFuncs.getBalance(offlinePlayer) >= amount;
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount)
    {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, String worldName, double amount)
    {
        return has(offlinePlayer, amount);
    }

    // -------------------------------------------------------------------------
    // withdrawPlayer
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount)
    {
        Player player = Bukkit.getServer().getPlayerExact(playerName);
        if (player == null)
        {
            return NOT_ONLINE;
        }
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer offlinePlayer, double amount)
    {
        if (!Double.isFinite(amount) || amount < 0)
        {
            return INVALID_PLAYER_AMOUNT;
        }

        if (!economyFuncs.hasWallet(offlinePlayer))
        {
            return NO_WALLET;
        }

        if (economyFuncs.isFrozen(offlinePlayer))
        {
            return ACCOUNT_FROZEN;
        }

        // Fetch once up front: gives an accurate "insufficient funds" message without attempting
        // the send, and lets the post-withdraw balance be computed rather than re-fetched.
        double oldBalance = economyFuncs.getBalance(offlinePlayer);

        if (oldBalance < amount)
        {
            return new EconomyResponse(amount, oldBalance, EconomyResponse.ResponseType.FAILURE,
                    "Insufficient funds.");
        }

        TransferOutcome outcome = economyFuncs.removeBalanceFPOutcome(offlinePlayer, amount);
        boolean success = outcome != null && outcome.isSuccess();
        double newBalance = success ? oldBalance - amount : oldBalance;
        return new EconomyResponse(amount, newBalance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                outcome == TransferOutcome.UNKNOWN ? UNKNOWN_OUTCOME_MESSAGE
                        : success ? null : "Withdrawal failed.");
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount)
    {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return withdrawPlayer(player, amount);
    }

    // -------------------------------------------------------------------------
    // depositPlayer
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount)
    {
        Player player = Bukkit.getServer().getPlayerExact(playerName);

        if (player == null)
        {
            return NOT_ONLINE;
        }

        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer offlinePlayer, double amount)
    {
        if (!Double.isFinite(amount) || amount < 0)
        {
            return INVALID_PLAYER_AMOUNT;
        }

        if (!economyFuncs.hasWallet(offlinePlayer))
        {
            return NO_WALLET;
        }

        if (economyFuncs.isFrozen(offlinePlayer))
        {
            return ACCOUNT_FROZEN;
        }

        TransferOutcome outcome = economyFuncs.addBalanceTPOutcome(offlinePlayer, amount);
        boolean success = outcome != null && outcome.isSuccess();
        double newBalance = economyFuncs.getBalance(offlinePlayer);
        return new EconomyResponse(amount, newBalance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                outcome == TransferOutcome.UNKNOWN ? UNKNOWN_OUTCOME_MESSAGE
                        : success ? null
                        : "Deposit failed (server wallet has insufficient funds or the node is unavailable).");
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount)
    {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return depositPlayer(player, amount);
    }

    // -------------------------------------------------------------------------
    // Bank operations
    // -------------------------------------------------------------------------

    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String playerName)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        Player player = Bukkit.getServer().getPlayerExact(playerName);
        if (player == null)
        {
            return NOT_ONLINE;
        }

        return createBankForUuid(name, player.getUniqueId().toString());
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        return createBankForUuid(name, player.getUniqueId().toString());
    }

    private EconomyResponse createBankForUuid(String name, String ownerUuid)
    {
        BankRecord bank = bankService.createBank(name, ownerUuid);

        if (bank == null)
        {
            if (bankService.isWalletSetupPending())
            {
                return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE,
                        "Wallet setup is still in progress after the server start - try again in a minute.");
            }

            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE,
                    "Bank '" + name + "' already exists or could not be created.");
        }

        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse deleteBank(String name)
    {
        return BANK_DELETION_UNSUPPORTED;
    }

    @Override
    public EconomyResponse bankBalance(String name)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        if (!bankService.bankExists(name))
        {
            return BANK_NOT_FOUND;
        }

        double balance = bankService.getBankBalance(name);
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankHas(String name, double amount)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        if (!bankService.bankExists(name))
        {
            return BANK_NOT_FOUND;
        }

        if (!Double.isFinite(amount) || amount < 0)
        {
            return INVALID_AMOUNT;
        }

        double balance = bankService.getBankBalance(name);
        boolean has = balance >= amount;
        return new EconomyResponse(amount, balance,
                has ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                has ? null : "Insufficient bank funds.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        if (!Double.isFinite(amount) || amount <= 0)
        {
            return INVALID_AMOUNT;
        }

        if (!bankService.bankExists(name))
        {
            return BANK_NOT_FOUND;
        }

        double balance = bankService.getBankBalance(name);

        if (balance < amount)
        {
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.FAILURE,
                    "Insufficient bank funds.");
        }

        TransferOutcome outcome = bankService.withdrawOutcome(name, amount);
        boolean success = outcome != null && outcome.isSuccess();
        double newBalance = bankService.getBankBalance(name);
        return new EconomyResponse(amount, newBalance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                outcome == TransferOutcome.UNKNOWN ? UNKNOWN_OUTCOME_MESSAGE
                        : success ? null : "Bank withdrawal failed.");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        if (!Double.isFinite(amount) || amount <= 0)
        {
            return INVALID_AMOUNT;
        }

        if (!bankService.bankExists(name))
        {
            return BANK_NOT_FOUND;
        }

        TransferOutcome outcome = bankService.depositOutcome(name, amount);
        boolean success = outcome != null && outcome.isSuccess();
        double newBalance = bankService.getBankBalance(name);
        return new EconomyResponse(amount, newBalance,
                success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                outcome == TransferOutcome.UNKNOWN ? UNKNOWN_OUTCOME_MESSAGE
                        : success ? null : "Bank deposit failed.");
    }

    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        Player player = Bukkit.getServer().getPlayerExact(playerName);

        if (player == null)
        {
            return NOT_ONLINE;
        }

        boolean isOwner = bankService.isBankOwner(name, player.getUniqueId().toString());
        return new EconomyResponse(0, 0,
                isOwner ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        boolean isOwner = bankService.isBankOwner(name, player.getUniqueId().toString());
        return new EconomyResponse(0, 0,
                isOwner ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, null);
    }

    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        Player player = Bukkit.getServer().getPlayerExact(playerName);

        if (player == null)
        {
            return NOT_ONLINE;
        }

        boolean isMember = bankService.isBankMember(name, player.getUniqueId().toString());
        return new EconomyResponse(0, 0,
                isMember ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, null);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player)
    {
        if (!bankService.isBankingEnabled())
        {
            return BANKS_DISABLED;
        }

        boolean isMember = bankService.isBankMember(name, player.getUniqueId().toString());
        return new EconomyResponse(0, 0,
                isMember ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, null);
    }

    @Override
    public List<String> getBanks()
    {
        if (!bankService.isBankingEnabled())
        {
            return Collections.emptyList();
        }

        return bankService.getBanks();
    }

    // -------------------------------------------------------------------------
    // createPlayerAccount (account creation is handled on join, not via Vault)
    // -------------------------------------------------------------------------

    @Override public boolean createPlayerAccount(String playerName)                          { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player)                       { return false; }
    @Override public boolean createPlayerAccount(String playerName, String worldName)        { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName)     { return false; }
}
