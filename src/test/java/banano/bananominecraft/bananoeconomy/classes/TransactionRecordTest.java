package banano.bananominecraft.bananoeconomy.classes;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionRecordTest
{
    private static final String EXPLORER_URL = "https://creeper.banano.cc/explorer/block/";

    // 64-char hash — realistic Banano block hash length
    private static final String HASH   = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String WALLET = "ban_3t6k35gi95xu6tergt6p69ck76ogmitsa8mnijtpxm9fkcm736xtoncuohr3";

    // Fixed, deterministic date used throughout: 16 May 2025 at 14:30
    private static final LocalDateTime DATE = LocalDateTime.of(2025, 5, 16, 14, 30);

    @Mock private ConfigEngine configEngine;
    @Mock private I18n i18n;

    /** Real instance backed by mocked deps — set up fresh before each test. */
    private MessageGenerator messageGenerator;

    @BeforeEach
    void setUp()
    {
        when(configEngine.getExplorerBlock()).thenReturn(EXPLORER_URL);

        // Header column labels (English defaults)
        when(i18n.get(Locale.ENGLISH, "history.col_date")).thenReturn("Date");
        when(i18n.get(Locale.ENGLISH, "history.col_amount")).thenReturn("Amount");
        when(i18n.get(Locale.ENGLISH, "history.col_confirmed")).thenReturn("C");
        when(i18n.get(Locale.ENGLISH, "history.col_hash")).thenReturn("Hash");

        messageGenerator = new MessageGenerator(i18n, configEngine);
    }

    /**
     * Extracts readable plain text from a BaseComponent array.
     *
     * <p>{@link BaseComponent#toPlainText} returns the raw text string verbatim,
     * which still contains any legacy {@code §x} colour escapes that were embedded
     * directly into the StringBuilder (e.g. {@code ChatColor.YELLOW} prepended to
     * the row string). {@link ChatColor#stripColor} removes those escapes so the
     * result can be compared against plain human-readable content.</p>
     */
    private static String plainText(BaseComponent[] components)
    {
        return ChatColor.stripColor(BaseComponent.toPlainText(components));
    }

    // -------------------------------------------------------------------------
    // Record construction
    // -------------------------------------------------------------------------

    @Test
    void constructor_setsAllFields()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Send, 99.0, HASH, true);

        assertEquals(DATE,                      record.transactionDate());
        assertEquals(WALLET,                    record.address());
        assertEquals(TransactionDirection.Send, record.direction());
        assertEquals(99.0,                      record.amount());
        assertEquals(HASH,                      record.transactionHash());
        assertTrue(record.confirmed());
    }

    // -------------------------------------------------------------------------
    // getHeaderString
    // -------------------------------------------------------------------------

    @Test
    void headerString_containsDateLabel()
    {
        assertTrue(TransactionRecord.getHeaderString(i18n, Locale.ENGLISH).contains("Date"));
    }

    @Test
    void headerString_containsAmountLabel()
    {
        assertTrue(TransactionRecord.getHeaderString(i18n, Locale.ENGLISH).contains("Amount"));
    }

    @Test
    void headerString_containsConfirmedLabel()
    {
        // Header uses abbreviated "C" for the confirmed column
        assertTrue(TransactionRecord.getHeaderString(i18n, Locale.ENGLISH).contains("C"));
    }

    @Test
    void headerString_containsHashLabel()
    {
        assertTrue(TransactionRecord.getHeaderString(i18n, Locale.ENGLISH).contains("Hash"));
    }

    @Test
    void headerString_dateAppearsBeforeAmount()
    {
        String header = TransactionRecord.getHeaderString(i18n, Locale.ENGLISH);
        assertTrue(header.indexOf("Date") < header.indexOf("Amount"),
                "'Date' column should appear before 'Amount' column");
    }

    @Test
    void headerString_amountAppearsBeforeHash()
    {
        String header = TransactionRecord.getHeaderString(i18n, Locale.ENGLISH);
        assertTrue(header.indexOf("Amount") < header.indexOf("Hash"),
                "'Amount' column should appear before 'Hash' column");
    }

    // -------------------------------------------------------------------------
    // toRecordString — date formatting
    // -------------------------------------------------------------------------

    @Test
    void toRecordString_dateFormattedAsDdMmYyHHmm()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Receive, 10.0, HASH, true);

        // "16/05/25 14:30" must appear at the very start of the plain-text row
        assertTrue(plainText(record.toRecordString(messageGenerator)).startsWith("16/05/25 14:30"),
                "Row should start with the date formatted as dd/MM/yy HH:mm");
    }

    @Test
    void toRecordString_leadingZeroPaddedDate()
    {
        // Day 1, month 1 should both be zero-padded: "01/01/25 09:05"
        LocalDateTime earlyDate = LocalDateTime.of(2025, 1, 1, 9, 5);
        TransactionRecord record = new TransactionRecord(earlyDate, WALLET, TransactionDirection.Receive, 1.0, HASH, false);

        assertTrue(plainText(record.toRecordString(messageGenerator)).startsWith("01/01/25 09:05"),
                "Single-digit day, month and hour should be zero-padded");
    }

    // -------------------------------------------------------------------------
    // toRecordString — confirmed flag
    // -------------------------------------------------------------------------

    @Test
    void toRecordString_confirmed_showsY()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Receive, 10.0, HASH, true);
        String row = plainText(record.toRecordString(messageGenerator));

        assertTrue(row.contains("Y"),  "Confirmed record should show 'Y' in the row");
        assertFalse(row.contains("N"), "Confirmed record should not show 'N' in the row");
    }

    @Test
    void toRecordString_unconfirmed_showsN()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Receive, 10.0, HASH, false);
        String row = plainText(record.toRecordString(messageGenerator));

        assertTrue(row.contains("N"),  "Unconfirmed record should show 'N' in the row");
        assertFalse(row.contains("Y"), "Unconfirmed record should not show 'Y' in the row");
    }

    // -------------------------------------------------------------------------
    // toRecordString — hash truncation
    // -------------------------------------------------------------------------

    @Test
    void toRecordString_hashTruncatedToEightCharsWithEllipsis()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Send, 5.0, HASH, true);

        // First 8 chars of HASH are "abcdef12"
        assertTrue(plainText(record.toRecordString(messageGenerator)).contains("abcdef12..."),
                "Row should contain the first 8 hash characters followed by '...'");
    }

    @Test
    void toRecordString_fullHashNotIncluded()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Send, 5.0, HASH, true);

        assertFalse(plainText(record.toRecordString(messageGenerator)).contains(HASH),
                "Full hash should not appear in the row string");
    }

    // -------------------------------------------------------------------------
    // toRecordString — amount formatting (DecimalFormat "#.##")
    // -------------------------------------------------------------------------

    @Test
    void toRecordString_wholeNumberAmount_noTrailingDecimalPoint()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Send, 25.0, HASH, false);
        String row = plainText(record.toRecordString(messageGenerator));

        assertTrue(row.contains("25"),    "Integer amount 25.0 should render as '25'");
        assertFalse(row.contains("25.0"), "Trailing '.0' should be suppressed");
    }

    @Test
    void toRecordString_fractionalAmount_retainsSignificantDecimals()
    {
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Send, 12.34, HASH, false);

        assertTrue(plainText(record.toRecordString(messageGenerator)).contains("12.34"),
                "Amount 12.34 should be rendered in full");
    }

    @Test
    void toRecordString_singleDecimalAmount_noTrailingZero()
    {
        // DecimalFormat "#.##" renders 7.5 as "7.5", not "7.50"
        TransactionRecord record = new TransactionRecord(DATE, WALLET, TransactionDirection.Send, 7.5, HASH, false);
        String row = plainText(record.toRecordString(messageGenerator));

        assertTrue(row.contains("7.5"),    "Amount 7.5 should render as '7.5'");
        assertFalse(row.contains("7.50"), "Trailing zero should be suppressed");
    }
}
