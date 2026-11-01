package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.wallet.light.LightWallet;
import banano.bananominecraft.bananoeconomy.wallet.light.LightWalletException;

import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link WalletSigner} that signs locally ({@code walletMode: light}): keys are derived from the
 * seed in this process and blocks are built, signed, worked and published by a {@link LightWallet}.
 * The seed is never sent anywhere, and nothing here talks to a node wallet.
 *
 * <p>There is one {@link LightWallet} per seed (player seed, bank seed). A wallet is {@code null}
 * when its seed is unavailable; every action on it then fails with a clear error instead of
 * guessing. The derivation index of an address is looked up through {@link IndexLookup} (the stored
 * {@code walletIndex}); the master wallet is index 0 of the player seed. A found index is always
 * verified by re-deriving the address, so a wrong stored index can never sign for another account.</p>
 */
public class LightWalletSigner implements WalletSigner
{
    /** Looks up the stored seed-derivation index of an address; {@code null} when unknown. */
    public interface IndexLookup
    {
        /** Index within the player seed of a player's wallet address, or {@code null}. */
        Integer playerIndex(String address);

        /** Index within the bank seed of a bank's address, or {@code null}. */
        Integer bankIndex(String address);
    }

    static final String UNAVAILABLE_MESSAGE =
            "Light wallet unavailable: the wallet seed could not be loaded (see the server log).";
    static final String UNMATCHED_MESSAGE =
            "This wallet's key index is not known (the startup scan has not matched this legacy account yet,"
            + " or could not), so it cannot sign. Try again shortly or ask a server admin - see the server log.";

    private final LightWallet player;
    private final LightWallet bank;
    private final IndexLookup lookup;
    private final Logger log;

    /**
     * @param player the wallet for the player seed, or {@code null} if that seed is unavailable
     * @param bank   the wallet for the bank seed, or {@code null} if banks are off or the seed is unavailable
     */
    public LightWalletSigner(LightWallet player, LightWallet bank, IndexLookup lookup, Logger log)
    {
        this.player = player;
        this.bank = bank;
        this.lookup = lookup;
        this.log = log;
    }

    @Override
    public boolean isLocal()
    {
        return true;
    }

    private LightWallet walletFor(Wallet wallet)
    {
        return wallet == Wallet.BANK ? bank : player;
    }

    private static Wallet kindFor(String walletId) throws TransactionError
    {
        if (LIGHT_PLAYER_WALLET_ID.equals(walletId))
        {
            return Wallet.PLAYER;
        }
        if (LIGHT_BANK_WALLET_ID.equals(walletId))
        {
            return Wallet.BANK;
        }
        throw new TransactionError("Unknown light wallet '" + walletId + "'.");
    }

    // -------------------------------------------------------------------------
    // Wallet lifecycle (local only — never touches the network)
    // -------------------------------------------------------------------------

    @Override
    public String createWallet(Wallet wallet)
    {
        if (walletFor(wallet) == null)
        {
            log.severe("Light wallet (" + wallet + ") unavailable: its seed could not be loaded.");
            return null;
        }
        return wallet == Wallet.BANK ? LIGHT_BANK_WALLET_ID : LIGHT_PLAYER_WALLET_ID;
    }

    @Override
    public Status walletStatus(Wallet wallet)
    {
        return walletFor(wallet) != null ? Status.EXISTS : Status.UNREACHABLE;
    }

