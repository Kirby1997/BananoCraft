package banano.bananominecraft.bananoeconomy.classes;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;

import java.net.MalformedURLException;
import java.net.URL;

public class MessageGenerator {

    public static BaseComponent[] generateTipReceiverMessage(OfflinePaymentRecord paymentRecord) {

        return generateTipReceiverMessage(paymentRecord.getFromPlayerName(),
                                            paymentRecord.getPaymentAmount(),
                                            paymentRecord.getBlockHash(),
                                            paymentRecord.getMessage());

    }

    public static BaseComponent[] generateTipReceiverMessage(String senderName, double amount, String blockHash, String message) {

        final String amountStr = Double.toString(amount);

        ComponentBuilder componentBuilder = new ComponentBuilder("You have received ").color(ChatColor.YELLOW)
                    .append(amountStr).color(ChatColor.WHITE).bold(true)
                    .append(" from ").color(ChatColor.YELLOW)
                    .append(senderName).color(ChatColor.WHITE).bold(true)
                    .append(" with block ID : ").append(blockHash).color(ChatColor.YELLOW).bold(true);

        if(message != null
                && message.length() > 0) {

            componentBuilder.append(" and attached message: ").color(ChatColor.AQUA).bold(false)
                    .append(message).color(ChatColor.YELLOW).bold(false);

        }

        return componentBuilder.create();

    }

    public static TextComponent generateBlockExplorerLink(ConfigEngine configEngine, String blockHash) {

        TextComponent blockLink;
        URL blockURL = null;

        try {

            blockURL = new URL(configEngine.getExplorerBlock() + blockHash);

        }
        catch (MalformedURLException ex) {

        }

        if(blockURL != null) {

            blockLink = new TextComponent("Click me to view the transaction in the block explorer");
            blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, blockURL.toString()));
            blockLink.setUnderlined(true);

        }
        else {

            blockLink = new TextComponent("Click me to copy the block hash to the clipboard");
            blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, blockHash));

        }

        return blockLink;

    }

    public static BaseComponent[] generateTipSenderMessage(String recipientName, double amount, String blockHash, String message) {

        final String amountStr = Double.toString(amount);

        ComponentBuilder componentBuilder = new ComponentBuilder("You have sent ").color(ChatColor.YELLOW)
                .append(amountStr).color(ChatColor.WHITE).bold(true)
                .append(" to ").color(ChatColor.YELLOW)
                .append(recipientName).color(ChatColor.WHITE).bold(true)
                .append(" with block ID : ")
                .append(blockHash).color(ChatColor.YELLOW).bold(true);

        if(message != null
                && message.length() > 0) {

            componentBuilder.append(" and attached message: ").color(ChatColor.AQUA).bold(false)
                    .append(message).color(ChatColor.YELLOW).bold(false);

        }

        return componentBuilder.create();

    }

    public static TextComponent generateClickToViewOfflinePayments() {

        TextComponent blockLink = new TextComponent("Click here to display the transactions.");
        blockLink.setUnderlined(true);
        blockLink.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bananoeconomy:showofflinetips"));

        return blockLink;

    }



}
