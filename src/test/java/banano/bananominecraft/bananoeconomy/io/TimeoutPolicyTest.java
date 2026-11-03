package banano.bananominecraft.bananoeconomy.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutPolicyTest
{
    /** Records which post overload was used and with which timeouts. */
    private static final class Recorder implements HttpTransport
    {
        final List<String> calls = new ArrayList<>();

        @Override
        public String post(String url, String json)
        {
            calls.add("normal");
            return "{}";
        }

        @Override
        public String post(String url, String json, int connect, int read)
        {
            calls.add("short:" + connect + "/" + read);
            return "{}";
        }
    }

    @Test
    void primaryThread_usesShortTimeouts() throws IOException
    {
        Recorder rec = new Recorder();
        new TimeoutPolicy(() -> true, () -> 1000, () -> 5000).wrap(rec).post("http://x", "{}");
        assertEquals(List.of("short:1000/5000"), rec.calls);
    }

    @Test
    void asyncThread_usesNormalTimeouts() throws IOException
    {
        Recorder rec = new Recorder();
        new TimeoutPolicy(() -> false, () -> 1000, () -> 5000).wrap(rec).post("http://x", "{}");
        assertEquals(List.of("normal"), rec.calls);
    }

    @Test
    void disabledByZeroReadTimeout_usesNormalTimeoutsEvenOnPrimary() throws IOException
    {
        Recorder rec = new Recorder();
        TimeoutPolicy policy = new TimeoutPolicy(() -> true, () -> 1000, () -> 0);
        policy.wrap(rec).post("http://x", "{}");
        assertEquals(List.of("normal"), rec.calls);
        assertFalse(policy.useShortTimeouts());
        assertEquals(-1, policy.readTimeoutMs());
    }

    @Test
    void decisionIsMadePerCall_andFollowsLiveConfig() throws IOException
    {
        Recorder rec = new Recorder();
        AtomicBoolean primary = new AtomicBoolean(true);
        AtomicInteger read = new AtomicInteger(3000);
        HttpTransport t = new TimeoutPolicy(primary::get, () -> 500, read::get).wrap(rec);

        t.post("u", "{}");
        primary.set(false);
        t.post("u", "{}");
        primary.set(true);
        read.set(0);
        t.post("u", "{}");
        read.set(4000);
        t.post("u", "{}");

        assertEquals(List.of("short:500/3000", "normal", "normal", "short:500/4000"), rec.calls);
    }

    @Test
    void defaultFourArgPost_delegatesToTwoArg() throws IOException
    {
        AtomicInteger twoArg = new AtomicInteger();
        HttpTransport plain = (url, body) ->
        {
            twoArg.incrementAndGet();
            return "ok";
        };
        assertEquals("ok", plain.post("u", "b", 10, 20));
        assertEquals(1, twoArg.get());
    }

    @Test
    void explicitTimeoutsPassThroughTheWrapper() throws IOException
    {
        Recorder rec = new Recorder();
        new TimeoutPolicy(() -> false, () -> 1, () -> 1).wrap(rec).post("u", "{}", 7, 9);
        assertEquals(List.of("short:7/9"), rec.calls);
    }
}
