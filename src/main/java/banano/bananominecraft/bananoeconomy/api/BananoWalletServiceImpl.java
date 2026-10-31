package banano.bananominecraft.bananoeconomy.api;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;
import banano.bananominecraft.bananoeconomy.validation.Validator;

import java.util.UUID;

/**
 * Default {@link BananoWalletService} implementation, backed directly by the plugin's own
 * {@link IDBConnector} and {@link RPC} rather than the {@code Player}-only {@code EconomyFuncs}
 * helpers — callers of this API shouldn't need an online {@code Player} object just to look up
 * a wallet address or balance.
 */
public final class BananoWalletServiceImpl implements BananoWalletService
{
    private static final String NO_WALLET_ERROR = "No BananoCraft wallet found for this player.";
    private static final String FROZEN_ERROR = "This player's BananoCraft account is frozen.";
    private static final String INVALID_AMOUNT_ERROR = "Amount must be a positive number.";
    private static final String INTERNAL_ERROR = "BananoCraft could not complete the request.";

    private final IDBConnector db;
    private final RPC rpc;
    private final RepresentativeService representativeService;

    public BananoWalletServiceImpl(IDBConnector db, RPC rpc, RepresentativeService representativeService)
    {
        this.db = db;
        this.rpc = rpc;
        this.representativeService = representativeService;
    }

    @Override
    public String getAddress(UUID playerId)
    {
        return walletOf(lookup(playerId));
    }

    /** The player's record, or {@code null} if there is none (or {@code playerId} is null). */
    private PlayerRecord lookup(UUID playerId)
    {
        return playerId != null ? db.getPlayerRecord(playerId) : null;
    }

    /**
     * The record's wallet if it is a real Banano address, else {@code null} — e.g. a record
     * that stored {@link RPC#ACCOUNT_CREATION_FAILED} after a failed account creation.
     */
    private static String walletOf(PlayerRecord record)
    {
        if (record == null)
        {
            return null;
        }

        String wallet = record.getWallet();
        return Validator.validateAddress(wallet) ? wallet : null;
    }

    @Override
    public double getBalance(UUID playerId)
    {
        String address = getAddress(playerId);

        if (address == null)
        {
            return 0.0;
        }

        Double balance = rpc.getBalance(address);
        return balance != null ? balance : 0.0;
    }

    @Override
    public SendResult send(UUID playerId, String destinationAddress, double amountBan)
    {
        if (!Double.isFinite(amountBan) || amountBan <= 0)
        {
            return SendResult.failed(INVALID_AMOUNT_ERROR);
        }

        PlayerRecord record = lookup(playerId);
        String wallet = walletOf(record);

        if (wallet == null)
        {
            return SendResult.failed(NO_WALLET_ERROR);
        }

        if (record.isFrozen())
        {
            return SendResult.failed(FROZEN_ERROR);
        }

        try
        {
            String blockHash = rpc.sendTransaction(wallet, destinationAddress, amountBan);
            return SendResult.succeeded(blockHash);
        }
        catch (TransactionError error)
        {
            return SendResult.failed(error.getUserError());
        }
        catch (RuntimeException error)
        {
            // Never leak internal failures into a calling plugin.
            return SendResult.failed(INTERNAL_ERROR);
        }
    }

    @Override
    public RepresentativeResult setRepresentative(UUID playerId, String representativeAddress)
    {
        PlayerRecord record = lookup(playerId);
        String wallet = walletOf(record);

        if (wallet == null)
        {
            return RepresentativeResult.failed(NO_WALLET_ERROR);
        }

        if (record.isFrozen())
        {
            return RepresentativeResult.failed(FROZEN_ERROR);
        }

        try
        {
            return toApiResult(representativeService.setRepresentative(wallet, representativeAddress));
        }
        catch (RuntimeException error)
        {
            return RepresentativeResult.failed(INTERNAL_ERROR);
        }
    }

    @Override
    public RepresentativeResult resetToDefaultRepresentative(UUID playerId)
    {
        PlayerRecord record = lookup(playerId);
        String wallet = walletOf(record);

        if (wallet == null)
        {
            return RepresentativeResult.failed(NO_WALLET_ERROR);
        }

        if (record.isFrozen())
        {
            return RepresentativeResult.failed(FROZEN_ERROR);
        }

        try
        {
            return toApiResult(representativeService.setRandomRepresentative(wallet));
        }
        catch (RuntimeException error)
        {
            return RepresentativeResult.failed(INTERNAL_ERROR);
        }
    }

    private static RepresentativeResult toApiResult(RepresentativeService.RepresentativeResult result)
    {
        return result.success()
                ? RepresentativeResult.succeeded()
                : RepresentativeResult.failed(result.userError());
    }
}
