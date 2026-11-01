package banano.bananominecraft.bananoeconomy.classes;

public class PlayerRecord
{
    private final String playerUUID;

    private final String playerName;
    private String wallet;
    private boolean isFrozen;

    /**
     * Derivation index of {@link #wallet} within the player wallet seed, or {@code null} for a legacy
     * record that has not been matched yet (see the startup backfill). Absent from old JSON files,
     * which Gson reads as {@code null}.
     */
    private volatile Integer walletIndex;

    public PlayerRecord(String playerUUID, String playerName, String wallet, boolean isFrozen)
    {
        this(playerUUID, playerName, wallet, isFrozen, null);
    }

    public PlayerRecord(String playerUUID, String playerName, String wallet, boolean isFrozen, Integer walletIndex)
    {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.wallet = wallet;
        this.isFrozen = isFrozen;
        this.walletIndex = walletIndex;
    }

    public String getPlayerUUID()
    {
        return this.playerUUID;
    }

    public String getPlayerName()
    {
        return this.playerName;
    }

    public String getWallet()
    {
        return this.wallet;
    }

    /**
     * Replace the wallet address held by this record. Only changes the in-memory
     * value; use {@code IDBConnector.updatePlayerWallet} to persist it.
     */
    public void setWallet(String wallet)
    {
        this.wallet = wallet;
    }

    public boolean isFrozen()
    {
        return this.isFrozen;
    }

    public void setFrozen(boolean frozen)
    {
        this.isFrozen = frozen;
    }

    /** Seed derivation index of this player's wallet, or {@code null} if not known (legacy record). */
    public Integer getWalletIndex()
    {
        return this.walletIndex;
    }

    /** Only changes the in-memory value; use {@code IDBConnector.setPlayerWalletIndex} to persist it. */
    public void setWalletIndex(Integer walletIndex)
    {
        this.walletIndex = walletIndex;
    }
}
