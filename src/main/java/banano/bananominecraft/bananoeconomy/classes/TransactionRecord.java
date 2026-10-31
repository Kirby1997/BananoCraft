package banano.bananominecraft.bananoeconomy.classes;

import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.helpers.StringHelper;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record TransactionRecord(
        LocalDateTime transactionDate,
        String address,
        TransactionDirection direction,
        double amount,
        String transactionHash,
        boolean confirmed
)
{
    /** Compact, fixed-width date format: "dd/MM/yy HH:mm" (14 chars). */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    // Column widths (excluding the single space separator between each column)
    private static final int COL_DATE   = 14; // "dd/MM/yy HH:mm"
    private static final int COL_AMOUNT = 10; // right-aligned amount
    private static final int COL_CONF   =  3; // "Y/N"
    private static final int COL_HASH   = 11; // "xxxxxxxx..."

    /**
     * Formats a single history row as a BungeeCord component array.
     *
     * <p>The hash is rendered as a clickable block-explorer link via
     * {@link MessageGenerator#generateBlockExplorerLink(String, String)}.</p>
     *
     * @param messageGenerator the plugin's shared {@link MessageGenerator} instance
     */
    public BaseComponent[] toRecordString(MessageGenerator messageGenerator)
    {
        final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
        final StringBuilder sb = new StringBuilder();

        sb.append(direction == TransactionDirection.Send ? ChatColor.YELLOW : ChatColor.GREEN);
        sb.append(StringHelper.padRight(transactionDate.format(DATE_FMT), COL_DATE));
        sb.append(" ");
        sb.append(StringHelper.padLeft(df.format(amount), COL_AMOUNT));
        sb.append(" ");
        sb.append(StringHelper.padRight(confirmed ? "Y" : "N", COL_CONF));
        sb.append(" ");

        ComponentBuilder componentBuilder = new ComponentBuilder(sb.toString());

        componentBuilder.append(messageGenerator.generateBlockExplorerLink(
                transactionHash, transactionHash.substring(0, 8)));
        componentBuilder.append("...");

        return componentBuilder.create();
    }

    /**
     * Returns a translated, fixed-width column header string for the history table.
     *
     * @param i18n   the plugin's {@link I18n} instance
     * @param locale the player's locale
     */
    public static String getHeaderString(I18n i18n, Locale locale)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(StringHelper.padRight(i18n.get(locale, "history.col_date"),      COL_DATE   + 1));
        sb.append(StringHelper.padRight(i18n.get(locale, "history.col_amount"),    COL_AMOUNT + 1));
        sb.append(StringHelper.padRight(i18n.get(locale, "history.col_confirmed"), COL_CONF   + 1));
        sb.append(i18n.get(locale, "history.col_hash"));

        return sb.toString();
    }
}
