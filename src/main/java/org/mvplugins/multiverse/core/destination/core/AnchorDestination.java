package org.mvplugins.multiverse.core.destination.core;

import java.util.Collection;

import co.aikar.commands.BukkitCommandIssuer;
import jakarta.inject.Inject;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.anchor.AnchorManager;
import org.mvplugins.multiverse.core.destination.Destination;

/**
 * {@link Destination} implementation for anchors.
 */
@Service
public class AnchorDestination implements Destination<AnchorDestination, AnchorDestinationInstance> {

    private final AnchorManager anchorManager;

    @Inject
    AnchorDestination(AnchorManager anchorManager) {
        this.anchorManager = anchorManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull String getIdentifier() {
        return "a";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable AnchorDestinationInstance getDestinationInstance(@Nullable String destinationParams) {
        if (destinationParams == null) {
            return null;
        }
        Location anchorLocation = this.anchorManager.getAnchorLocation(destinationParams);
        if (anchorLocation == null) {
            return null;
        }
        return new AnchorDestinationInstance(this, destinationParams, anchorLocation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Collection<String> suggestDestinations(@NotNull BukkitCommandIssuer issuer, @Nullable String destinationParams) {
        return this.anchorManager.getAnchors(issuer.getPlayer());
    }
}
