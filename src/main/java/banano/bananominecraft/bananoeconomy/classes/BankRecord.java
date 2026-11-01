package banano.bananominecraft.bananoeconomy.classes;

public class BankRecord
{
    private final String bankName;
    private final String address;
    private final String ownerUuid;
    private final long   createdAt;

    /** Derivation index of {@link #address} within the bank wallet seed; {@code null} for a legacy record. */
    private volatile Integer walletIndex;

    public BankRecord(String bankName, String address, String ownerUuid, long createdAt)
    {
        this(bankName, address, ownerUuid, createdAt, null);
    }

    public BankRecord(String bankName, String address, String ownerUuid, long createdAt, Integer walletIndex)
    {
        this.bankName    = bankName;
        this.address     = address;
        this.ownerUuid   = ownerUuid;
        this.createdAt   = createdAt;
        this.walletIndex = walletIndex;
    }

    public String getBankName()  { return bankName;  }
    public String getAddress()   { return address;   }
    public String getOwnerUuid() { return ownerUuid; }
    public long   getCreatedAt() { return createdAt; }

    /** Seed derivation index of this bank's account, or {@code null} if not known (legacy record). */
    public Integer getWalletIndex() { return walletIndex; }

    /** Only changes the in-memory value; use {@code IDBConnector.setBankWalletIndex} to persist it. */
    public void setWalletIndex(Integer walletIndex) { this.walletIndex = walletIndex; }
}
