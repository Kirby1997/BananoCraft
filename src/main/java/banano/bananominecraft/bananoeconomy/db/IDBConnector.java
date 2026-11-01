package banano.bananominecraft.bananoeconomy.db;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.OfflinePaymentRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface IDBConnector
{
    // -------------------------------------------------------------------------
    // Player records
    // -------------------------------------------------------------------------

    PlayerRecord getPlayerRecord(UUID playerId);
    PlayerRecord getPlayerRecord(Player player);
    PlayerRecord getOfflinePlayerRecord(OfflinePlayer player);
    boolean updatePlayerRecord(PlayerRecord playerRecord);

    /**
     * Persist a new wallet address for an existing player record and update the
     * cached {@link PlayerRecord}. Returns {@code false} if the player has no
     * record or the write failed.
     */
    boolean updatePlayerWallet(Player player, String wallet);

    /**
     * As {@link #updatePlayerWallet(Player, String)}, also storing the seed-derivation index of the
     * new wallet. A {@code null} index leaves the record's index unchanged.
     */
    boolean updatePlayerWallet(Player player, String wallet, Integer walletIndex);

    /** Creates a record for a legacy-style wallet whose derivation index is not known ({@code null}). */
    PlayerRecord createPlayerRecord(Player player, String walletAddress);

    /** Creates a record storing the seed-derivation index of {@code walletAddress}. */
    PlayerRecord createPlayerRecord(Player player, String walletAddress, Integer walletIndex);
    /** @throws IllegalStateException if the database cannot answer (never guesses "not assigned"). */
    boolean isAlreadyAssignedToOtherPlayer(String walletAddress, Player currentPlayer);
    void unloadPlayerRecord(Player player);

    /** @throws IllegalStateException if the database cannot answer (never guesses "no record"). */
    boolean hasPlayerRecord(Player player);
    /** @throws IllegalStateException if the database cannot answer (never guesses "no record"). */
    boolean hasPlayerRecord(OfflinePlayer player);

    // -------------------------------------------------------------------------
    // Wallet derivation indices (walletIndex)
    // -------------------------------------------------------------------------

    /**
     * Highest stored player-wallet index across ALL players (online or not), or {@code 0} if none is
     * stored (so the first allocated index is {@code 1}; 0 is the master wallet).
     *
     * @throws IllegalStateException if the database cannot answer (never guesses, or an index could be re-issued)
     */
    int getMaxPlayerWalletIndex();

    /**
     * Highest stored bank-account index across all banks, or {@code 0} if none is stored.
     *
     * @throws IllegalStateException if the database cannot answer
     */
    int getMaxBankWalletIndex();

    /** Stored player-wallet index of {@code address}, or {@code null} if no player has it / it has none. */
    Integer getPlayerWalletIndex(String address);

    /** Stored bank-account index of {@code address}, or {@code null} if no bank has it / it has none. */
    Integer getBankWalletIndex(String address);

    /** Every player record (online or not) whose walletIndex is still {@code null}. */
    List<PlayerRecord> getPlayerRecordsMissingWalletIndex();

    /** Persists the index of one player's wallet (and updates a cached record). */
    boolean setPlayerWalletIndex(String playerUuid, int walletIndex);

    /** Persists the index of one bank's account (and updates the cached record). */
    boolean setBankWalletIndex(String bankName, int walletIndex);

    // -------------------------------------------------------------------------
    // Offline payments
    // -------------------------------------------------------------------------

    boolean saveOfflinePayment(OfflinePaymentRecord paymentRecord);
    List<OfflinePaymentRecord> getOfflinePaymentRecords(Player forPlayer);
    void deleteOfflinePaymentRecords(Player forPlayer);
    double getOfflinePaymentsTotal(Player forPlayer);

    // -------------------------------------------------------------------------
    // Freeze queries
    // -------------------------------------------------------------------------

    List<PlayerRecord> getFrozenPlayers();
    List<PlayerRecord> getUnfrozenPlayers();

    // -------------------------------------------------------------------------
    // Bank accounts
    // -------------------------------------------------------------------------

    BankRecord getBankRecord(String bankName);
    boolean createBankRecord(BankRecord bank);
    boolean bankNameExists(String bankName);
    boolean deleteBankRecord(String bankName);
    List<String> getAllBankNames();

    boolean isBankOwner(String bankName, String playerUuid);
    boolean isBankMember(String bankName, String playerUuid);
    boolean addBankMember(String bankName, String playerUuid);
    boolean removeBankMember(String bankName, String playerUuid);

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void close();
}
