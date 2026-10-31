package banano.bananominecraft.bananoeconomy.classes;

public class BankRecord
{
    private final String bankName;
    private final String address;
    private final String ownerUuid;
    private final long   createdAt;

    public BankRecord(String bankName, String address, String ownerUuid, long createdAt)
    {
        this.bankName  = bankName;
        this.address   = address;
        this.ownerUuid = ownerUuid;
        this.createdAt = createdAt;
    }

    public String getBankName()  { return bankName;  }
    public String getAddress()   { return address;   }
    public String getOwnerUuid() { return ownerUuid; }
    public long   getCreatedAt() { return createdAt; }
}
