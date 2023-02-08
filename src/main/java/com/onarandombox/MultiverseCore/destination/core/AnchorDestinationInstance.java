package com.onarandombox.MultiverseCore.destination.core;

import com.onarandombox.MultiverseCore.api.DestinationInstance;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnchorDestinationInstance implements DestinationInstance {
    private final String anchorName;
    private final Location anchorLocation;

    /**
     * Constructor.
     *
     * @param anchorName        The name of the anchor.
     * @param anchorLocation    The location of the anchor.
     */
    public AnchorDestinationInstance(@NotNull String anchorName, @NotNull Location anchorLocation) {
        this.anchorName = anchorName;
        this.anchorLocation = anchorLocation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Location getLocation(@NotNull Entity teleportee) {
        return anchorLocation;
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
        return anchorName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String serialise() {
        return anchorName;
    }
}
