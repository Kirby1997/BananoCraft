package banano.bananominecraft.bananoeconomy.wallet;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Opened once the startup {@link WalletIndexBackfill} has finished. New wallet-index allocation
 * ({@code highest stored index + 1}) is only reliable after that: until legacy records have been
 * matched, their indices are not stored, so a new account could be handed an index a legacy account
 * already uses.
 */
public final class BackfillGate
{
    private final CountDownLatch latch;
    private final MainThreadGuard guard;

    /** @param open whether the gate starts open (nothing to wait for) */
    public BackfillGate(boolean open, MainThreadGuard guard)
    {
        this.latch = new CountDownLatch(open ? 0 : 1);
        this.guard = guard;
    }

    /** A gate that never blocks anyone. */
    public static BackfillGate alwaysOpen()
    {
        return new BackfillGate(true, MainThreadGuard.neverPrimary());
    }

    /** Marks the backfill finished (successfully or not); wakes every waiter. */
    public void open()
    {
        latch.countDown();
    }

    public boolean isOpen()
    {
        return latch.getCount() == 0;
    }

    /**
     * Waits for the gate, at most {@code timeoutMillis}, and never on the primary thread (there it only
     * checks the state).
     *
     * @return {@code true} if the gate is open
     */
    public boolean awaitOpen(long timeoutMillis)
    {
        if (isOpen())
        {
            return true;
        }
        if (guard.isPrimaryThread())
        {
            return false;
        }

        try
        {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
