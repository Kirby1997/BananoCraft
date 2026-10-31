package banano.bananominecraft.bananoeconomy.commands;

import banano.bananominecraft.bananoeconomy.i18n.I18n;
import banano.bananominecraft.bananoeconomy.io.EconomyFuncs;
import banano.bananominecraft.bananoeconomy.io.RPC;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.services.RepresentativeService;
import banano.bananominecraft.bananoeconomy.trackers.TaskTracker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Lets a player manage their own account's representative:
 * <ul>
 *   <li>{@code /representative set <ban_address|random>} — assign a specific representative, or
 *       one chosen randomly from {@link RepresentativeService#getCandidateRepresentatives()}.</li>
 *   <li>{@code /representative get} — look up the account's current representative.</li>
 * </ul>
 */
public class RepresentativeCommand implements CommandExecutor
{
    private static final String ARG_SET    = "set";
    private static final String ARG_GET    = "get";
    private static final String ARG_RANDOM = "random";

    private final JavaPlugin plugin;
    private final EconomyFuncs economyFuncs;
    private final TaskTracker taskTracker;
    private final I18n i18n;
    private final RepresentativeService representativeService;

    public RepresentativeCommand(JavaPlugin plugin, EconomyFuncs economyFuncs, RPC rpc,
                                 ConfigEngine configEngine, TaskTracker taskTracker, I18n i18n)
    {
        this(plugin, economyFuncs, taskTracker, i18n, new RepresentativeService(rpc, configEngine));
    }

    /** Service-injecting constructor — pass a mock {@link RepresentativeService} in tests. */
    public RepresentativeCommand(JavaPlugin plugin, EconomyFuncs economyFuncs, TaskTracker taskTracker,
                                 I18n i18n, RepresentativeService representativeService)
    {
        this.plugin = plugin;
        this.economyFuncs = economyFuncs;
        this.taskTracker = taskTracker;
        this.i18n = i18n;
        this.representativeService = representativeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        BukkitTask task = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if (!plugin.isEnabled())
                {
                    return;
                }

                if (!(sender instanceof Player player))
                {
                    return;
                }

                final Locale locale = I18n.parseMinecraftLocale(player.getLocale());

                if (args.length == 0)
                {
                    player.sendMessage(i18n.get(locale, "representative.usage"));
                    return;
                }

                if (economyFuncs.isFrozen(player))
                {
                    player.sendMessage(i18n.get(locale, "representative.frozen"));
                    return;
                }

                switch (args[0].toLowerCase())
                {
                    case ARG_SET -> handleSet(player, locale, args);
                    case ARG_GET -> handleGet(player, locale);
                    default -> player.sendMessage(i18n.get(locale, "representative.usage"));
                }
            }
        }.runTaskAsynchronously(plugin);

        taskTracker.track(task);
        return true;
    }

    private void handleSet(Player player, Locale locale, String[] args)
    {
        if (args.length != 2)
        {
            player.sendMessage(i18n.get(locale, "representative.set_usage"));
            return;
        }

        String playerWallet = economyFuncs.getWallet(player);

        RepresentativeService.RepresentativeResult result = args[1].equalsIgnoreCase(ARG_RANDOM)
                ? representativeService.setRandomRepresentative(playerWallet)
                : representativeService.setRepresentative(playerWallet, args[1]);

        if (result.success())
        {
            player.sendMessage(i18n.get(locale, "representative.set_success", args[1]));
        }
        else
        {
            player.sendMessage(i18n.get(locale, "representative.set_failed", result.userError()));
        }
    }

    private void handleGet(Player player, Locale locale)
    {
        String playerWallet = economyFuncs.getWallet(player);
        RepresentativeService.RepresentativeInfoResult result =
                representativeService.getCurrentRepresentative(playerWallet);

        if (result.success())
        {
            player.sendMessage(i18n.get(locale, "representative.get_success", result.representative()));
        }
        else
        {
            player.sendMessage(i18n.get(locale, "representative.get_failed", result.userError()));
        }
    }
}
