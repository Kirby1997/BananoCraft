package banano.bananominecraft.bananoeconomy.classes;

import java.time.LocalDateTime;
import java.util.UUID;

public class OfflinePaymentRecord {

    private UUID targetPlayerUUID;

    private String fromPlayerName;

    private double paymentAmount;
    private String blockHash;
    private String message;

    private LocalDateTime transactionDate;

    public OfflinePaymentRecord(UUID targetPlayerUUID, String fromPlayerName, double paymentAmount, String blockHash, LocalDateTime transactionDate, String message) {
        this.targetPlayerUUID = targetPlayerUUID;
        this.fromPlayerName = fromPlayerName;
        this.paymentAmount = paymentAmount;
        this.blockHash = blockHash;
        this.transactionDate = transactionDate;
        this.message = message;
    }

    public UUID getTargetPlayerUUID() {
        return this.targetPlayerUUID;
    }

    public String getFromPlayerName() {
        return this.fromPlayerName;
    }

    public double getPaymentAmount() {
        return this.paymentAmount;
    }

    public String getBlockHash() {
        return this.blockHash;
    }

    public LocalDateTime getTransactionDate() {
        return this.transactionDate;
    }

    public String getMessage() {
        return this.message;
    }

}
