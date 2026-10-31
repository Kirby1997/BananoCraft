package banano.bananominecraft.bananoeconomy.events;

import banano.bananominecraft.bananoeconomy.classes.MessageGenerator;
import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.BananoWebSocket;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.db.IDBConnector;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public class OnJoin implements Listener
{
    private final Plugin plugin;
    private final EconomyFuncs economyFuncs;
    private final IDBConnector db;
    private final ConfigEngine configEngine;
    private final BananoWebSocket webSocket;
    private final TaskTracker taskTracker;
    private final I18n i18n;
    private final MessageGenerator messageGenerator;

    public OnJoin(Plugin plugin, EconomyFuncs economyFuncs, IDBConnector db,
                  ConfigEngine configEngine, BananoWebSocket webSocket, TaskTracker taskTracker,
                  I18n i18n, MessageGenerator messageGenerator)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.db = db;
        this.configEngine = configEngine;
        this.webSocket = webSocket;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
        this.messageGenerator = messageGenerator;
    }

    @EventHandler
    public void onJoinServer(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        Locale locale = I18n.parseMinecraftLocale(player.getLocale());

        TextComponent welcomeMessage = new TextComponent(i18n.get(locale, "join.welcome"));
        welcomeMessage.setColor(ChatColor.YELLOW);
        welcomeMessage.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Kirby1997/BananoCraft"));
        welcomeMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(i18n.get(locale, "join.see_code")).create()));
        player.spigot().sendMessage(welcomeMessage);

        BukkitTask task = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                try
                {
                    plugin.getLogger().info("Creating account for player: " + player.getName());

                    if (!economyFuncs.accountCreate(player))
                    {
                        player.sendMessage(ChatColor.RED + i18n.get(locale, "join.wallet_error"));
                    }
                    else
                    {
                        // Account is ready — subscribe to real-time deposit notifications
                        String wallet = economyFuncs.getWallet(player);
                        webSocket.watchAccount(wallet, player.getUniqueId());
                    }
                }
                catch (Exception ex)
                {
                    player.sendMessage(org.bukkit.ChatColor.RED + i18n.get(locale, "join.wallet_error_generic"));
                }

                if (configEngine.getEnableOfflinePayment())
                {
                    try
                    {
                        double totalAmount = db.getOfflinePaymentsTotal(player);

                        if (totalAmount > 0)
                        {
                            player.sendMessage(ChatColor.GOLD + ChatColor.BOLD.toString()
                                    + i18n.get(locale, "join.offline_total", totalAmount));

                            player.spigot().sendMessage(messageGenerator.generateClickToViewOfflinePayments(locale));
                        }
                    }
                    catch (Exception ex)
                    {
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to check offline payments on join.", ex);
                    }
                }
            }
        }.runTaskAsynchronously(this.plugin);

        taskTracker.track(task);
    }
}
