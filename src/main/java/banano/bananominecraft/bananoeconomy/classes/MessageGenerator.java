package banano.bananominecraft.bananoeconomy.classes;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds the rich BungeeCord chat components used throughout the plugin.
 *
 * <p>Inject a single instance (created in {@code BananoEconomyMain.onEnable}) wherever
 * player-facing messages are produced. Resolve the player locale with
 * {@link I18n#parseMinecraftLocale(String)} and pass it to each method so that
 * translated text is used.</p>
 */
public class MessageGenerator
{
    private static final Logger LOGGER = Logger.getLogger(MessageGenerator.class.getName());

    private final I18n i18n;
    private final ConfigEngine configEngine;

    public MessageGenerator(I18n i18n, ConfigEngine configEngine)
    {
        this.i18n = i18n;
        this.configEngine = configEngine;
    }

    // -------------------------------------------------------------------------
    // Tip messages
    // -------------------------------------------------------------------------

    public BaseComponent[] generateTipReceiverMessage(Locale locale, OfflinePaymentRecord paymentRecord)
    {
        return generateTipReceiverMessage(locale,
                paymentRecord.fromPlayerName(),
                paymentRecord.paymentAmount(),
                paymentRecord.blockHash(),
                paymentRecord.message());
    }

    public BaseComponent[] generateTipReceiverMessage(Locale locale,
                                                       String senderName,
                                                       double amount,
                                                       String blockHash,
                                                       String message)
    {
        final String amountStr = Double.toString(amount);
        final boolean hasNote = message != null && !message.isEmpty();

        final String text = hasNote
                ? i18n.get(locale, "msg.received_with_note", amountStr, senderName, blockHash, message)
                : i18n.get(locale, "msg.received", amountStr, senderName, blockHash);

        return new ComponentBuilder(text).color(ChatColor.YELLOW).create();
    }

    public BaseComponent[] generateTipSenderMessage(Locale locale,
                                                     String recipientName,
                                                     double amount,
                                                     String blockHash,
                                                     String message)
    {
        final String amountStr = Double.toString(amount);
        final boolean hasNote = message != null && !message.isEmpty();

        final String text = hasNote
                ? i18n.get(locale, "msg.sent_with_note", amountStr, recipientName, blockHash, message)
                : i18n.get(locale, "msg.sent", amountStr, recipientName, blockHash);

        return new ComponentBuilder(text).color(ChatColor.YELLOW).create();
    }

    // -------------------------------------------------------------------------
    // Block explorer links
    // -------------------------------------------------------------------------

    /**
     * Generates a clickable link to the block explorer for {@code blockHash}.
     * The link text is the translated "click me" string.
     */
    public TextComponent generateBlockExplorerLink(Locale locale, String blockHash)
    {
        TextComponent blockLink;
        URL blockURL = null;

        try
        {
            blockURL = new URL(configEngine.getExplorerBlock() + blockHash);
        }
        catch (MalformedURLException ex)
        {
            LOGGER.log(Level.WARNING, "Block explorer URL is malformed; falling back to clipboard.", ex);
        }

        if (blockURL != null)
        {
            blockLink = new TextComponent(i18n.get(locale, "msg.view_in_explorer"));
            blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
            blockLink.setUnderlined(true);
        }
        else
        {
            blockLink = new TextComponent(i18n.get(locale, "msg.copy_block_hash"));
            blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, blockHash));
        }

        return blockLink;
    }

    /**
     * Generates a clickable block-explorer link with {@code customText} as the visible
     * label. Used by {@link TransactionRecord} to display the abbreviated hash.
     */
    public TextComponent generateBlockExplorerLink(String blockHash, String customText)
    {
        TextComponent blockLink;
        URL blockURL = null;

        try
        {
            blockURL = new URL(configEngine.getExplorerBlock() + blockHash);
        }
        catch (MalformedURLException ex)
        {
            LOGGER.log(Level.WARNING, "Block explorer URL is malformed; falling back to clipboard.", ex);
        }

        if (blockURL != null)
        {
            blockLink = new TextComponent(customText);
            blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
            blockLink.setUnderlined(true);
        }
        else
        {
            blockLink = new TextComponent(customText);
            blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, blockHash));
        }

        return blockLink;
    }

    // -------------------------------------------------------------------------
    // Offline-payment click link
    // -------------------------------------------------------------------------

    public TextComponent generateClickToViewOfflinePayments(Locale locale)
    {
        TextComponent link = new TextComponent(i18n.get(locale, "msg.view_offline_payments"));
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bananoeconomy:showofflinetips"));
        return link;
    }

    // -------------------------------------------------------------------------
    // Clickable address (deposit)
    // -------------------------------------------------------------------------

    /**
     * Builds a composite component: a plain-text prefix line, the wallet address
     * (clickable copy-to-clipboard), and optionally an account-explorer link.
     *
     * @param prefixMessage the already-translated prefix line (e.g. "Deposit bans to your address:")
     * @param walletAddress the Banano address
     */
    public TextComponent generateClickableAddressMessage(String prefixMessage, String walletAddress)
    {
        TextComponent addressLink = new TextComponent(prefixMessage + "\n");
        addressLink.setColor(ChatColor.WHITE);

        TextComponent clipboardLink = new TextComponent(walletAddress);
        clipboardLink.setColor(ChatColor.AQUA);
        clipboardLink.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, walletAddress));
        addressLink.addExtra(clipboardLink);

        URL addressURL = null;

        try
        {
            addressURL = new URL(configEngine.getExplorerAccount() + walletAddress);
        }
        catch (MalformedURLException ex)
        {
            LOGGER.log(Level.WARNING, "Account explorer URL is malformed; no explorer link will be shown.", ex);
        }

        if (addressURL != null)
        {
            TextComponent urlText = new TextComponent("\nClick me to view the account in the account explorer. ");
            urlText.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, addressURL.toString()));
            urlText.setUnderlined(true);
            urlText.setColor(ChatColor.YELLOW);
            addressLink.addExtra(urlText);
        }

        return addressLink;
    }
}
