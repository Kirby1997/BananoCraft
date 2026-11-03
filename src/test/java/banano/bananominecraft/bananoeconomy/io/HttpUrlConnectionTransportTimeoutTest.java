package banano.bananominecraft.bananoeconomy.io;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real sockets against a local com.sun.net.httpserver stub. */
class HttpUrlConnectionTransportTimeoutTest
{
    private HttpServer server;
    private String url;
    private volatile long delayMs;
    private final AtomicInteger hits = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex ->
        {
            hits.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            try
            {
                if (delayMs > 0)
                {
                    release.await(delayMs, TimeUnit.MILLISECONDS);
                }
            }
            catch (InterruptedException ignored)
            {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            try
            {
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody())
                {
                    os.write(body);
                }
            }
            catch (IOException ignored)
            {
                // client gave up
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop()
    {
        release.countDown();
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    @Test
    void fastResponse_succeedsWithShortTimeouts() throws IOException
    {
        assertEquals("{\"ok\":true}", new HttpUrlConnectionTransport().post(url, "{}", 500, 2000));
    }

    @Test
    void slowResponse_afterSend_isRequestSentIOException_causedBySocketTimeout()
    {
        delayMs = 5000;
        long start = System.nanoTime();

        RequestSentIOException ex = assertThrows(RequestSentIOException.class,
                () -> new HttpUrlConnectionTransport().post(url, "{\"action\":\"send\"}", 500, 300));

        assertInstanceOf(SocketTimeoutException.class, ex.getCause());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000, "must give up quickly");
        assertEquals(1, hits.get(), "the stub did receive the request");
    }

    @Test
    void slowResponse_withNormalTimeouts_stillWaits() throws IOException
    {
        delayMs = 400;
        assertEquals("{\"ok\":true}", new HttpUrlConnectionTransport().post(url, "{}"));
    }

    @Test
    void connectionRefused_isPlainIOException_notRequestSent() throws IOException
    {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress()))
        {
            closedPort = s.getLocalPort();
        }

        IOException ex = assertThrows(IOException.class,
                () -> new HttpUrlConnectionTransport().post("http://127.0.0.1:" + closedPort, "{}", 500, 500));
        assertFalse(ex instanceof RequestSentIOException, ex.toString());
    }

    @Test
    void unknownHost_isPlainIOException_notRequestSent()
    {
        IOException ex = assertThrows(IOException.class,
                () -> new HttpUrlConnectionTransport().post("http://no-such-host.invalid:7072", "{}", 500, 500));
        assertFalse(ex instanceof RequestSentIOException, ex.toString());
    }

    @Test
    void connectTimeout_isPlainIOException_notRequestSent()
    {
        // 10.255.255.1 is typically unroutable; if the sandbox rejects it instantly that is a plain IOException too.
        IOException ex = assertThrows(IOException.class,
                () -> new HttpUrlConnectionTransport().post("http://10.255.255.1:7072", "{}", 200, 500));
        assertFalse(ex instanceof RequestSentIOException, ex.toString());
    }

    @Test
    void errorStatus_afterSend_isRequestSentIOException()
    {
        server.removeContext("/");
        server.createContext("/", ex ->
        {
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        assertThrows(RequestSentIOException.class, () -> new HttpUrlConnectionTransport().post(url, "{}", 500, 2000));
    }

    @Test
    void totalDeadlineIsMinOfDefaultAndReadTimeout() throws Exception
    {
        // Drip bytes slower than the read timeout allows in total but faster than the per-read silence limit.
        server.removeContext("/");
        server.createContext("/", ex ->
        {
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(200, 0);
            try (OutputStream os = ex.getResponseBody())
            {
                for (int i = 0; i < 40; i++)
                {
                    os.write('x');
                    os.flush();
                    Thread.sleep(50);
                }
            }
            catch (IOException | InterruptedException ignored)
            {
                // client gave up
            }
        });

        long start = System.nanoTime();
        RequestSentIOException ex = assertThrows(RequestSentIOException.class,
                () -> new HttpUrlConnectionTransport().post(url, "{}", 500, 600));
        assertTrue(ex.getMessage().contains("600 ms"), ex.getMessage());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1900);

        // The two-argument call keeps the 30 s deadline, so the same 2 s drip completes.
        assertEquals(40, new HttpUrlConnectionTransport().post(url, "{}").length());
    }
}
