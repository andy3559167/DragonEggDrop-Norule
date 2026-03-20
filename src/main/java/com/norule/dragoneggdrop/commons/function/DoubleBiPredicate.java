package com.norule.dragoneggdrop.commons.function;

/**
 * A primitive predicate for two double arguments.
 */
@FunctionalInterface
public interface DoubleBiPredicate {

    /**
     * Test this predicate with two values.
     *
     * @param left the first value
     * @param right the second value
     *
     * @return true if the predicate is met
     */
    boolean test(double left, double right);

}
