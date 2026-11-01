package banano.bananominecraft.bananoeconomy.wallet.light;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable record of signed blocks whose {@code process} outcome is unknown, at most one per account index.
 *
 * <p>{@link LightWallet} saves a block here (as the exact {@code process} request JSON, which includes the subtype
 * and the signed block with its work) the moment its outcome becomes unknown and clears it once the outcome is
 * resolved. On construction it loads every record and treats each one as unresolved: the block is republished or
 * resolved against the ledger before anything else is built on that account, so a restart can never lead to a
 * second, different block on the same previous.</p>
 *
 * <p>Calls are made while holding the account's lock (and from the constructor for {@link #loadAll()}); an
 * implementation must be thread-safe across indices. {@link #save} should be durable when it returns (e.g. write
 * to a temporary file, fsync, atomic rename). An implementation may throw a {@link RuntimeException}; the wallet
 * logs it and keeps its in-memory record.</p>
 */
public interface PendingBlockStore
{
    /**
     * Durably record the unresolved block of account {@code index}, replacing any previous record for it.
     *
     * @param index     account index
     * @param blockJson the {@code process} request JSON of the signed block (contains no secrets)
     */
    void save(int index, String blockJson);

    /**
     * Remove the record for account {@code index}, if any.
     *
     * @param index account index
     */
    void clear(int index);

    /**
     * @return every stored record, index &rarr; block JSON (never {@code null})
     */
    Map<Integer, String> loadAll();

    /**
     * @return a store that keeps records in memory only (lost on restart); the default for
     *         {@link LightWallet}'s constructor without a store
     */
    static PendingBlockStore inMemory()
    {
        return new PendingBlockStore()
        {
            private final Map<Integer, String> records = new ConcurrentHashMap<>();

            @Override
            public void save(int index, String blockJson)
            {
                records.put(index, blockJson);
            }

            @Override
            public void clear(int index)
            {
                records.remove(index);
            }

            @Override
            public Map<Integer, String> loadAll()
            {
                return Collections.unmodifiableMap(new HashMap<>(records));
            }
        };
    }
}
