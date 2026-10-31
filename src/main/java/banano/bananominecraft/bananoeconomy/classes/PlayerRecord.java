package banano.bananominecraft.bananoeconomy.classes;

public class PlayerRecord
{
    private final String playerUUID;

    private final String playerName;
    private String wallet;
    private boolean isFrozen;

    public PlayerRecord(String playerUUID, String playerName, String wallet, boolean isFrozen)
    {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.wallet = wallet;
        this.isFrozen = isFrozen;
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
}
