package com.norule.dragoneggdrop.scheduler;

import com.google.common.base.Preconditions;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link BukkitRunnable} variant that can be scheduled through
 * {@link ServerTaskScheduler} for Folia compatibility.
 */
public abstract class CompatBukkitRunnable extends BukkitRunnable {

    private ScheduledTaskHandle handle;

    /**
     * Schedule this runnable as a repeating task.
     *
     * @param scheduler task scheduler
     * @param location execution location for region scheduling on Folia
     * @param delayTicks delay in ticks
     * @param periodTicks period in ticks
     */
    public synchronized void runTaskTimerCompat(@NotNull ServerTaskScheduler scheduler, @Nullable Location location, long delayTicks, long periodTicks) {
        Preconditions.checkArgument(scheduler != null, "scheduler must not be null");
        Preconditions.checkState(this.handle == null, "Task already scheduled");

        this.handle = scheduler.runTimer(location, delayTicks, periodTicks, this);
    }

    /**
     * Schedule this runnable as a delayed task.
     *
     * @param scheduler task scheduler
     * @param location execution location for region scheduling on Folia
     * @param delayTicks delay in ticks
     */
    public synchronized void runTaskLaterCompat(@NotNull ServerTaskScheduler scheduler, @Nullable Location location, long delayTicks) {
        Preconditions.checkArgument(scheduler != null, "scheduler must not be null");
        Preconditions.checkState(this.handle == null, "Task already scheduled");

        this.handle = scheduler.runLater(location, delayTicks, this);
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        if (this.handle != null) {
            this.handle.cancel();
            return;
        }

        super.cancel();
    }

    @Override
    public synchronized boolean isCancelled() throws IllegalStateException {
        if (this.handle != null) {
            return this.handle.isCancelled();
        }

        return super.isCancelled();
    }

}
