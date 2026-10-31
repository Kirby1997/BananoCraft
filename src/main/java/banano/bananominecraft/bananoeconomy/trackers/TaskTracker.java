package banano.bananominecraft.bananoeconomy.trackers;

import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks active async {@link BukkitTask}s so they can all be cancelled cleanly
 * when the plugin shuts down.
 *
 * <p>Injected into every class that schedules async work, replacing the previous
 * static {@code Main.trackTask()} service-locator call.</p>
 */
public interface TaskTracker
{
    void track(BukkitTask task);
}
