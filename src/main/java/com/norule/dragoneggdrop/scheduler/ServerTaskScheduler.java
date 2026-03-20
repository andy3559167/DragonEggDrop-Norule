package com.norule.dragoneggdrop.scheduler;

import com.google.common.base.Preconditions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Schedules tasks on both classic Bukkit schedulers and Folia schedulers.
 */
public final class ServerTaskScheduler {

    private final JavaPlugin plugin;
    private final boolean folia;

    private final Object regionScheduler;
    private final Object globalRegionScheduler;
    private final Object asyncScheduler;
    private final Method globalRunAtFixedRateMethod;
    private final Method globalRunDelayedMethod;
    private final Method asyncRunAtFixedRateMethod;

    /**
     * Construct a new scheduler adapter.
     *
     * @param plugin the plugin instance
     */
    public ServerTaskScheduler(@NotNull JavaPlugin plugin) {
        Preconditions.checkArgument(plugin != null, "plugin must not be null");

        this.plugin = plugin;

        Object regionSchedulerLocal = null;
        Object globalSchedulerLocal = null;
        Object asyncSchedulerLocal = null;
        Method globalRunAtFixedRateLocal = null;
        Method globalRunDelayedLocal = null;
        Method asyncRunAtFixedRateLocal = null;
        boolean foliaLocal = false;

        try {
            Object server = Bukkit.getServer();
            regionSchedulerLocal = resolveSchedulerAccessor(server, "getRegionScheduler");
            globalSchedulerLocal = resolveSchedulerAccessor(server, "getGlobalRegionScheduler");
            asyncSchedulerLocal = resolveSchedulerAccessor(server, "getAsyncScheduler");

            if (globalSchedulerLocal != null) {
                globalRunAtFixedRateLocal = findOptionalMethod(globalSchedulerLocal.getClass(), "runAtFixedRate", 4);
                globalRunDelayedLocal = findOptionalMethod(globalSchedulerLocal.getClass(), "runDelayed", 3);
            }

            if (asyncSchedulerLocal != null) {
                asyncRunAtFixedRateLocal = findOptionalMethod(asyncSchedulerLocal.getClass(), "runAtFixedRate", 5);
            }

            foliaLocal = regionSchedulerLocal != null
                && globalRunAtFixedRateLocal != null
                && globalRunDelayedLocal != null
                && asyncRunAtFixedRateLocal != null;
        } catch (IllegalStateException ignored) {
            foliaLocal = false;
        }

        this.folia = foliaLocal;
        this.regionScheduler = regionSchedulerLocal;
        this.globalRegionScheduler = globalSchedulerLocal;
        this.asyncScheduler = asyncSchedulerLocal;
        this.globalRunAtFixedRateMethod = globalRunAtFixedRateLocal;
        this.globalRunDelayedMethod = globalRunDelayedLocal;
        this.asyncRunAtFixedRateMethod = asyncRunAtFixedRateLocal;
    }

    /**
     * Check whether Folia scheduler APIs are available.
     *
     * @return true if running on Folia-compatible scheduler
     */
    public boolean isFolia() {
        return folia;
    }

    /**
     * Schedule a repeating synchronous task.
     *
     * @param location region location for Folia. Null falls back to global scheduler on Folia
     * @param delayTicks delay in ticks
     * @param periodTicks period in ticks
     * @param runnable task body
     *
     * @return the scheduled task handle
     */
    @NotNull
    public ScheduledTaskHandle runTimer(@Nullable Location location, long delayTicks, long periodTicks, @NotNull Runnable runnable) {
        Preconditions.checkArgument(runnable != null, "runnable must not be null");

        if (!folia) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
            return new BukkitTaskHandle(task);
        }

        long normalizedDelayTicks = normalizeFoliaDelayTicks(delayTicks);
        long normalizedPeriodTicks = normalizeFoliaPeriodTicks(periodTicks);

        Location taskLocation = normalizeLocation(location);
        if (taskLocation != null) {
            Object task = runRegionAtFixedRate(taskLocation, normalizedDelayTicks, normalizedPeriodTicks, runnable);
            return new ReflectiveTaskHandle(task);
        }

