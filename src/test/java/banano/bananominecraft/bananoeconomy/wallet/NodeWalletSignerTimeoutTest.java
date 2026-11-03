package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import banano.bananominecraft.bananoeconomy.exceptions.UnknownOutcomeError;
import banano.bananominecraft.bananoeconomy.io.HttpTransport;
import banano.bananominecraft.bananoeconomy.io.RequestSentIOException;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeWalletSignerTimeoutTest
{
    private static NodeWalletSigner signer(HttpTransport http)
    {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        ConfigEngine config = mock(ConfigEngine.class);
        when(config.getNodeAddress()).thenReturn("http://node:7072");
        return new NodeWalletSigner(plugin, config, http);
    }

    private static HttpTransport failing(IOException e)
    {
        return (u, b) ->
        {
            throw e;
        };
    }

    private static final RequestSentIOException SENT_TIMEOUT =
            new RequestSentIOException(new SocketTimeoutException("Read timed out"));

    @Test
    void sendTimeoutAfterRequestSent_isUnknownOutcome()
    {
        assertThrows(UnknownOutcomeError.class, () ->
                signer(failing(SENT_TIMEOUT)).send("w", "ban_a", "ban_b", BigInteger.ONE, "id-1"));
    }

    @Test
    void receiveAndRepresentativeTimeoutAfterSend_areUnknownOutcome()
    {
        NodeWalletSigner s = signer(failing(SENT_TIMEOUT));
        assertThrows(UnknownOutcomeError.class, () -> s.receive("w", "ban_a", "HASH"));
        assertThrows(UnknownOutcomeError.class, () -> s.setRepresentative("w", "ban_a", "ban_r"));
    }

    @Test
    void connectFailure_isPlainTransactionError()
    {
        for (IOException e : new IOException[]{
                new SocketTimeoutException("connect timed out"),
                new ConnectException("Connection refused"),
                new java.net.UnknownHostException("nohost")})
        {
            NodeWalletSigner s = signer(failing(e));
            TransactionError err = assertThrows(TransactionError.class,
                    () -> s.send("w", "ban_a", "ban_b", BigInteger.ONE, "id-1"));
            assertFalse(err instanceof UnknownOutcomeError, e.toString());
            assertEquals("Send transaction failed", err.getMessage());
            assertEquals("Receive transaction failed",
                    assertThrows(TransactionError.class, () -> s.receive("w", "ban_a", "H")).getMessage());
            assertEquals("Set representative failed",
                    assertThrows(TransactionError.class, () -> s.setRepresentative("w", "a", "r")).getMessage());
        }
    }

    @Test
    void sentFailureWrappedInAnotherException_isStillUnknown()
    {
        HttpTransport t = (u, b) ->
        {
            throw new RuntimeException("wrapped", SENT_TIMEOUT);
        };
        assertThrows(UnknownOutcomeError.class, () ->
                signer(t).send("w", "ban_a", "ban_b", BigInteger.ONE, "id-1"));
    }

    @Test
    void readOnlyAction_keepsExistingFailureValue()
    {
        NodeWalletSigner s = signer(failing(SENT_TIMEOUT));
        assertNull(s.accountCreate("w", 1));
        assertEquals(WalletSigner.Status.UNREACHABLE, s.walletStatus(WalletSigner.Wallet.PLAYER));
    }
}
