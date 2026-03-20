package com.norule.dragoneggdrop.scheduler;

/**
 * A lightweight handle to a scheduled task.
 */
public interface ScheduledTaskHandle {

    /**
     * Cancel this task.
     */
    void cancel();

    /**
     * Check whether this task is cancelled.
     *
     * @return true if cancelled
     */
    boolean isCancelled();

}
