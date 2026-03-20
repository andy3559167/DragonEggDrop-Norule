package com.norule.dragoneggdrop.commons.function;

/**
 * A primitive value provider to avoid boxing with {@link Double}.
 *
 * @param <T> input type
 */
@FunctionalInterface
public interface DoubleProvider<T> {

    /**
     * Fetch a double value from the provided object.
     *
     * @param input the input object
     *
     * @return the provided value
     */
    double get(T input);

}
