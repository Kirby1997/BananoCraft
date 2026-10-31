package banano.bananominecraft.bananoeconomy.api;

import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;

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
        PlayerRecord record = db.getPlayerRecord(playerId);
        return record != null ? record.getWallet() : null;
    }

    @Override
    public double getBalance(UUID playerId)
    {
        String address = getAddress(playerId);

        if (address == null)
        {
            return 0.0;
        }

        return rpc.getBalance(address);
    }

    @Override
    public SendResult send(UUID playerId, String destinationAddress, double amountBan)
    {
        PlayerRecord record = db.getPlayerRecord(playerId);

        if (record == null || record.getWallet() == null)
        {
            return SendResult.failed(NO_WALLET_ERROR);
        }

        if (record.isFrozen())
        {
            return SendResult.failed(FROZEN_ERROR);
        }

        try
        {
            String blockHash = rpc.sendTransaction(record.getWallet(), destinationAddress, amountBan);
            return SendResult.succeeded(blockHash);
        }
        catch (TransactionError error)
        {
            return SendResult.failed(error.getUserError());
        }
    }

    @Override
    public RepresentativeResult setRepresentative(UUID playerId, String representativeAddress)
    {
        PlayerRecord record = db.getPlayerRecord(playerId);

        if (record == null || record.getWallet() == null)
        {
            return RepresentativeResult.failed(NO_WALLET_ERROR);
        }

        if (record.isFrozen())
        {
            return RepresentativeResult.failed(FROZEN_ERROR);
        }

        return toApiResult(representativeService.setRepresentative(record.getWallet(), representativeAddress));
    }

    @Override
    public RepresentativeResult resetToDefaultRepresentative(UUID playerId)
    {
        PlayerRecord record = db.getPlayerRecord(playerId);

        if (record == null || record.getWallet() == null)
        {
            return RepresentativeResult.failed(NO_WALLET_ERROR);
        }

        if (record.isFrozen())
        {
            return RepresentativeResult.failed(FROZEN_ERROR);
        }

        return toApiResult(representativeService.setRandomRepresentative(record.getWallet()));
    }

    private static RepresentativeResult toApiResult(RepresentativeService.RepresentativeResult result)
    {
        return result.success()
                ? RepresentativeResult.succeeded()
                : RepresentativeResult.failed(result.userError());
    }
}