    @Override
    public String accountCreate(String walletId, int index)
    {
        try
        {
            LightWallet wallet = walletFor(kindFor(walletId));
            if (wallet == null)
            {
                log.severe(UNAVAILABLE_MESSAGE);
                return null;
            }
            if (index < 0)
            {
                log.severe("Light wallet needs an explicit account index (got " + index + ").");
                return null;
            }
            return wallet.address(index);
        }
        catch (TransactionError | RuntimeException e)
        {
            log.log(Level.SEVERE, "Account derivation failed.", e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Signing actions
    // -------------------------------------------------------------------------

    @Override
    public String send(String walletId, String sender, String recipient, BigInteger amountRaw, String sendId)
            throws TransactionError
    {
        final Wallet kind = kindFor(walletId);
        final LightWallet wallet = requireWallet(kind);
        final int index = resolveIndex(kind, wallet, sender);

        try
        {
            return wallet.send(index, recipient, amountRaw);
        }
        catch (LightWalletException e)
        {
            throw toTransactionError("send from " + sender + " to " + recipient + " amount " + amountRaw + " raw", e);
        }
    }

    @Override
    public String receive(String walletId, String account, String blockHash) throws TransactionError
    {
        final Wallet kind = kindFor(walletId);
        final LightWallet wallet = requireWallet(kind);
        final int index = resolveIndex(kind, wallet, account);

        try
        {
            return wallet.receive(index, blockHash);
        }
        catch (LightWalletException e)
        {
            throw toTransactionError("receive " + blockHash + " into " + account, e);
        }
    }

    @Override
    public String setRepresentative(String walletId, String account, String representative) throws TransactionError
    {
        final Wallet kind = kindFor(walletId);
        final LightWallet wallet = requireWallet(kind);
        final int index = resolveIndex(kind, wallet, account);

        try
        {
            return wallet.changeRepresentative(index, representative);
        }
        catch (LightWalletException e)
        {
            throw toTransactionError("representative change of " + account, e);
        }
    }

    private LightWallet requireWallet(Wallet kind) throws TransactionError
    {
        LightWallet wallet = walletFor(kind);
        if (wallet == null)
        {
            log.severe(UNAVAILABLE_MESSAGE);
            throw new TransactionError(UNAVAILABLE_MESSAGE);
        }
        return wallet;
    }

    /**
     * Finds the derivation index of {@code address}: index 0 for the master wallet, else the stored
     * walletIndex. The result is verified by re-deriving the address.
     *
     * @throws TransactionError if the index is unknown (legacy record never matched) or inconsistent
     */
    int resolveIndex(Wallet kind, LightWallet wallet, String address) throws TransactionError
    {
        Integer index = null;

        if (kind == Wallet.PLAYER && address != null && address.equals(wallet.address(0)))
        {
            index = 0;
        }
        else if (address != null)
        {
            index = kind == Wallet.BANK ? lookup.bankIndex(address) : lookup.playerIndex(address);
        }

        if (index == null)
        {
            log.severe("No wallet index is stored for " + address + " - it was not matched by the startup"
                    + " backfill (raise walletIndexScanLimit in config.yml and restart). Refusing to sign.");
            throw new TransactionError(UNMATCHED_MESSAGE);
        }

        if (index < 0 || !address.equals(wallet.address(index)))
        {
            log.severe("Stored wallet index " + index + " does not derive " + address + " - refusing to sign.");
            throw new TransactionError("Stored wallet index does not match this address, so it cannot sign."
                    + " Ask a server admin - see the server log.");
        }

        return index;
    }

    /**
     * Maps a {@link LightWalletException} onto the user-facing {@link TransactionError} behaviour.
     * {@code UNKNOWN_OUTCOME} becomes an {@link UnknownOutcomeError}, never a plain failure.
     */
    TransactionError toTransactionError(String what, LightWalletException e)
    {
        switch (e.kind())
        {
            case UNKNOWN_OUTCOME:
                log.log(Level.SEVERE, "Outcome UNKNOWN for " + what
                        + " - the block may or may not have been accepted. Check the account history.", e);
                return new UnknownOutcomeError();
            case INSUFFICIENT_BALANCE:
                return new TransactionError("Insufficient balance");
            case UNAVAILABLE:
                log.log(Level.WARNING, "Network unavailable for " + what + ".", e);
                return new TransactionError("Network unavailable, please try again later.");
            case REJECTED:
                log.log(Level.WARNING, "Block rejected for " + what + ".", e);
                return new TransactionError("Transaction rejected: " + e.getMessage());
            case INVALID:
            default:
                log.log(Level.WARNING, "Invalid request for " + what + ".", e);
                return new TransactionError("Invalid transaction: " + e.getMessage());
        }
    }
}
