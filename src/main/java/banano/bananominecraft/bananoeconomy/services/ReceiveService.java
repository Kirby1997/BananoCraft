package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.io.RPC;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-free core of the "receive pending" flow: pocket every not-yet-received deposit
 * waiting for an account. Shared by {@code ReceiveCommand} (player wallet) and any future
 * admin tooling for the master/bank wallets.
 *
 * <p>In node mode the node normally auto-pockets deposits, after which {@code BananoWebSocket}
 * notifies the player. In light mode there is no node auto-receive: the plugin pockets what is
 * pending when a player joins and right after its own payments. This service is the manual
 * backstop for deposits still sitting as pending/receivable blocks (e.g. received while the
 * node's auto-receive was disabled/lagging, while the player was offline, or, in light mode,
 * sent from outside while the player stayed online).</p>
 *
 * <p>Knows nothing about Bukkit, scheduling, or messaging — so it is unit-testable with plain
 * mocks. The command layer keeps the async boundary and player messaging.</p>
 */
public final class ReceiveService
{
    private final RPC rpc;
    private final ConfigEngine configEngine;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ReceiveService(RPC rpc, ConfigEngine configEngine)
    {
        this.rpc = rpc;
        this.configEngine = configEngine;
    }

    /**
     * Pockets every pending block for {@code account}, signing with the main player wallet.
     */
    public ReceiveResult receiveAllPending(String account)
    {
        return receiveAllPending(account, configEngine.getWalletId());
    }

    /**
     * Pockets every pending block for {@code account}.
     *
     * <p>Stops at the first failure and reports it, since a mid-loop failure most likely means
     * the wallet/account itself is now unusable rather than that specific block being bad —
     * whatever was already received before the failure is still reported as received.</p>
     */
    public ReceiveResult receiveAllPending(String account, String walletId)
    {
        // One run per account at a time: repeated /receive must not start parallel walks of the
        // same pending list (each block is a node receive with proof-of-work).
        if (!inFlight.add(account))
        {
            return ReceiveResult.partiallyFailed(List.of(), "A receive is already in progress for this account.");
        }

        try
        {
            return receivePending(account, walletId);
        }
        finally
        {
            inFlight.remove(account);
        }
    }

    private ReceiveResult receivePending(String account, String walletId)
    {
        List<String> pendingBlocks = rpc.receivablePending(account);

        if (pendingBlocks.isEmpty())
        {
            return ReceiveResult.succeeded(List.of());
        }

        List<String> receivedBlocks = new ArrayList<>();

        for (String pendingBlock : pendingBlocks)
        {
            try
            {
                receivedBlocks.add(rpc.receiveBlock(walletId, account, pendingBlock));
            }
            catch (TransactionError error)
            {
                return ReceiveResult.partiallyFailed(receivedBlocks, error.getUserError());
            }
        }

        return ReceiveResult.succeeded(receivedBlocks);
    }

    /** Outcome of {@link #receiveAllPending}. */
    public record ReceiveResult(boolean success, List<String> receivedBlocks, String userError)
    {
        public static ReceiveResult succeeded(List<String> receivedBlocks)
        {
            return new ReceiveResult(true, receivedBlocks, null);
        }

        public static ReceiveResult partiallyFailed(List<String> receivedBlocks, String userError)
        {
            return new ReceiveResult(false, receivedBlocks, userError);
        }
    }
}
