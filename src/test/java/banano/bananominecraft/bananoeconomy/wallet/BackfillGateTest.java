package banano.bananominecraft.bananoeconomy.wallet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackfillGateTest
{
    @Test
    void alwaysOpen_neverBlocks()
    {
        BackfillGate gate = BackfillGate.alwaysOpen();
        assertTrue(gate.isOpen());
        assertTrue(gate.awaitOpen(0));
    }

    @Test
    void closedGate_timesOutOffTheMainThread()
    {
        BackfillGate gate = new BackfillGate(false, MainThreadGuard.neverPrimary());
        long start = System.nanoTime();
        assertFalse(gate.awaitOpen(100));
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 90);
        assertFalse(gate.isOpen());
    }

    @Test
    void closedGate_returnsImmediatelyOnThePrimaryThread()
    {
        BackfillGate gate = new BackfillGate(false, new MainThreadGuard(() -> true, Runnable::run));
        long start = System.nanoTime();
        assertFalse(gate.awaitOpen(60_000));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000);
    }

    @Test
    void opening_wakesWaiters() throws Exception
    {
        BackfillGate gate = new BackfillGate(false, MainThreadGuard.neverPrimary());
        Thread opener = new Thread(() ->
        {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { }
            gate.open();
        });
        opener.start();

        assertTrue(gate.awaitOpen(10_000));
        assertTrue(gate.isOpen());
        opener.join();
    }

    @Test
    void openIsIdempotent_andStaysOpen()
    {
        BackfillGate gate = new BackfillGate(false, MainThreadGuard.neverPrimary());
        gate.open();
        gate.open();
        assertTrue(gate.awaitOpen(0));
    }

    @Test
    void interruptedWaiterDoesNotReportOpen()
    {
        BackfillGate gate = new BackfillGate(false, MainThreadGuard.neverPrimary());
        Thread.currentThread().interrupt();
        assertFalse(gate.awaitOpen(10_000));
        assertTrue(Thread.interrupted(), "interrupt flag is preserved (and cleared here)");
    }
}
