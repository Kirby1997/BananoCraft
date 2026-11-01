package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.logging.Logger;

/**
 * Startup backfill of {@code walletIndex}: records created by older versions know their address but
 * not the seed-derivation index it came from. This derives addresses {@code 0..scanLimit} from the
 * decrypted seed and matches them to those records, persisting every match.
 *
 * <p>Runs in both wallet modes. It is CPU-bound, so call it off the main thread. Records that stay
 * unmatched are summarised at SEVERE; in light mode such a player cannot spend (the signer refuses)
 * and is never silently given a new wallet.</p>
 */
public final class WalletIndexBackfill
{
    /** How many unmatched records are listed by name in the SEVERE summary. */
    private static final int MAX_LISTED = 20;

    /** Outcome of one backfill pass. */
    public static final class Result
    {
        public final int examined;
        public final int matched;
        public final int persistFailures;
        public final List<String> unmatched;

        Result(int examined, int matched, int persistFailures, List<String> unmatched)
        {
            this.examined = examined;
            this.matched = matched;
            this.persistFailures = persistFailures;
            this.unmatched = unmatched;
        }
    }

    private final IDBConnector db;
    private final Logger log;

    public WalletIndexBackfill(IDBConnector db, Logger log)
    {
        this.db = db;
        this.log = log;
    }

    /** Address of the account at {@code index} of {@code seed32} ({@code ban_} prefix), via A's NanoKeys. */
    public static IntFunction<String> addressDeriver(byte[] seed32)
    {
        return index -> NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(seed32, index)), "ban_");
    }

    /**
     * Finds the derivation index of each address in {@code addresses}.
     *
     * @param addressOf  derives the address at an index
     * @param scanLimit  highest index tried (inclusive)
     * @return address → index for every address found; stops early once all are found
     */
    public static Map<String, Integer> match(IntFunction<String> addressOf, Iterable<String> addresses, int scanLimit)
    {
        Map<String, Integer> found = new HashMap<>();
        Set<String> wanted = new HashSet<>();
        for (String address : addresses)
        {
            if (address != null)
            {
                wanted.add(address);
            }
        }

        for (int index = 0; index <= scanLimit && found.size() < wanted.size(); index++)
        {
            String derived = addressOf.apply(index);
            if (wanted.contains(derived) && !found.containsKey(derived))
            {
                found.put(derived, index);
            }
        }

        return found;
    }

    /** Backfills player records with a null index; logs a SEVERE summary of any that stay unmatched. */
    public Result backfillPlayers(IntFunction<String> playerAddressOf, int scanLimit)
    {
        List<PlayerRecord> missing = db.getPlayerRecordsMissingWalletIndex();
        if (missing.isEmpty())
        {
            return new Result(0, 0, 0, new ArrayList<>());
        }

        List<String> wallets = new ArrayList<>();
        for (PlayerRecord record : missing)
        {
            wallets.add(record.getWallet());
        }

        Map<String, Integer> found = match(playerAddressOf, wallets, scanLimit);

        int matched = 0;
        int failures = 0;
        List<String> unmatched = new ArrayList<>();

        for (PlayerRecord record : missing)
        {
            Integer index = found.get(record.getWallet());
            if (index == null)
            {
                unmatched.add(describe(record.getPlayerName(), record.getPlayerUUID(), record.getWallet()));
            }
            else if (db.setPlayerWalletIndex(record.getPlayerUUID(), index))
            {
                matched++;
            }
            else
            {
                failures++;
            }
        }

        log.info("Wallet index backfill (players): matched " + matched + " of " + missing.size()
                + " record(s) without an index.");
        reportUnmatched("player", unmatched, failures, scanLimit);

        return new Result(missing.size(), matched, failures, unmatched);
    }

    /** Backfills bank records with a null index; logs a SEVERE summary of any that stay unmatched. */
    public Result backfillBanks(IntFunction<String> bankAddressOf, int scanLimit)
    {
        Map<String, BankRecord> missing = new LinkedHashMap<>();
        for (String name : db.getAllBankNames())
        {
            BankRecord record = db.getBankRecord(name);
            if (record != null && record.getWalletIndex() == null && record.getAddress() != null)
            {
                missing.put(name, record);
            }
        }

        if (missing.isEmpty())
        {
            return new Result(0, 0, 0, new ArrayList<>());
        }

        List<String> addresses = new ArrayList<>();
        for (BankRecord record : missing.values())
        {
            addresses.add(record.getAddress());
        }

        Map<String, Integer> found = match(bankAddressOf, addresses, scanLimit);

        int matched = 0;
        int failures = 0;
        List<String> unmatched = new ArrayList<>();

        for (BankRecord record : missing.values())
        {
            Integer index = found.get(record.getAddress());
            if (index == null)
            {
                unmatched.add(describe(record.getBankName(), null, record.getAddress()));
            }
            else if (db.setBankWalletIndex(record.getBankName(), index))
            {
                matched++;
            }
            else
            {
                failures++;
            }
        }

        log.info("Wallet index backfill (banks): matched " + matched + " of " + missing.size()
                + " record(s) without an index.");
        reportUnmatched("bank", unmatched, failures, scanLimit);

        return new Result(missing.size(), matched, failures, unmatched);
    }

    private static String describe(String name, String uuid, String address)
    {
        return name + (uuid != null ? " (" + uuid + ")" : "") + " " + address;
    }

    private void reportUnmatched(String kind, List<String> unmatched, int persistFailures, int scanLimit)
    {
        if (persistFailures > 0)
        {
            log.severe(persistFailures + " " + kind + " record(s) matched the seed but their wallet index could not"
                    + " be saved - they will be retried at the next startup.");
        }

        if (unmatched.isEmpty())
        {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(unmatched.size()).append(" ").append(kind).append(" record(s) could NOT be matched to the ")
                .append(kind).append(" wallet seed within indices 0..").append(scanLimit)
                .append(". In light mode these accounts cannot send, receive or change representative until"
                        + " matched (raise walletIndexScanLimit in config.yml and restart). They are never given"
                        + " a new wallet. Unmatched: ");

        for (int i = 0; i < unmatched.size() && i < MAX_LISTED; i++)
        {
            message.append(i > 0 ? "; " : "").append(unmatched.get(i));
        }
        if (unmatched.size() > MAX_LISTED)
        {
            message.append("; ... and ").append(unmatched.size() - MAX_LISTED).append(" more");
        }

        log.severe(message.toString());
    }
}
