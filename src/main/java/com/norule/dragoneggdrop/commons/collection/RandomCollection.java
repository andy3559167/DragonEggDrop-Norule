package com.norule.dragoneggdrop.commons.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A weighted random collection implementation.
 *
 * @param <T> element type
 */
public final class RandomCollection<T> {

    private final Random random = new Random();
    private final List<Entry<T>> entries = new ArrayList<>();

    private double totalWeight;

    /**
     * Add a weighted value to this collection.
     *
     * @param weight the weight (must be positive)
     * @param value the value
     */
    public void add(double weight, @Nullable T value) {
        if (weight <= 0.0D) {
            return;
        }

        this.entries.add(new Entry<>(weight, value));
        this.totalWeight += weight;
    }

    /**
     * Remove all values equal to the provided value.
     *
     * @param value the value to remove
     *
     * @return true if at least one value was removed
     */
    public boolean remove(@Nullable T value) {
        boolean removed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry<T> entry = entries.get(i);
            if (!Objects.equals(entry.value, value)) {
                continue;
            }

            this.totalWeight -= entry.weight;
            this.entries.remove(i);
            removed = true;
        }

        if (this.totalWeight < 0.0D) {
            this.totalWeight = 0.0D;
        }

        return removed;
    }

    /**
     * Clear this collection.
     */
    public void clear() {
        this.entries.clear();
        this.totalWeight = 0.0D;
    }

    /**
     * Fetch the next random value.
     *
     * @return the value or null if none are available
     */
    @Nullable
    public T next() {
        return this.next(this.random);
    }

    /**
     * Fetch the next random value with a provided random.
     *
     * @param random the random to use
     *
     * @return the value or null if none are available
     */
    @Nullable
    public T next(@NotNull Random random) {
        if (random == null || this.entries.isEmpty() || this.totalWeight <= 0.0D) {
            return null;
        }

        double needle = random.nextDouble() * this.totalWeight;
        double cumulative = 0.0D;
        for (Entry<T> entry : entries) {
            cumulative += entry.weight;
            if (needle < cumulative) {
                return entry.value;
            }
        }

        // Fallback for floating-point edge cases.
        return this.entries.get(this.entries.size() - 1).value;
    }

    private static final class Entry<T> {

        private final double weight;
        private final T value;

        private Entry(double weight, T value) {
            this.weight = weight;
            this.value = value;
        }

    }

}
