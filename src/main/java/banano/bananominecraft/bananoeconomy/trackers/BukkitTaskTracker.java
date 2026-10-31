package banano.bananominecraft.bananoeconomy.trackers;

import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe {@link TaskTracker} implementation backed by a
 * {@link CopyOnWriteArrayList}.
 *
 * <p>A single instance is created in {@code Main.onEnable()} and injected into
 * every class that schedules async work.  {@link #cancelAll()} is called from
 * {@code Main.onDisable()} to drain all in-flight tasks before the plugin
 * unloads.</p>
 */
public class BukkitTaskTracker implements TaskTracker
{
    private final List<BukkitTask> activeTasks = new CopyOnWriteArrayList<>();

    @Override
    public void track(BukkitTask task)
    {
        activeTasks.add(task);
    }

    /**
     * Cancel every tracked task that has not already finished, then clear the
     * list so the references can be garbage-collected.
     */
    public void cancelAll()
    {
        activeTasks.forEach(task ->
        {
            if (!task.isCancelled())
            {
                task.cancel();
            }
        });
        activeTasks.clear();
    }
}
