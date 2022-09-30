package banano.bananominecraft.bananoeconomy.classes;

public class PlayerRecord {

    private final String playerUUID;

    private final String playerName;
    private final String wallet;
    private boolean isFrozen;

    public PlayerRecord(String playerUUID, String playerName, String wallet, boolean isFrozen) {

        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.wallet = wallet;
        this.isFrozen = isFrozen;

    }

    public String getPlayerUUID() {
        return this.playerUUID;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public String getWallet() {
        return this.wallet;
    }

    public boolean isFrozen() {
        return this.isFrozen;
    }

    public void setFrozen(boolean frozen) {
        this.isFrozen = frozen;
    }

}
