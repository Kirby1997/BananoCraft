package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;

public class DepositCommand implements CommandExecutor
{
    private static final String ARG_SERVER = "server";

    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final ConfigEngine configEngine;
    private final RPC rpc;
    private final I18n i18n;

    public DepositCommand(Plugin plugin, EconomyFuncs economyFuncs, ConfigEngine configEngine, RPC rpc, I18n i18n)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.configEngine = configEngine;
        this.rpc = rpc;
        this.i18n = i18n;
    }

    private URL getURL() throws Exception
    {
        return new URL(this.configEngine.getExplorerAccount());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (sender instanceof Player player)
        {
            final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

            if (economyFuncs.isFrozen(player))
            {
                player.sendMessage(i18n.get(locale, "deposit.frozen"));
                return false;
            }

            String playerWallet = economyFuncs.getWallet(player);
            String walletOwner  = "your";

            if (args.length > 0 && args[0].equalsIgnoreCase(ARG_SERVER))
            {
                playerWallet = rpc.getMasterWallet();
                walletOwner  = "server";
            }

            try
            {
                TextComponent clickableWallet = new TextComponent(playerWallet);
                clickableWallet.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, playerWallet));

                TextComponent walletHoverText = new TextComponent(i18n.get(locale, "deposit.copy_hint"));
                walletHoverText.setColor(ChatColor.WHITE);
                clickableWallet.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[] { walletHoverText }));

                URL walletURL = new URL(getURL() + playerWallet);
                player.spigot().sendMessage(new ComponentBuilder(i18n.get(locale, "deposit.address", walletOwner) + " ")
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append(clickableWallet)
                        .color(net.md_5.bungee.api.ChatColor.WHITE)
                        .bold(true).create());

                TextComponent addrlink = new TextComponent(i18n.get(locale, "deposit.explorer_link", walletOwner));
                addrlink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, walletURL.toString()));
                addrlink.setUnderlined(true);
                player.spigot().sendMessage(addrlink);
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "Failed to build deposit message.", e);
            }
        }
        else
        {
            plugin.getLogger().warning("Deposit command issued by non-player sender: " + sender.getName());
        }

        return false;
    }
}
