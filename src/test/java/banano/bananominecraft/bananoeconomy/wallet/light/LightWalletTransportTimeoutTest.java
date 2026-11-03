package banano.bananominecraft.bananoeconomy.wallet.light;

import banano.bananominecraft.bananoeconomy.io.RequestSentIOException;
import banano.bananominecraft.bananoeconomy.wallet.crypto.Hex;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A main-thread read timeout reaches the light wallet as a {@link RequestSentIOException} (an IOException) from
 * {@link HttpLightRpc}; it must be handled exactly like any other lost answer: re-query, never rebuild the block.
 */
class LightWalletTransportTimeoutTest
{
    private static final long THRESHOLD = 0xf000000000000000L;

    private static byte[] seed()
    {
        byte[] s = new byte[32];
        for (int i = 0; i < 32; i++)
        {
            s[i] = (byte) (i * 7 + 1);
        }
        return s;
    }

    /** Ledger whose I/O failures look like the transport's post-send read timeouts. */
    private static LightRpc asTimeouts(FakeLedger ledger)
    {
        return request ->
        {
            try
            {
                return ledger.call(request);
            }
            catch (IOException e)
            {
                throw new RequestSentIOException(new SocketTimeoutException("Read timed out"));
            }
        };
    }

    @Test
    void readTimeoutAfterProcess_isResolvedFromTheLedger_withoutResending() throws Exception
    {
        FakeLedger ledger = new FakeLedger(THRESHOLD);
        String rep = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(new byte[32], 0)), "ban_");
        LightWallet wallet = new LightWallet(seed(), "ban_", rep, asTimeouts(ledger), new CountingWorkGenerator(),
                THRESHOLD, Logger.getLogger("t"));
        try
        {
            String src = ledger.fund(wallet.address(0), BigInteger.valueOf(100));
            wallet.receive(0, src);
            int before = ledger.count("process");
            ledger.addFaults(FakeLedger.Fault.FAIL_AFTER);

            String stranger = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(new byte[32], 5)), "ban_");
            String hash = wallet.send(0, stranger, BigInteger.valueOf(30));

            assertEquals(hash, Hex.encode(ledger.account(wallet.address(0)).frontier));
            assertEquals(before + 1, ledger.count("process"), "the block must not be published twice");
            assertFalse(wallet.hasUnresolved(0));
        }
        finally
        {
            wallet.shutdown();
        }
    }

    @Test
    void timeoutOnEveryAttempt_isUnknownOutcome_andKeepsTheBlockPending() throws Exception
    {
        FakeLedger ledger = new FakeLedger(THRESHOLD);
        String rep = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(new byte[32], 0)), "ban_");
        LightWallet wallet = new LightWallet(seed(), "ban_", rep, asTimeouts(ledger), new CountingWorkGenerator(),
                THRESHOLD, Logger.getLogger("t"));
        try
        {
            wallet.receive(0, ledger.fund(wallet.address(0), BigInteger.valueOf(100)));
            ledger.addFaults(FakeLedger.Fault.FAIL_BEFORE, FakeLedger.Fault.FAIL_BEFORE);

            LightWalletException ex = assertThrows(LightWalletException.class,
                    () -> wallet.send(0, wallet.address(1), BigInteger.valueOf(30)));
            assertEquals(LightWalletException.Kind.UNKNOWN_OUTCOME, ex.kind());
            assertTrue(wallet.hasUnresolved(0));
        }
        finally
        {
            wallet.shutdown();
        }
    }
}
