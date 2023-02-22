package com.onarandombox.MultiverseCore.destination.core;

import com.onarandombox.MultiverseCore.api.DestinationInstance;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExactDestinationInstance implements DestinationInstance {
    private final Location location;

    /**
     * Constructor.
     *
     * @param location The location to teleport to.
     */
    public ExactDestinationInstance(Location location) {
        this.location = location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Location getLocation(@NotNull Entity teleportee) {
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Vector getVelocity(@NotNull Entity teleportee) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getFinerPermissionSuffix() {
        return location.getWorld().getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String serialise() {
        return location.getWorld().getName() + ":" + location.getX() + "," + location.getY()
                + "," + location.getZ() + ":" + location.getPitch() + ":" + location.getYaw();
    }
}
