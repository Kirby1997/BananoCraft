package banano.bananominecraft.bananoeconomy.services;

import banano.bananominecraft.bananoeconomy.io.RPC;

import java.util.List;

/**
 * Framework-free aggregation of the node-status values shown by {@code NodeInfoCommand}.
 *
 * <p>Gathers the block count, node URL, master wallet, and its balance from {@link RPC}
 * into a single {@link NodeInfo} snapshot. The command keeps the rendering (which lines
 * each sender type sees), so this aggregation is unit-testable with a plain mock.</p>
 */
public final class NodeInfoService
{
    private final RPC rpc;

    public NodeInfoService(RPC rpc)
    {
        this.rpc = rpc;
    }

    /**
     * Query the node for its current status.
     *
     * @throws Exception if the node URL cannot be resolved (propagated from {@link RPC#getURL()})
     */
    public NodeInfo gather() throws Exception
    {
        final List<String> blockCount   = rpc.getBlockCount();
        final String        masterWallet = rpc.getMasterWallet();

        return new NodeInfo(
                blockCount.get(0),
                blockCount.get(1),
                rpc.getURL().toString(),
                masterWallet,
                rpc.getBalance(masterWallet));
    }

    /** Immutable snapshot of node status. */
    public record NodeInfo(String checkedBlocks,
                           String uncheckedBlocks,
                           String nodeUrl,
                           String masterWallet,
                           double balance)
    {
    }
}
