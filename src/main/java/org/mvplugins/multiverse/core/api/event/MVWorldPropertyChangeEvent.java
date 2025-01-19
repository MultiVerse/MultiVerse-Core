/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package org.mvplugins.multiverse.core.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import org.mvplugins.multiverse.core.api.world.MultiverseWorld;

/**
 * This event is fired after the property is changed.
 * <p>
 * To get the name of the property that was changed, use {@link #getName()}.
 * To get the old value, use {@link #getOldValue()}.
 * To get the new value, use {@link #getNewValue()}.
 *
 * @param <T> The type of the property that was set.
 * @since 5.0
 */
public class MVWorldPropertyChangeEvent<T> extends Event {
    private final MultiverseWorld world;
    private final String name;
    private final T oldValue;
    private final T newValue;

    public MVWorldPropertyChangeEvent(MultiverseWorld world, String name, T oldValue, T value) {
        this.world = world;
        this.name = name;
        this.oldValue = oldValue;
        this.newValue = value;
    }

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * {@inheritDoc}
     */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Gets the handler list. This is required by the event system.
     *
     * @return A list of handlers.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the changed world property's name.
     *
     * @return The changed world property's name.
     * @since 5.0
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the old value.
     *
     * @return The old value.
     * @since 5.0
     */
    public T getOldValue() {
        return oldValue;
    }

    /**
     * Gets the new value.
     *
     * @return The new value.
     * @since 5.0
     */
    public T getNewValue() {
        return this.newValue;
    }

    /**
     * Get the world targeted because of this change.
     *
     * @return A valid MultiverseWorld.
     * @since 5.0
     */
    public MultiverseWorld getWorld() {
        return this.world;
    }
}
