package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;

import java.math.BigInteger;

/**
 * The wallet-control actions that sign blocks: everything {@code RPC} used to do with a node-held
 * wallet (seed import, existence check, account creation, send, receive, representative change).
 * Read-only queries (balance, history, receivable, ...) are not part of this interface.
 *
 * <p>{@link NodeWalletSigner} asks a node that holds the seed to sign ({@code walletMode: node});
 * {@link LightWalletSigner} derives keys and signs locally ({@code walletMode: light}), so the seed
 * never leaves the server.</p>
 *
 * <p>Wallets are addressed by the same {@code walletId} strings the callers already pass around
 * (taken from {@code ConfigEngine}): a node wallet ID in node mode, or one of the
 * {@link #LIGHT_PLAYER_WALLET_ID} / {@link #LIGHT_BANK_WALLET_ID} markers in light mode.</p>
 */
public interface WalletSigner
{
    /** Wallet ID {@code ConfigEngine} reports for the player seed in light mode (no node wallet exists). */
    String LIGHT_PLAYER_WALLET_ID = "light:player";

    /** Wallet ID {@code ConfigEngine} reports for the bank seed in light mode (no node wallet exists). */
    String LIGHT_BANK_WALLET_ID = "light:bank";

    /** Which of the two configured seeds an action concerns. */
    enum Wallet
    {
        PLAYER,
        BANK
    }

    /**
     * Result of asking whether a wallet exists. {@link #MISSING} is only reported when the node
     * explicitly says the wallet is unknown; anything else that goes wrong is {@link #UNREACHABLE}.
     */
    enum Status
    {
        EXISTS,
        MISSING,
        UNREACHABLE
    }

    /**
     * {@code true} when signing happens locally and there are no node wallet IDs to create or
     * persist (light mode).
     */
    boolean isLocal();

    /**
     * Imports the configured seed. Node: {@code wallet_create}, returning the node's wallet ID.
     * Light: a local no-op that succeeds and returns the light wallet marker ID; it never sends
     * anything over the network.
     *
     * @return the wallet ID, or {@code null} (after logging why) on failure
     */
    String createWallet(Wallet wallet);

    /** Whether the wallet exists. Light mode: {@link Status#EXISTS} whenever its seed is usable. */
    Status walletStatus(Wallet wallet);

    /**
     * Derives (node: {@code account_create}) the account at {@code index} of the wallet.
     *
     * @param index explicit derivation index, or {@code -1} to let the node pick the next free one
     *              (node mode only)
     * @return the account address, or {@code null} (after logging why) on failure
     */
    String accountCreate(String walletId, int index);

    /**
     * Sends {@code amountRaw} from {@code sender} (an account of {@code walletId}) to {@code recipient}.
     * Inputs have already been validated by the caller.
     *
     * @param sendId idempotency id for this logical send (node mode: the node's {@code "id"} field)
     * @return the send block hash
     */
    String send(String walletId, String sender, String recipient, BigInteger amountRaw, String sendId)
            throws TransactionError;

    /** Pockets the pending {@code blockHash} into {@code account}; returns the receive block hash. */
    String receive(String walletId, String account, String blockHash) throws TransactionError;

    /** Changes the representative of {@code account}; returns the change block hash. */
    String setRepresentative(String walletId, String account, String representative) throws TransactionError;
}
