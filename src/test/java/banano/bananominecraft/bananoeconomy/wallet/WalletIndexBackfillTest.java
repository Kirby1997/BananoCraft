package banano.bananominecraft.bananoeconomy.wallet;

import banano.bananominecraft.bananoeconomy.classes.BankRecord;
import banano.bananominecraft.bananoeconomy.classes.PlayerRecord;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import banano.bananominecraft.bananoeconomy.wallet.crypto.NanoKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletIndexBackfillTest
{
    /** Mocked key derivation: index i derives "addr-i". */
    private static final IntFunction<String> FAKE = index -> "addr-" + index;

    private IDBConnector db;
    private Logger log;
    private final List<LogRecord> logged = new ArrayList<>();
    private WalletIndexBackfill backfill;

    @BeforeEach
    void setUp()
    {
        db = mock(IDBConnector.class);
        log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler()
        {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        backfill = new WalletIndexBackfill(db, log);
    }

    private static PlayerRecord legacyPlayer(String uuid, String name, String wallet)
    {
        return new PlayerRecord(uuid, name, wallet, false);
    }

    // -------------------------------------------------------------------------
    // match()
    // -------------------------------------------------------------------------

    @Test
    void match_findsEachAddressAtItsIndex()
    {
        Map<String, Integer> found = WalletIndexBackfill.match(FAKE, List.of("addr-0", "addr-250", "addr-9"), 1000);

        assertEquals(Map.of("addr-0", 0, "addr-250", 250, "addr-9", 9), found);
    }

    @Test
    void match_respectsTheScanLimit_inclusively()
    {
        Map<String, Integer> found = WalletIndexBackfill.match(FAKE, List.of("addr-10", "addr-11"), 10);

        assertEquals(Map.of("addr-10", 10), found);
    }

    @Test
    void match_stopsAsSoonAsEverythingIsFound()
    {
        List<Integer> derived = new ArrayList<>();
        IntFunction<String> counting = index ->
        {
            derived.add(index);
            return "addr-" + index;
        };

        WalletIndexBackfill.match(counting, List.of("addr-3"), 10_000);

        assertEquals(List.of(0, 1, 2, 3), derived);
    }

    @Test
    void match_ignoresNullAndUnknownAddresses()
    {
        List<String> wanted = new ArrayList<>();
        wanted.add(null);
        wanted.add("someone-elses-address");

        assertTrue(WalletIndexBackfill.match(FAKE, wanted, 50).isEmpty());
    }

    @Test
    void match_usesTheRealKeyDerivation()
    {
        byte[] seed = new byte[32];
        seed[0] = 42;
        IntFunction<String> real = WalletIndexBackfill.addressDeriver(seed);

        String at3 = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(seed, 3)), "ban_");
        String at7 = NanoKeys.address(NanoKeys.publicKey(NanoKeys.privateKey(seed, 7)), "ban_");

        assertTrue(at3.startsWith("ban_"));
        assertEquals(at3, real.apply(3));
        assertEquals(Map.of(at3, 3, at7, 7), WalletIndexBackfill.match(real, List.of(at3, at7, "ban_unrelated"), 20));
    }

    // -------------------------------------------------------------------------
    // players
    // -------------------------------------------------------------------------

    @Test
    void backfillPlayers_persistsEveryMatch()
    {
        when(db.getPlayerRecordsMissingWalletIndex()).thenReturn(List.of(
                legacyPlayer("uuid-a", "Alice", "addr-4"),
                legacyPlayer("uuid-b", "Bob", "addr-12")));
        when(db.setPlayerWalletIndex(anyString(), anyInt())).thenReturn(true);

        WalletIndexBackfill.Result result = backfill.backfillPlayers(FAKE, 100);

        assertEquals(2, result.examined);
        assertEquals(2, result.matched);
        assertTrue(result.unmatched.isEmpty());
        verify(db).setPlayerWalletIndex("uuid-a", 4);
        verify(db).setPlayerWalletIndex("uuid-b", 12);
        assertTrue(logged.stream().noneMatch(r -> r.getLevel() == Level.SEVERE));
    }

    @Test
    void backfillPlayers_logsSevereSummary_forUnmatchedAndNeverTouchesThem()
    {
        when(db.getPlayerRecordsMissingWalletIndex()).thenReturn(List.of(
                legacyPlayer("uuid-a", "Alice", "addr-4"),
                legacyPlayer("uuid-z", "Zed", "addr-from-another-seed")));
        when(db.setPlayerWalletIndex(anyString(), anyInt())).thenReturn(true);

        WalletIndexBackfill.Result result = backfill.backfillPlayers(FAKE, 100);

        assertEquals(1, result.matched);
        assertEquals(1, result.unmatched.size());
        assertTrue(result.unmatched.get(0).contains("Zed"));
        verify(db, never()).setPlayerWalletIndex(eq("uuid-z"), anyInt());

        LogRecord severe = logged.stream().filter(r -> r.getLevel() == Level.SEVERE).findFirst().orElseThrow();
        assertTrue(severe.getMessage().contains("Zed"), severe.getMessage());
        assertTrue(severe.getMessage().contains("addr-from-another-seed"), severe.getMessage());
        assertTrue(severe.getMessage().contains("walletIndexScanLimit"), severe.getMessage());
    }

    @Test
    void backfillPlayers_countsAndReportsPersistFailures()
    {
        when(db.getPlayerRecordsMissingWalletIndex()).thenReturn(List.of(legacyPlayer("uuid-a", "Alice", "addr-4")));
        when(db.setPlayerWalletIndex(anyString(), anyInt())).thenReturn(false);

        WalletIndexBackfill.Result result = backfill.backfillPlayers(FAKE, 100);

        assertEquals(0, result.matched);
        assertEquals(1, result.persistFailures);
        assertTrue(logged.stream().anyMatch(r -> r.getLevel() == Level.SEVERE));
    }

    @Test
    void backfillPlayers_doesNothing_whenNoRecordLacksAnIndex()
    {
        when(db.getPlayerRecordsMissingWalletIndex()).thenReturn(List.of());

        WalletIndexBackfill.Result result = backfill.backfillPlayers(i -> { throw new AssertionError("no scan"); }, 100);

        assertEquals(0, result.examined);
        verify(db, never()).setPlayerWalletIndex(anyString(), anyInt());
    }

    // -------------------------------------------------------------------------
    // banks
    // -------------------------------------------------------------------------

    @Test
    void backfillBanks_matchesOnlyBanksWithoutAnIndex()
    {
        when(db.getAllBankNames()).thenReturn(List.of("old", "new", "foreign"));
        when(db.getBankRecord("old")).thenReturn(new BankRecord("old", "addr-2", "owner", 1L));
        when(db.getBankRecord("new")).thenReturn(new BankRecord("new", "addr-9", "owner", 1L, 9));
        when(db.getBankRecord("foreign")).thenReturn(new BankRecord("foreign", "addr-elsewhere", "owner", 1L));
        when(db.setBankWalletIndex(anyString(), anyInt())).thenReturn(true);

        WalletIndexBackfill.Result result = backfill.backfillBanks(FAKE, 100);

        assertEquals(2, result.examined);
        assertEquals(1, result.matched);
        verify(db).setBankWalletIndex("old", 2);
        verify(db, never()).setBankWalletIndex(eq("new"), anyInt());
        verify(db, never()).setBankWalletIndex(eq("foreign"), anyInt());
        assertTrue(logged.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getMessage().contains("foreign")));
    }
}
