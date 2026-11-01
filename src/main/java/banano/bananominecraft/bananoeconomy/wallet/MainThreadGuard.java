package banano.bananominecraft.bananoeconomy.wallet;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Answers "am I on the server's primary thread?" and runs work asynchronously, so code that is
 * reached through synchronous APIs (Vault) can keep slow work (light-mode proof-of-work) off the
 * main thread. Injectable so unit tests need no running server.
 */
public final class MainThreadGuard
{
    private final BooleanSupplier primaryThread;
    private final Consumer<Runnable> async;

    public MainThreadGuard(BooleanSupplier primaryThread, Consumer<Runnable> async)
    {
        this.primaryThread = primaryThread;
        this.async = async;
    }

    /** Guard backed by Bukkit and the plugin's async scheduler. */
    public static MainThreadGuard forPlugin(Plugin plugin)
    {
        return new MainThreadGuard(
                () -> Bukkit.getServer() != null && Bukkit.isPrimaryThread(),
                task ->
                {
                    try
                    {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
                    }
                    catch (RuntimeException e)
                    {
                        // e.g. the plugin is being disabled: the work is best-effort, drop it.
                        plugin.getLogger().log(Level.FINE, "Could not schedule background work.", e);
                    }
                });
    }

    /** Guard that is never on the primary thread (tests, plain scripts). */
    public static MainThreadGuard neverPrimary()
    {
        return new MainThreadGuard(() -> false, Runnable::run);
    }

    public boolean isPrimaryThread()
    {
        return primaryThread.getAsBoolean();
    }

    /** Runs {@code task} on a background thread. */
    public void runAsync(Runnable task)
    {
        async.accept(task);
    }

    /** Runs {@code task} now unless this is the primary thread, in which case it is scheduled async. */
    public void runOffMainThread(Runnable task)
    {
        if (isPrimaryThread())
        {
            runAsync(task);
        }
        else
        {
            task.run();
        }
    }
}
