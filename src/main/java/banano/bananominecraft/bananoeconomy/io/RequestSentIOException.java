package banano.bananominecraft.bananoeconomy.io;

import java.io.IOException;

/**
 * An I/O failure that happened after the request had been handed to the network (the node may have
 * received and acted on it): a read timeout, a connection reset while waiting for the answer, an
 * error status, or an oversized/too slow response.
 *
 * <p>Failures before that point (connect timeout, connection refused, unknown host) are plain
 * {@link IOException}s: the node cannot have seen the request. State-changing wallet actions use
 * the distinction to report an unknown outcome instead of a definite failure.</p>
 */
public class RequestSentIOException extends IOException
{
    public RequestSentIOException(IOException cause)
    {
        super(cause.getMessage(), cause);
    }
}