        Object task = invoke(globalRunAtFixedRateMethod, globalRegionScheduler, plugin, asConsumer(runnable), normalizedDelayTicks, normalizedPeriodTicks);
        return new ReflectiveTaskHandle(task);
    }

    /**
     * Schedule a delayed synchronous task.
     *
     * @param location region location for Folia. Null falls back to global scheduler on Folia
     * @param delayTicks delay in ticks
     * @param runnable task body
     *
     * @return the scheduled task handle
     */
    @NotNull
    public ScheduledTaskHandle runLater(@Nullable Location location, long delayTicks, @NotNull Runnable runnable) {
        Preconditions.checkArgument(runnable != null, "runnable must not be null");

        if (!folia) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
            return new BukkitTaskHandle(task);
        }

        long normalizedDelayTicks = normalizeFoliaDelayTicks(delayTicks);

        Location taskLocation = normalizeLocation(location);
        if (taskLocation != null) {
            Object task = runRegionDelayed(taskLocation, normalizedDelayTicks, runnable);
            return new ReflectiveTaskHandle(task);
        }

        Object task = invoke(globalRunDelayedMethod, globalRegionScheduler, plugin, asConsumer(runnable), normalizedDelayTicks);
        return new ReflectiveTaskHandle(task);
    }

    /**
     * Schedule a repeating async task.
     *
     * @param delayTicks delay in ticks
     * @param periodTicks period in ticks
     * @param runnable task body
     *
     * @return the scheduled task handle
     */
    @NotNull
    public ScheduledTaskHandle runAsyncTimer(long delayTicks, long periodTicks, @NotNull Runnable runnable) {
        Preconditions.checkArgument(runnable != null, "runnable must not be null");

        if (!folia) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
            return new BukkitTaskHandle(task);
        }

        long delayMillis = ticksToMillis(normalizeFoliaDelayTicks(delayTicks));
        long periodMillis = ticksToMillis(normalizeFoliaPeriodTicks(periodTicks));
        Object task = invoke(asyncRunAtFixedRateMethod, asyncScheduler, plugin, asConsumer(runnable), delayMillis, periodMillis, TimeUnit.MILLISECONDS);
        return new ReflectiveTaskHandle(task);
    }

    @Nullable
    private Location normalizeLocation(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.clone();
    }

    @NotNull
    private Object runRegionAtFixedRate(@NotNull Location location, long delayTicks, long periodTicks, @NotNull Runnable runnable) {
        Method byLocation = findOptionalMethod(regionScheduler.getClass(), "runAtFixedRate", 5);
        if (byLocation != null) {
            return invoke(byLocation, regionScheduler, plugin, location, asConsumer(runnable), delayTicks, periodTicks);
        }

        Method byChunk = findMethod(regionScheduler.getClass(), "runAtFixedRate", 7);
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot schedule region task for location without world");
        }

        return invoke(
            byChunk,
            regionScheduler,
            plugin,
            world,
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
            asConsumer(runnable),
            delayTicks,
            periodTicks
        );
    }

    @NotNull
    private Object runRegionDelayed(@NotNull Location location, long delayTicks, @NotNull Runnable runnable) {
        Method byLocation = findOptionalMethod(regionScheduler.getClass(), "runDelayed", 4);
        if (byLocation != null) {
            return invoke(byLocation, regionScheduler, plugin, location, asConsumer(runnable), delayTicks);
        }

        Method byChunk = findMethod(regionScheduler.getClass(), "runDelayed", 6);
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot schedule region task for location without world");
        }

        return invoke(
            byChunk,
            regionScheduler,
            plugin,
            world,
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
            asConsumer(runnable),
            delayTicks
        );
    }

    @Nullable
    private static Object resolveSchedulerAccessor(@NotNull Object server, @NotNull String accessorName) {
        Method serverMethod = findOptionalMethod(server.getClass(), accessorName, 0);
        if (serverMethod != null) {
            return invoke(serverMethod, server);
        }

        Method bukkitMethod = findOptionalMethod(Bukkit.class, accessorName, 0);
        if (bukkitMethod != null) {
            return invokeStatic(bukkitMethod);
        }

        return null;
    }

    @NotNull
    private Consumer<Object> asConsumer(@NotNull Runnable runnable) {
        return scheduledTask -> runnable.run();
    }

    @NotNull
    private static Method findMethod(@NotNull Class<?> type, @NotNull String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }

        throw new IllegalStateException("Could not find method " + name + " with " + parameterCount + " parameters in " + type.getName());
    }

    @Nullable
    private static Method findOptionalMethod(@NotNull Class<?> type, @NotNull String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }

        return null;
    }

    @NotNull
    private static Object invoke(@NotNull Method method, @NotNull Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException initialException) {
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException retryException) {
                throw new IllegalStateException("Failed to invoke method " + method.getName(), retryException);
            }
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke method " + method.getName(), exception);
        }
    }

    @NotNull
    private static Object invokeStatic(@NotNull Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException initialException) {
            try {
                method.setAccessible(true);
                return method.invoke(null, args);
            } catch (IllegalAccessException | InvocationTargetException retryException) {
                throw new IllegalStateException("Failed to invoke static method " + method.getName(), retryException);
            }
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke static method " + method.getName(), exception);
        }
    }

    private static long normalizeFoliaDelayTicks(long delayTicks) {
        return Math.max(1L, delayTicks);
    }

    private static long normalizeFoliaPeriodTicks(long periodTicks) {
        return Math.max(1L, periodTicks);
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * 50L;
    }

    private static final class BukkitTaskHandle implements ScheduledTaskHandle {

        private final BukkitTask task;

        private BukkitTaskHandle(@NotNull BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            this.task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return this.task.isCancelled();
        }

    }

    private static final class ReflectiveTaskHandle implements ScheduledTaskHandle {

        private final Object task;
        private final Method cancelMethod;
        private final Method isCancelledMethod;

        private volatile boolean cancelled = false;

        private ReflectiveTaskHandle(@NotNull Object task) {
            this.task = task;
            this.cancelMethod = findMethod(task.getClass(), "cancel", 0);
            this.isCancelledMethod = findOptionalMethod(task.getClass(), "isCancelled", 0);
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }

            invoke(this.cancelMethod, this.task);
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            if (this.isCancelledMethod == null) {
                return this.cancelled;
            }

            Object result = invoke(this.isCancelledMethod, this.task);
            return result instanceof Boolean ? (Boolean) result : this.cancelled;
        }

    }

}
