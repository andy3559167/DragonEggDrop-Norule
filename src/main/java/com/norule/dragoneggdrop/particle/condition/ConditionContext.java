package com.norule.dragoneggdrop.particle.condition;

import com.google.common.base.Preconditions;
import com.norule.dragoneggdrop.particle.ParticleVariables;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * Represents various pieces of data required as context for an {@link EquationCondition}.
 *
 * @author Parker Hawke - Choco
 */
public class ConditionContext {

    private final ParticleVariables variables;
    private final World world;

    /**
     * Construct a new context.
     *
     * @param variables the particle variables
     * @param world the world
     */
    public ConditionContext(@NotNull ParticleVariables variables, @NotNull World world) {
        Preconditions.checkArgument(variables != null, "variables must not be null");
        Preconditions.checkArgument(world != null, "world must not be null");

        this.variables = variables;
        this.world = world;
    }

    /**
     * Get the particle variables.
     *
     * @return the particle variables
     */
    @NotNull
    public ParticleVariables getVariables() {
        return variables;
    }

    /**
     * Get the {@link World} instance.
     *
     * @return the world
     */
    @NotNull
    public World getWorld() {
        return world;
    }

}
