package banano.bananominecraft.bananoeconomy.wallet;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MainThreadGuardTest
{
    @Test
    void runOffMainThread_runsInlineWhenNotPrimary()
    {
        List<Runnable> async = new ArrayList<>();
        AtomicInteger ran = new AtomicInteger();
        new MainThreadGuard(() -> false, async::add).runOffMainThread(ran::incrementAndGet);

        assertEquals(1, ran.get());
        assertTrue(async.isEmpty());
    }

    @Test
    void runOffMainThread_schedulesWhenPrimary()
    {
        List<Runnable> async = new ArrayList<>();
        AtomicInteger ran = new AtomicInteger();
        new MainThreadGuard(() -> true, async::add).runOffMainThread(ran::incrementAndGet);

        assertEquals(0, ran.get());
        assertEquals(1, async.size());
        async.get(0).run();
        assertEquals(1, ran.get());
    }

    @Test
    void neverPrimary_isNotPrimary()
    {
        assertFalse(MainThreadGuard.neverPrimary().isPrimaryThread());
    }
}
